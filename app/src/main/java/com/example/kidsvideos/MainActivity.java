package com.example.kidsvideos;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Button btnSelectFolder;
    private TextView tvCurrentFolder;
    private TextView tvNoVideos;
    private RecyclerView recyclerVideos;
    private VideoAdapter videoAdapter;
    private List<File> videoFiles;

    private ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    selectFolder();
                } else {
                    Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupRecyclerView();
        setupListeners();

        // Load default folder (Movies or Downloads)
        loadDefaultFolder();
    }

    private void initViews() {
        btnSelectFolder = findViewById(R.id.btn_select_folder);
        tvCurrentFolder = findViewById(R.id.tv_current_folder);
        tvNoVideos = findViewById(R.id.tv_no_videos);
        recyclerVideos = findViewById(R.id.recycler_videos);
    }

    private void setupRecyclerView() {
        videoFiles = new ArrayList<>();
        videoAdapter = new VideoAdapter(videoFiles, this::playVideo);
        recyclerVideos.setLayoutManager(new LinearLayoutManager(this));
        recyclerVideos.setAdapter(videoAdapter);
    }

    private void setupListeners() {
        btnSelectFolder.setOnClickListener(v -> {
            if (hasStoragePermission()) {
                selectFolder();
            } else {
                requestStoragePermission();
            }
        });
    }

    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        requestPermissionLauncher.launch(new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.READ_MEDIA_VIDEO
        });
    }

    private void selectFolder() {
        // For simplicity, we'll browse some common video folders
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);

        // Check Downloads folder first
        if (downloadsDir.exists() && downloadsDir.canRead()) {
            loadVideosFromFolder(downloadsDir);
        } else if (moviesDir.exists() && moviesDir.canRead()) {
            loadVideosFromFolder(moviesDir);
        } else if (dcimDir.exists() && dcimDir.canRead()) {
            loadVideosFromFolder(dcimDir);
        } else {
            // Fallback to external storage root
            File externalStorage = Environment.getExternalStorageDirectory();
            if (externalStorage.exists() && externalStorage.canRead()) {
                loadVideosFromFolder(externalStorage);
            }
        }
    }

    private void loadDefaultFolder() {
        if (hasStoragePermission()) {
            selectFolder();
        }
    }

    private void loadVideosFromFolder(File folder) {
        videoFiles.clear();

        if (folder != null && folder.exists() && folder.canRead()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (isVideoFile(file)) {
                        videoFiles.add(file);
                    }
                }
            }
        }

        updateUI(folder);
    }

    private boolean isVideoFile(File file) {
        if (!file.isFile()) return false;

        String name = file.getName().toLowerCase();
        return name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mkv") ||
               name.endsWith(".mov") || name.endsWith(".wmv") || name.endsWith(".flv") ||
               name.endsWith(".webm") || name.endsWith(".m4v") || name.endsWith(".3gp");
    }

    private void updateUI(File folder) {
        if (folder != null) {
            tvCurrentFolder.setText("Folder: " + folder.getAbsolutePath());
            tvCurrentFolder.setVisibility(TextView.VISIBLE);
        }

        if (videoFiles.isEmpty()) {
            tvNoVideos.setVisibility(TextView.VISIBLE);
            recyclerVideos.setVisibility(RecyclerView.GONE);
        } else {
            tvNoVideos.setVisibility(TextView.GONE);
            recyclerVideos.setVisibility(RecyclerView.VISIBLE);
        }

        videoAdapter.notifyDataSetChanged();
    }

    private void playVideo(File videoFile) {
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra("video_path", videoFile.getAbsolutePath());
        startActivity(intent);
    }
}