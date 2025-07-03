package com.example.kidsvideos;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private List<File> videoFiles;
    private OnVideoClickListener listener;
    private ExecutorService executorService;
    private Handler mainHandler;

    public interface OnVideoClickListener {
        void onVideoClick(File videoFile);
    }

    public VideoAdapter(List<File> videoFiles, OnVideoClickListener listener) {
        this.videoFiles = videoFiles;
        this.listener = listener;
        this.executorService = Executors.newFixedThreadPool(2); // Limit background threads
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        File videoFile = videoFiles.get(position);
        holder.bind(videoFile);
    }

    @Override
    public int getItemCount() {
        return videoFiles.size();
    }

    @Override
    public void onViewRecycled(@NonNull VideoViewHolder holder) {
        super.onViewRecycled(holder);
        // Cancel any pending operations when view is recycled
        holder.cancelPendingOperations();
    }

    public void cleanup() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    class VideoViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivVideoThumbnail;
        private TextView tvVideoName;
        private TextView tvVideoDuration;
        private Runnable pendingDurationTask;
        private boolean isRecycled = false;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            ivVideoThumbnail = itemView.findViewById(R.id.iv_video_thumbnail);
            tvVideoName = itemView.findViewById(R.id.tv_video_name);
            tvVideoDuration = itemView.findViewById(R.id.tv_video_duration);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onVideoClick(videoFiles.get(position));
                }
            });
        }

        public void bind(File videoFile) {
            isRecycled = false;

            // Set video name immediately
            String fileName = videoFile.getName();
            tvVideoName.setText(fileName);

            // Show loading state for duration
            tvVideoDuration.setText("Loading...");

            // Load video duration asynchronously
            loadVideoDurationAsync(videoFile);

            // Load video thumbnail asynchronously
            loadVideoThumbnail(videoFile, ivVideoThumbnail);
        }

        private void loadVideoDurationAsync(File videoFile) {
            // Cancel any previous duration loading task
            if (pendingDurationTask != null) {
                mainHandler.removeCallbacks(pendingDurationTask);
            }

            // Check if we have cached metadata first
            String cachedDuration = ThumbnailCache.getInstance(itemView.getContext())
                    .getCachedMetadata(videoFile, "duration");

            if (cachedDuration != null) {
                tvVideoDuration.setText("Duration: " + cachedDuration);
                return;
            }

            // Create background task for duration calculation
            pendingDurationTask = () -> {
                if (isRecycled) return;

                String duration = getVideoDuration(videoFile);

                // Cache the duration for future use
                ThumbnailCache.getInstance(itemView.getContext())
                        .cacheMetadata(videoFile, "duration", duration);

                // Update UI on main thread
                mainHandler.post(() -> {
                    if (!isRecycled) {
                        tvVideoDuration.setText("Duration: " + duration);
                    }
                });
            };

            executorService.execute(pendingDurationTask);
        }

        private void loadVideoThumbnail(File videoFile, ImageView imageView) {
            // Set default placeholder
            imageView.setImageResource(android.R.drawable.ic_media_play);

            // Use thumbnail cache for efficient loading
            ThumbnailCache.getInstance(itemView.getContext()).getThumbnail(
                itemView.getContext(),
                videoFile,
                thumbnail -> {
                    if (thumbnail != null && !isRecycled) {
                        imageView.setImageBitmap(thumbnail);
                    }
                }
            );
        }

        private String getVideoDuration(File videoFile) {
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();

                // Check if it's a content URI or regular file path
                String path = videoFile.getAbsolutePath();
                if (path.startsWith("content://")) {
                    retriever.setDataSource(itemView.getContext(), Uri.parse(path));
                } else {
                    retriever.setDataSource(path);
                }

                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                retriever.release();

                if (durationStr != null && !durationStr.isEmpty()) {
                    long duration = Long.parseLong(durationStr);
                    long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
                    long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) -
                                  TimeUnit.MINUTES.toSeconds(minutes);
                    return String.format("%02d:%02d", minutes, seconds);
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Log the specific error for debugging
                System.out.println("Error getting duration for: " + videoFile.getAbsolutePath() + " - " + e.getMessage());
            }
            return "Unknown";
        }

        public void cancelPendingOperations() {
            isRecycled = true;
            if (pendingDurationTask != null) {
                mainHandler.removeCallbacks(pendingDurationTask);
                pendingDurationTask = null;
            }
        }
    }
}