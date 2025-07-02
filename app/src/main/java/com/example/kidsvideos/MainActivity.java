package com.example.kidsvideos;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.concurrent.Executor;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

        private static final String PREFS_NAME = "KidsVideosPrefs";
    private static final String PREF_SELECTED_FOLDER_URI = "selected_folder_uri";
    private static final String PREF_SORT_ORDER = "sort_order";
    private static final String SORT_DATE_ASC = "date_asc";
    private static final String SORT_DATE_DESC = "date_desc";

    private Toolbar toolbar;
    private TextView tvNoVideos;
    private RecyclerView recyclerVideos;
    private VideoAdapter videoAdapter;
    private List<File> videoFiles;
    private SharedPreferences prefs;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private String currentSortOrder;

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
                    openFolderPicker();
                } else {
                    Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show();
                }
            });

    private ActivityResultLauncher<Intent> folderPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        // Take persistent permission
                        getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        // Save the selected folder URI
                        saveSelectedFolderUri(uri);
                        loadVideosFromUri(uri);
                    }
                }
            });

        @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

                        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentSortOrder = prefs.getString(PREF_SORT_ORDER, SORT_DATE_DESC);

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupBiometricAuthentication();

        // Load previously selected folder or default folder
        loadSavedFolderOrDefault();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvNoVideos = findViewById(R.id.tv_no_videos);
        recyclerVideos = findViewById(R.id.recycler_videos);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Kids Videos");
            getSupportActionBar().setSubtitle("No folder selected");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_select_folder) {
            showFolderChangeConfirmation();
            return true;
        } else if (item.getItemId() == R.id.action_sort) {
            showSortDialog();
            return true;
        } else if (item.getItemId() == R.id.action_clear_cache) {
            showClearCacheDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView() {
        videoFiles = new ArrayList<>();
        videoAdapter = new VideoAdapter(videoFiles, this::playVideo);
        recyclerVideos.setLayoutManager(new GridLayoutManager(this, 2)); // 2 columns
        recyclerVideos.setAdapter(videoAdapter);
    }



    private void showFolderChangeConfirmation() {
        new AlertDialog.Builder(this)
            .setTitle("Change Video Folder")
            .setMessage("Do you really want to change the video folder?\n\nThis will require authentication.")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes", (dialog, which) -> {
                dialog.dismiss();
                authenticateAndSelectFolder();
            })
            .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
            .setCancelable(true)
            .show();
    }

        private void showSortDialog() {
        String[] sortOptions = {"Date Modified (Newest First)", "Date Modified (Oldest First)"};
        int selectedIndex = currentSortOrder.equals(SORT_DATE_DESC) ? 0 : 1;

        new AlertDialog.Builder(this)
            .setTitle("Sort Videos")
            .setIcon(android.R.drawable.ic_menu_sort_alphabetically)
            .setSingleChoiceItems(sortOptions, selectedIndex, (dialog, which) -> {
                String newSortOrder = (which == 0) ? SORT_DATE_DESC : SORT_DATE_ASC;
                if (!newSortOrder.equals(currentSortOrder)) {
                    currentSortOrder = newSortOrder;
                    saveSortOrder();
                    refreshVideoList();
                }
                dialog.dismiss();
            })
            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
            .show();
    }

    private void showClearCacheDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Clear Thumbnail Cache")
            .setMessage("This will delete all cached video thumbnails to free up storage space. Thumbnails will be regenerated as needed.\n\nProceed?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Clear Cache", (dialog, which) -> {
                ThumbnailCache.getInstance(this).clearCache();
                Toast.makeText(this, "Thumbnail cache cleared", Toast.LENGTH_SHORT).show();
                // Refresh video list to regenerate thumbnails
                refreshVideoList();
                dialog.dismiss();
            })
            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
            .show();
    }

    private void setupBiometricAuthentication() {
        Executor executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt((FragmentActivity) this, executor,
            new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    Toast.makeText(MainActivity.this,
                        "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    // Authentication successful, proceed with folder selection
                    proceedWithFolderSelection();
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    Toast.makeText(MainActivity.this,
                        "Authentication failed", Toast.LENGTH_SHORT).show();
                }
            });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
            .setTitle("Folder Selection Access")
            .setSubtitle("Authenticate to change video folder")
            .setDescription("Use your fingerprint, face, or device PIN to access folder settings")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK |
                                    BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build();
    }

    private void authenticateAndSelectFolder() {
        BiometricManager biometricManager = BiometricManager.from(this);

        switch (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK |
                                               BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                // Biometric/PIN authentication is available
                biometricPrompt.authenticate(promptInfo);
                break;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                // No biometric features available - proceed without authentication
                Toast.makeText(this, "No authentication available, proceeding...", Toast.LENGTH_SHORT).show();
                proceedWithFolderSelection();
                break;
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                // Biometric features are currently unavailable
                Toast.makeText(this, "Biometric features are currently unavailable", Toast.LENGTH_SHORT).show();
                proceedWithFolderSelection();
                break;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                // No biometric credentials enrolled - proceed without authentication
                Toast.makeText(this, "No lock screen security set up, proceeding...", Toast.LENGTH_SHORT).show();
                proceedWithFolderSelection();
                break;
            default:
                // Unknown state - proceed without authentication
                proceedWithFolderSelection();
                break;
        }
    }

    private void proceedWithFolderSelection() {
        // Modern document picker doesn't require storage permissions
        openFolderPicker();
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

        private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                       Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        // Start in the Downloads directory if possible
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                Uri downloadsUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Download"
                );
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsUri);
            } catch (Exception e) {
                // Ignore if initial URI setting fails
            }
        }

        folderPickerLauncher.launch(intent);
    }

    private void loadSavedFolderOrDefault() {
        // Try to load previously selected folder first (works with document picker URIs)
        String savedUriString = prefs.getString(PREF_SELECTED_FOLDER_URI, null);
        if (savedUriString != null) {
            try {
                Uri savedUri = Uri.parse(savedUriString);
                // Check if we still have permission to access this URI
                if (hasUriPermission(savedUri)) {
                    loadVideosFromUri(savedUri);
                    return;
                } else {
                    // Permission lost, clear the saved URI
                    clearSavedFolderUri();
                }
            } catch (Exception e) {
                // Invalid URI, clear it
                clearSavedFolderUri();
            }
        }

        // Fall back to common video folders only if we have storage permissions
        if (hasStoragePermission()) {
            loadCommonVideoFolders();
        } else {
            // No saved folder and no storage permissions - show empty state
            videoFiles.clear();
            if (getSupportActionBar() != null) {
                getSupportActionBar().setSubtitle("Tap gear icon to select folder");
            }
            updateEmptyState();
        }
    }

    private void loadDefaultFolder() {
        if (hasStoragePermission()) {
            // Load from common video folders as fallback
            loadCommonVideoFolders();
        }
    }

    private void saveSelectedFolderUri(Uri uri) {
        prefs.edit().putString(PREF_SELECTED_FOLDER_URI, uri.toString()).apply();
    }

    private void clearSavedFolderUri() {
        prefs.edit().remove(PREF_SELECTED_FOLDER_URI).apply();
    }

    private void saveSortOrder() {
        prefs.edit().putString(PREF_SORT_ORDER, currentSortOrder).apply();
    }

    private void refreshVideoList() {
        // Re-sort the current video list and refresh UI
        sortVideoFiles();
        videoAdapter.notifyDataSetChanged();
    }

    private void sortVideoFiles() {
        if (currentSortOrder.equals(SORT_DATE_DESC)) {
            // Sort by date modified, newest first
            videoFiles.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        } else {
            // Sort by date modified, oldest first
            videoFiles.sort((f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
        }
    }

    private boolean hasUriPermission(Uri uri) {
        try {
            DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);
            return documentFile != null && documentFile.exists() && documentFile.canRead();
        } catch (Exception e) {
            return false;
        }
    }

    private void loadCommonVideoFolders() {
        // Try common video folders as fallback
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

        sortVideoFiles();
        updateUI(folder);
    }

    private void loadVideosFromUri(Uri uri) {
        videoFiles.clear();

        try {
            DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);
            if (documentFile != null && documentFile.exists()) {
                DocumentFile[] files = documentFile.listFiles();
                for (DocumentFile file : files) {
                    if (file.isFile() && isVideoFile(file.getName())) {
                        // Create a VideoFile wrapper to hold both DocumentFile and File info
                        videoFiles.add(new File(file.getName()) {
                            @Override
                            public String getAbsolutePath() {
                                return file.getUri().toString();
                            }

                            @Override
                            public boolean exists() {
                                return file.exists();
                            }

                            @Override
                            public String getName() {
                                return file.getName();
                            }

                            @Override
                            public long lastModified() {
                                return file.lastModified();
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error accessing folder: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        sortVideoFiles();
        updateUIForUri(uri);
    }

    private void updateUIForUri(Uri uri) {
        if (uri != null) {
            String path = uri.getLastPathSegment();
            if (path != null) {
                String folderName = extractFolderName(path);
                updateToolbarSubtitle(folderName);
            }
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

    private String extractFolderName(String path) {
        if (path.contains("primary:")) {
            String cleanPath = path.replace("primary:", "");
            if (cleanPath.startsWith("/")) {
                cleanPath = cleanPath.substring(1);
            }
            String[] parts = cleanPath.split("/");
            return parts.length > 0 ? parts[parts.length - 1] : "Storage";
        }
        return path;
    }

    private void updateToolbarSubtitle(String folderName) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle("📁 " + folderName);
        }
    }

    private void updateEmptyState() {
        tvNoVideos.setVisibility(TextView.VISIBLE);
        recyclerVideos.setVisibility(RecyclerView.GONE);
        videoAdapter.notifyDataSetChanged();
    }

        private boolean isVideoFile(File file) {
        if (!file.isFile()) return false;
        return isVideoFile(file.getName());
    }

    private boolean isVideoFile(String fileName) {
        if (fileName == null) return false;
        String name = fileName.toLowerCase();
        return name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mkv") ||
               name.endsWith(".mov") || name.endsWith(".wmv") || name.endsWith(".flv") ||
               name.endsWith(".webm") || name.endsWith(".m4v") || name.endsWith(".3gp");
    }

    private void updateUI(File folder) {
        if (folder != null) {
            updateToolbarSubtitle(folder.getName());
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up thumbnail cache resources
        ThumbnailCache.getInstance(this).shutdown();
    }
}