package edu.fandm.mibrahi1.mikey;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;


// AI was used a lot in this class; however I understand what every method does
// and why it is needed.
// I use getExternalFilesDir() because:
// - It stores files in external storage (required by assignment)
// - Files are private to this app
// - No additional storage permission is required
// - Not deprecated
// - Files are removed automatically when app is uninstalled
// I also use MediaRecorder because it is a high-level API that
// directly records and encodes audio to a file.
// It is simpler and less error-prone than AudioRecord.


public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ImageButton recordButton;

    private static final int RECORD_AUDIO_REQUEST_CODE = 1;
    private MediaRecorder recorder;
    private boolean isRecording = false;
    private String currentFilePath;

    private ArrayList<File> recordingFiles = new ArrayList<>();
    private RecordingsAdapter recordingsAdapter;
    private MediaPlayer player;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        recyclerView = findViewById(R.id.recordingsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        recordingsAdapter = new RecordingsAdapter();
        recyclerView.setAdapter(recordingsAdapter);

        refreshRecordingsList();

        recordButton = findViewById(R.id.recordButton);

        recordButton.setOnClickListener(v -> {
            if (!ensureAudioPermission()) return;

            if (!isRecording) {
                // Stops audio overlap. stops playing beofre recording
                if (player != null) {
                    try { player.stop(); } catch (Exception ignored) {}
                    player.release();
                    player = null;
                }
                startRecording();
                if (isRecording) {
                    recordButton.setBackgroundResource(R.drawable.round_button_red);
                } else {
                    recordButton.setBackgroundResource(R.drawable.round_button_grey);
                    Toast.makeText(this, "Recording failed to start", Toast.LENGTH_LONG).show();
                }
            } else {
                stopRecording();
                recordButton.setBackgroundResource(R.drawable.round_button_grey);
                refreshRecordingsList();
            }
        });




    }

    // AI said it helps with rotation
    @Override
    protected void onResume() {
        super.onResume();
        refreshRecordingsList();
    }

//    AI was used to build this class that will help manage the List of recordings
    private class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView rowText;
            VH(View itemView) {
                super(itemView);
                rowText = itemView.findViewById(R.id.rowText);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.row_recording, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            File f = recordingFiles.get(position);
            holder.rowText.setText(f.getName());

            // Tap = play
            holder.itemView.setOnClickListener(v -> playFile(f));

            // Long press = delete
            holder.itemView.setOnLongClickListener(v -> {
                confirmDelete(f);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return recordingFiles.size();
        }
    }

    // This class is to refresh the recordings list
    private void refreshRecordingsList() {
        File dir = getRecordingsDir();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".m4a"));

        recordingFiles.clear();
        if (files != null) {
            // Sort newest first
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            recordingFiles.addAll(Arrays.asList(files));
        }

        if (recordingsAdapter != null) {
            recordingsAdapter.notifyDataSetChanged();
        }
    }

    private void playFile(File f) {
        // stop previous playback if any
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }

        try {
            player = new MediaPlayer();
            player.setDataSource(f.getAbsolutePath());
            player.setOnCompletionListener(mp -> {
                mp.release();
                player = null;
            });
            player.prepare();
            player.start();

            Toast.makeText(this, "Playing: " + f.getName(), Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Could not play file", Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDelete(File f) {
        new AlertDialog.Builder(this)
                .setTitle("Delete recording?")
                .setMessage(f.getName())
                .setPositiveButton("Delete", (dialog, which) -> {
                    // If currently playing this file, stop
                    if (player != null) {
                        try { player.stop(); } catch (Exception ignored) {}
                        player.release();
                        player = null;
                    }

                    boolean ok = f.delete();
                    if (!ok) {
                        Toast.makeText(this, "Delete failed", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                        refreshRecordingsList();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


// AI was used in this class a lot to use the Media recorder
    private void startRecording() {
        // Make sure permission is granted before calling this
        currentFilePath = makeNewRecordingPath();

        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioEncodingBitRate(128000);
        recorder.setAudioSamplingRate(44100);
        recorder.setOutputFile(currentFilePath);

        try {
            recorder.prepare();
            recorder.start();
            isRecording = true;
        } catch (IOException | IllegalStateException e) {
            isRecording = false;
            recorder = null;
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        if (recorder == null) return;

        try {
            recorder.stop();
        } catch (RuntimeException e) {
            // happens if stop is called too soon or recorder wasn't started right
            // delete the broken file if it exists
            if (currentFilePath != null) {
                new File(currentFilePath).delete();
            }
        } finally {
            recorder.release();
            recorder = null;
            isRecording = false;
        }
    }

    // Ensures app does not crash
    @Override
    protected void onStop() {
        super.onStop();
        if (isRecording) {
            stopRecording();
            recordButton.setBackgroundResource(R.drawable.round_button_grey);
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private File getRecordingsDir() {
        File base = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (base == null) {
            base = getFilesDir(); // fallback (internal) to avoid crash
        }
        File dir = new File(base, "recordings");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private String makeNewRecordingPath() {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File outFile = new File(getRecordingsDir(), "Mikey_" + ts + ".m4a");
        return outFile.getAbsolutePath();
    }

    private boolean ensureAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }

        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                RECORD_AUDIO_REQUEST_CODE
        );
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                Toast.makeText(this,
                        "Microphone permission granted",
                        Toast.LENGTH_SHORT).show();

            } else {

                Toast.makeText(this,
                        "Microphone permission denied",
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}