package com.example.linkplayer;

import android.Manifest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.util.Locale;

/**
 * MainActivity for the LinkPlayer application.
 * This class handles both local audio playback and video streaming from a URL
 * using the Media3 ExoPlayer library.
 */
public class MainActivity extends AppCompatActivity {

    // UI Elements
    private TextView tvAudioFileName, tvNowPlaying, tvStatus, tvPosition, tvDuration;
    private EditText etVideoUrl;
    private SeekBar seekBarMain;
    private PlayerView playerView;
    private Button btnPlay, btnPause, btnStop, btnRestart, btnOpenFile, btnOpenUrl;

    // Media Player and background tasks
    private ExoPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateProgressAction;

    // Launcher for requesting multiple permissions (Storage for media access)
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = true;
                for (boolean b : result.values()) {
                    if (!b) {
                        granted = false;
                        break;
                    }
                }
                if (!granted) {
                    Toast.makeText(this, "Permissions required to read local media files", Toast.LENGTH_SHORT).show();
                }
            });

    // Launcher for picking a file from the device storage
    private final ActivityResultLauncher<String> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    // Play the selected local audio file
                    playMedia(uri, "Local Audio File");
                    // Show file segment name in the UI
                    tvAudioFileName.setText(uri.getLastPathSegment());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Initialize UI components by finding them in activity_main.xml
        initViews();

        // 2. Setup the ExoPlayer instance
        initializePlayer();

        // 3. Request necessary permissions for accessing local storage
        checkPermissions();

        // 4. Set up click listeners for all buttons
        setupListeners();
    }

    private void initViews() {
        // Link Java objects to the layout components
        tvAudioFileName = findViewById(R.id.tvAudioFileName);
        tvNowPlaying = findViewById(R.id.tvNowPlaying);
        tvStatus = findViewById(R.id.tvStatus);
        tvPosition = findViewById(R.id.tvPosition);
        tvDuration = findViewById(R.id.tvDuration);
        etVideoUrl = findViewById(R.id.etVideoUrl);
        seekBarMain = findViewById(R.id.seekBarMain);
        playerView = findViewById(R.id.playerView);

        btnPlay = findViewById(R.id.btnPlay);
        btnPause = findViewById(R.id.btnPause);
        btnStop = findViewById(R.id.btnStop);
        btnRestart = findViewById(R.id.btnRestart);
        btnOpenFile = findViewById(R.id.btnOpenFile);
        btnOpenUrl = findViewById(R.id.btnOpenUrl);
    }

    private void initializePlayer() {
        // Build the ExoPlayer instance
        player = new ExoPlayer.Builder(this).build();
        // Attach the player to the PlayerView (surface for video)
        playerView.setPlayer(player);

        // Listen for player state changes (Ready, Buffering, Playing, etc.)
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                switch (playbackState) {
                    case Player.STATE_BUFFERING:
                        tvStatus.setText("Buffering...");
                        break;
                    case Player.STATE_READY:
                        tvStatus.setText("Ready");
                        updateDuration();
                        startProgressUpdate();
                        break;
                    case Player.STATE_ENDED:
                        tvStatus.setText("Playback Finished");
                        stopProgressUpdate();
                        break;
                    case Player.STATE_IDLE:
                        tvStatus.setText("Idle");
                        break;
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                // Toggle Play/Pause button visibility based on playing state
                if (isPlaying) {
                    btnPlay.setVisibility(View.GONE);
                    btnPause.setVisibility(View.VISIBLE);
                } else {
                    btnPlay.setVisibility(View.VISIBLE);
                    btnPause.setVisibility(View.GONE);
                }
            }
        });
    }

    private void setupListeners() {
        // "Open File" -> Pick an audio file
        btnOpenFile.setOnClickListener(v -> filePickerLauncher.launch("audio/*"));

        // "Open URL" -> Stream video from the text input
        btnOpenUrl.setOnClickListener(v -> {
            String url = etVideoUrl.getText().toString().trim();
            if (!url.isEmpty()) {
                playMedia(Uri.parse(url), "Streaming Video");
                playerView.setVisibility(View.VISIBLE); // Ensure video view is visible
            } else {
                Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show();
            }
        });

        // "Play" button logic
        btnPlay.setOnClickListener(v -> player.play());

        // "Pause" button logic
        btnPause.setOnClickListener(v -> player.pause());

        // "Stop" button logic -> Using pause and seek to 0 instead of stop
        // This keeps the media loaded so Play and Restart still work correctly
        btnStop.setOnClickListener(v -> {
            player.pause();
            player.seekTo(0);
            tvStatus.setText("Stopped");
        });

        // "Restart" button logic -> Seek to 0 and play
        btnRestart.setOnClickListener(v -> {
            player.seekTo(0);
            player.play();
        });

        // SeekBar interaction -> Seek media when user moves the bar
        seekBarMain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    player.seekTo(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void playMedia(Uri uri, String title) {
        // Create a MediaItem from the provided URI
        MediaItem mediaItem = MediaItem.fromUri(uri);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
        tvNowPlaying.setText(title);
        
        // Hide video surface if playing audio to save space/UI
        if (title.toLowerCase().contains("audio")) {
            playerView.setVisibility(View.GONE);
        }
    }

    private void updateDuration() {
        // Get media duration and set the seekbar maximum
        long duration = player.getDuration();
        if (duration != C.TIME_UNSET && duration > 0) {
            seekBarMain.setMax((int) duration);
            tvDuration.setText(formatTime(duration));
        }
    }

    private void startProgressUpdate() {
        // Update the seekbar and time text every 1 second
        stopProgressUpdate();
        updateProgressAction = new Runnable() {
            @Override
            public void run() {
                if (player != null && player.isPlaying()) {
                    long currentPosition = player.getCurrentPosition();
                    seekBarMain.setProgress((int) currentPosition);
                    tvPosition.setText(formatTime(currentPosition));
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(updateProgressAction);
    }

    private void stopProgressUpdate() {
        // Stop the background update task
        if (updateProgressAction != null) {
            handler.removeCallbacks(updateProgressAction);
        }
    }

    /**
     * Converts milliseconds to a readable time format (MM:SS)
     */
    private String formatTime(long millis) {
        int totalSeconds = (int) (millis / 1000);
        int seconds = totalSeconds % 60;
        int minutes = totalSeconds / 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private void checkPermissions() {
        // Handle different permission sets for Android 13+ vs older versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO
            });
        } else {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProgressUpdate();
        // Always release the player to free up system resources (Memory, Decoders)
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
