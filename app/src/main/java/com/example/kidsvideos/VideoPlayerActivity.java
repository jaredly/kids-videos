package com.example.kidsvideos;

import android.media.MediaPlayer;
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
import java.util.concurrent.TimeUnit;

public class VideoPlayerActivity extends AppCompatActivity {

    private VideoView videoView;
    private ImageButton btnPlayPause;
    private ImageButton btnClose;
    private SeekBar seekBar;
    private TextView tvDuration;
    private View controlsLayout;

    private Handler handler = new Handler();
    private boolean isPlaying = false;
    private boolean isUserSeeking = false;
    private Runnable updateSeekBarRunnable;

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
        tvDuration = findViewById(R.id.tv_duration);
        controlsLayout = findViewById(R.id.controls_layout);
    }

    private void setupVideoPlayer() {
        String videoPath = getIntent().getStringExtra("video_path");
        if (videoPath != null) {
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

                    // Auto-play
                    videoView.start();
                    isPlaying = true;
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                    startSeekBarUpdate();
                });

                videoView.setOnCompletionListener(mediaPlayer -> {
                    isPlaying = false;
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                    seekBar.setProgress(0);
                    stopSeekBarUpdate();
                });

                // Show/hide controls on tap
                videoView.setOnClickListener(v -> toggleControlsVisibility());
        }
    }

    private void setupControls() {
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnClose.setOnClickListener(v -> finish());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    videoView.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                videoView.seekTo(seekBar.getProgress());
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
    }

    private void startSeekBarUpdate() {
        updateSeekBarRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isUserSeeking && videoView.isPlaying()) {
                    seekBar.setProgress(videoView.getCurrentPosition());
                }
                handler.postDelayed(this, 1000);
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
            controlsLayout.setVisibility(View.GONE);
        } else {
            controlsLayout.setVisibility(View.VISIBLE);
            // Auto-hide after 3 seconds
            handler.postDelayed(() -> controlsLayout.setVisibility(View.GONE), 3000);
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSeekBarUpdate();
    }
}