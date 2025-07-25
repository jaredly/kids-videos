package com.example.kidsvideos;

import android.media.MediaPlayer;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class VideoPlayerActivity extends AppCompatActivity {

    // Static map to store video positions across activity instances
    private static final Map<String, Integer> videoPositions = new HashMap<>();
    private static final int RESUME_THRESHOLD = 5000; // Only resume if more than 5 seconds in

    private VideoView videoView;
    private ImageButton btnPlayPause;
    private ImageButton btnClose;
    private SeekBar seekBar;
    private TextView tvCurrentTime;
    private TextView tvDuration;
    private View controlsLayout;

    private Handler handler = new Handler();
    private boolean isPlaying = false;
    private boolean isUserSeeking = false;
    private Runnable updateSeekBarRunnable;
    private Runnable hideControlsRunnable;
    private String currentVideoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        initViews();
        setupVideoPlayer();
        setupControls();
        hideSystemUI();
    }

    private void initViews() {
        videoView = findViewById(R.id.video_view);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnClose = findViewById(R.id.btn_close);
        seekBar = findViewById(R.id.seek_bar);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvDuration = findViewById(R.id.tv_duration);
        controlsLayout = findViewById(R.id.controls_layout);
    }

    private void setupVideoPlayer() {
        String videoPath = getIntent().getStringExtra("video_path");
        if (videoPath != null) {
            currentVideoPath = videoPath;
            Uri videoUri;

            // Check if it's a content URI or file path
            if (videoPath.startsWith("content://")) {
                videoUri = Uri.parse(videoPath);
            } else {
                File videoFile = new File(videoPath);
                if (videoFile.exists()) {
                    videoUri = Uri.fromFile(videoFile);
                } else {
                    return; // File doesn't exist
                }
            }

            videoView.setVideoURI(videoUri);

                videoView.setOnPreparedListener(mediaPlayer -> {
                    int duration = videoView.getDuration();
                    seekBar.setMax(duration);
                    tvDuration.setText(formatTime(duration));
                    tvCurrentTime.setText("00:00");

                    // Restore previous position if available
                    Integer savedPosition = videoPositions.get(currentVideoPath);
                    if (savedPosition != null && savedPosition > RESUME_THRESHOLD && savedPosition < duration - 5000) {
                        videoView.seekTo(savedPosition);
                        tvCurrentTime.setText(formatTime(savedPosition));
                        seekBar.setProgress(savedPosition);
                    }

                    // Auto-play
                    videoView.start();
                    isPlaying = true;
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                    startSeekBarUpdate();

                    // Start auto-hide timer when video begins
                    startControlsAutoHide();
                });

                videoView.setOnCompletionListener(mediaPlayer -> {
                    isPlaying = false;
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                    seekBar.setProgress(0);
                    tvCurrentTime.setText("00:00");
                    stopSeekBarUpdate();

                    // Clear saved position when video completes
                    if (currentVideoPath != null) {
                        videoPositions.remove(currentVideoPath);
                    }
                });

                // Show/hide controls on tap
                videoView.setOnClickListener(v -> toggleControlsVisibility());
        }
    }

    private void setupControls() {
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnClose.setOnClickListener(v -> {
            Intent intent = new Intent(VideoPlayerActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    videoView.seekTo(progress);
                    tvCurrentTime.setText(formatTime(progress));
                    resetControlsAutoHide(); // Reset timer when user scrubs
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
                resetControlsAutoHide(); // Reset timer when user starts seeking
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                int seekPosition = seekBar.getProgress();
                videoView.seekTo(seekPosition);
                tvCurrentTime.setText(formatTime(seekPosition));
                resetControlsAutoHide(); // Reset timer when user finishes seeking
            }
        });
    }

    private void togglePlayPause() {
        if (isPlaying) {
            videoView.pause();
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            stopSeekBarUpdate();
        } else {
            videoView.start();
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            startSeekBarUpdate();
        }
        isPlaying = !isPlaying;
        resetControlsAutoHide(); // Reset timer when user interacts with play/pause
    }

    private void startSeekBarUpdate() {
        updateSeekBarRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isUserSeeking && videoView.isPlaying()) {
                    int currentPosition = videoView.getCurrentPosition();
                    seekBar.setProgress(currentPosition);
                    tvCurrentTime.setText(formatTime(currentPosition));

                    // Save position every 5 seconds for crash recovery
                    if (currentPosition % 5000 < 200) { // Within 200ms of a 5-second mark
                        saveCurrentPosition();
                    }
                }
                handler.postDelayed(this, 100); // Update every 100ms for smooth animation
            }
        };
        handler.post(updateSeekBarRunnable);
    }

    private void stopSeekBarUpdate() {
        if (updateSeekBarRunnable != null) {
            handler.removeCallbacks(updateSeekBarRunnable);
        }
    }

    private void toggleControlsVisibility() {
        if (controlsLayout.getVisibility() == View.VISIBLE) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        controlsLayout.setVisibility(View.VISIBLE);
        startControlsAutoHide();
    }

    private void hideControls() {
        controlsLayout.setVisibility(View.GONE);
        cancelControlsAutoHide();
    }

    private void startControlsAutoHide() {
        // Cancel any existing auto-hide timer
        cancelControlsAutoHide();

        // Start new auto-hide timer
        hideControlsRunnable = () -> hideControls();
        handler.postDelayed(hideControlsRunnable, 3000);
    }

    private void cancelControlsAutoHide() {
        if (hideControlsRunnable != null) {
            handler.removeCallbacks(hideControlsRunnable);
            hideControlsRunnable = null;
        }
    }

    private void resetControlsAutoHide() {
        // Only reset if controls are currently visible
        if (controlsLayout.getVisibility() == View.VISIBLE) {
            startControlsAutoHide();
        }
    }

    private void saveCurrentPosition() {
        if (currentVideoPath != null && videoView != null) {
            int currentPosition = videoView.getCurrentPosition();
            int duration = videoView.getDuration();

            // Don't save if video just started or almost finished
            if (currentPosition > RESUME_THRESHOLD && currentPosition < duration - 5000) {
                videoPositions.put(currentVideoPath, currentPosition);
            }
        }
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private String formatTime(int milliseconds) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                      TimeUnit.MINUTES.toSeconds(minutes);
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView.isPlaying()) {
            videoView.pause();
            isPlaying = false;
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        }
        stopSeekBarUpdate();
        cancelControlsAutoHide();
        saveCurrentPosition(); // Save position when pausing
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSeekBarUpdate();
        cancelControlsAutoHide();
        saveCurrentPosition(); // Save position when closing
        if (videoView != null) {
            videoView.stopPlayback();
        }
    }
}