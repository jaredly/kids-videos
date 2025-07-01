package com.example.kidsvideos;

import android.media.MediaMetadataRetriever;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private List<File> videoFiles;
    private OnVideoClickListener listener;

    public interface OnVideoClickListener {
        void onVideoClick(File videoFile);
    }

    public VideoAdapter(List<File> videoFiles, OnVideoClickListener listener) {
        this.videoFiles = videoFiles;
        this.listener = listener;
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

    class VideoViewHolder extends RecyclerView.ViewHolder {
        private TextView tvVideoName;
        private TextView tvVideoDuration;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
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
            String fileName = videoFile.getName();
            tvVideoName.setText(fileName);

            // Get video duration
            String duration = getVideoDuration(videoFile);
            tvVideoDuration.setText("Duration: " + duration);
        }

        private String getVideoDuration(File videoFile) {
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(videoFile.getAbsolutePath());
                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                retriever.release();

                if (durationStr != null) {
                    long duration = Long.parseLong(durationStr);
                    long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
                    long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) -
                                  TimeUnit.MINUTES.toSeconds(minutes);
                    return String.format("%02d:%02d", minutes, seconds);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "Unknown";
        }
    }
}