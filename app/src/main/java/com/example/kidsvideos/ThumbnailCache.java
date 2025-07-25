package com.example.kidsvideos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ThumbnailCache {
    private static final String TAG = "ThumbnailCache";
    private static final String CACHE_DIR_NAME = "video_thumbnails";
    private static final int CACHE_MAX_SIZE_MB = 50; // 50MB cache limit
    private static final int THUMBNAIL_WIDTH = 200;
    private static final int THUMBNAIL_HEIGHT = 150;

    private static ThumbnailCache instance;
    private final File cacheDir;
    private final ExecutorService executor;
    private final ExecutorService precacheExecutor;
    private final Map<String, String> metadataCache; // In-memory cache for metadata
    private Future<?> currentPrecacheTask;

    public interface ThumbnailCallback {
        void onThumbnailLoaded(Bitmap thumbnail);
    }

    public interface PrecacheProgressCallback {
        void onProgress(int processed, int total);
        void onComplete();
    }

    private ThumbnailCache(Context context) {
        // Create cache directory in app's cache folder
        cacheDir = new File(context.getCacheDir(), CACHE_DIR_NAME);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        executor = Executors.newFixedThreadPool(3); // Limit concurrent thumbnail generation
        precacheExecutor = Executors.newFixedThreadPool(2); // Dedicated threads for precaching
        metadataCache = new ConcurrentHashMap<>();

        // Clean up old cache files on startup
        cleanupCache();
    }

    public static synchronized ThumbnailCache getInstance(Context context) {
        if (instance == null) {
            instance = new ThumbnailCache(context.getApplicationContext());
        }
        return instance;
    }

    public void getThumbnail(Context context, java.io.File videoFile, ThumbnailCallback callback) {
        executor.execute(() -> {
            try {
                Bitmap thumbnail = loadThumbnail(context, videoFile);
                if (callback != null) {
                    // Post back to main thread
                    android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                    mainHandler.post(() -> callback.onThumbnailLoaded(thumbnail));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading thumbnail for " + videoFile.getAbsolutePath(), e);
                if (callback != null) {
                    android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                    mainHandler.post(() -> callback.onThumbnailLoaded(null));
                }
            }
        });
    }

    // New method to cache metadata
    public void cacheMetadata(java.io.File videoFile, String metadataType, String value) {
        String cacheKey = generateCacheKey(videoFile) + "_" + metadataType;
        metadataCache.put(cacheKey, value);

        // Also save to disk for persistence
        executor.execute(() -> {
            File metadataFile = new File(cacheDir, cacheKey + ".txt");
            try (FileWriter writer = new FileWriter(metadataFile)) {
                writer.write(value);
            } catch (IOException e) {
                Log.e(TAG, "Failed to save metadata to cache", e);
            }
        });
    }

    // New method to retrieve cached metadata
    public String getCachedMetadata(java.io.File videoFile, String metadataType) {
        String cacheKey = generateCacheKey(videoFile) + "_" + metadataType;

        // Check in-memory cache first
        String cached = metadataCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Check disk cache
        File metadataFile = new File(cacheDir, cacheKey + ".txt");
        if (metadataFile.exists() && metadataFile.lastModified() >= videoFile.lastModified()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(metadataFile))) {
                String value = reader.readLine();
                if (value != null) {
                    metadataCache.put(cacheKey, value); // Cache in memory for next time
                    return value;
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to read cached metadata", e);
            }
        }

        return null;
    }

    // New method to precache metadata for all videos
    public void precacheMetadata(Context context, List<java.io.File> videoFiles, PrecacheProgressCallback callback) {
        // Cancel any existing precaching
        cancelPrecaching();

        if (videoFiles == null || videoFiles.isEmpty()) {
            if (callback != null) {
                callback.onComplete();
            }
            return;
        }

        Log.d(TAG, "Starting metadata precaching for " + videoFiles.size() + " videos");

        currentPrecacheTask = precacheExecutor.submit(() -> {
            int total = videoFiles.size();
            int processed = 0;

            for (java.io.File videoFile : videoFiles) {
                // Check if task was cancelled
                if (Thread.currentThread().isInterrupted()) {
                    Log.d(TAG, "Precaching cancelled");
                    return;
                }

                try {
                    // Check if metadata is already cached
                    String cachedDuration = getCachedMetadata(videoFile, "duration");

                    if (cachedDuration == null) {
                        // Calculate and cache duration
                        String duration = calculateVideoDuration(context, videoFile);
                        if (duration != null) {
                            cacheMetadata(videoFile, "duration", duration);
                            Log.d(TAG, "Precached duration for: " + videoFile.getName());
                        }
                    }

                    processed++;

                    // Update progress on main thread
                    if (callback != null) {
                        final int currentProgress = processed;
                        android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                        mainHandler.post(() -> callback.onProgress(currentProgress, total));
                    }

                    // Small delay to prevent overwhelming the system
                    Thread.sleep(50);

                } catch (InterruptedException e) {
                    Log.d(TAG, "Precaching interrupted");
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Error precaching metadata for " + videoFile.getAbsolutePath(), e);
                    processed++; // Still count as processed even if failed
                }
            }

            Log.d(TAG, "Metadata precaching completed for " + processed + " videos");

            // Notify completion on main thread
            if (callback != null) {
                android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                mainHandler.post(callback::onComplete);
            }
        });
    }

    // Method to cancel ongoing precaching
    public void cancelPrecaching() {
        if (currentPrecacheTask != null && !currentPrecacheTask.isDone()) {
            currentPrecacheTask.cancel(true);
            Log.d(TAG, "Cancelled ongoing precaching");
        }
    }

    // Helper method to calculate video duration
    private String calculateVideoDuration(Context context, java.io.File videoFile) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();

            // Check if it's a content URI or regular file path
            String path = videoFile.getAbsolutePath();
            if (path.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(path));
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
            Log.e(TAG, "Error calculating duration for " + videoFile.getAbsolutePath(), e);
        }
        return "Unknown";
    }

    private Bitmap loadThumbnail(Context context, java.io.File videoFile) {
        String cacheKey = generateCacheKey(videoFile);
        File cacheFile = new File(cacheDir, cacheKey + ".jpg");

        // Check if cached thumbnail exists and is newer than video file
        if (cacheFile.exists() && cacheFile.lastModified() >= videoFile.lastModified()) {
            try {
                Bitmap cachedThumbnail = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
                if (cachedThumbnail != null) {
                    Log.d(TAG, "Loaded cached thumbnail for " + videoFile.getName());
                    return cachedThumbnail;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to load cached thumbnail, will regenerate", e);
                cacheFile.delete(); // Delete corrupted cache file
            }
        }

        // Generate new thumbnail
        Bitmap thumbnail = generateThumbnail(context, videoFile);
        if (thumbnail != null) {
            // Cache the thumbnail
            saveThumbnailToCache(thumbnail, cacheFile);
            Log.d(TAG, "Generated and cached thumbnail for " + videoFile.getName());
        }

        return thumbnail;
    }

    private Bitmap generateThumbnail(Context context, java.io.File videoFile) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();

            // Check if it's a content URI or regular file path
            String path = videoFile.getAbsolutePath();
            if (path.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(path));
            } else {
                retriever.setDataSource(path);
            }

            // Get video duration and calculate halfway point
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long thumbnailTime = 1000000; // Default to 1 second if duration unavailable

            if (durationStr != null && !durationStr.isEmpty()) {
                try {
                    long durationMs = Long.parseLong(durationStr);
                    // Get frame from halfway through the video (convert ms to microseconds)
                    thumbnailTime = (durationMs / 2) * 1000;

                    // Ensure we don't go beyond video duration and have a minimum of 0.5 seconds
                    thumbnailTime = Math.max(500000, Math.min(thumbnailTime, durationMs * 1000 - 100000));
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid duration format, using default time", e);
                }
            }

            // Get frame at calculated time
            Bitmap rawThumbnail = retriever.getFrameAtTime(thumbnailTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            retriever.release();

            if (rawThumbnail != null) {
                // Scale thumbnail to consistent size to save memory and disk space
                return Bitmap.createScaledBitmap(rawThumbnail, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error generating thumbnail for " + videoFile.getAbsolutePath(), e);
        }
        return null;
    }

    private void saveThumbnailToCache(Bitmap thumbnail, File cacheFile) {
        try (FileOutputStream out = new FileOutputStream(cacheFile)) {
            thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, out);
        } catch (IOException e) {
            Log.e(TAG, "Failed to save thumbnail to cache", e);
        }
    }

    private String generateCacheKey(java.io.File videoFile) {
        try {
            // Use MD5 hash of file path + last modified time for cache key
            String input = videoFile.getAbsolutePath() + "_" + videoFile.lastModified();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash if MD5 is not available
            return String.valueOf((videoFile.getAbsolutePath() + videoFile.lastModified()).hashCode());
        }
    }

    private void cleanupCache() {
        executor.execute(() -> {
            try {
                long totalSize = 0;
                File[] cacheFiles = cacheDir.listFiles();
                if (cacheFiles != null) {
                    // Calculate total cache size
                    for (File file : cacheFiles) {
                        totalSize += file.length();
                    }

                    // If cache exceeds limit, delete oldest files
                    long maxSizeBytes = CACHE_MAX_SIZE_MB * 1024L * 1024L;
                    if (totalSize > maxSizeBytes) {
                        Log.d(TAG, "Cache size (" + (totalSize / 1024 / 1024) + "MB) exceeds limit, cleaning up...");

                        // Sort files by last modified time (oldest first)
                        java.util.Arrays.sort(cacheFiles, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));

                        // Delete oldest files until we're under the limit
                        for (File file : cacheFiles) {
                            if (totalSize <= maxSizeBytes) break;
                            totalSize -= file.length();
                            if (file.delete()) {
                                Log.d(TAG, "Deleted old cache file: " + file.getName());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during cache cleanup", e);
            }
        });
    }

    public void clearCache() {
        // Cancel any ongoing precaching
        cancelPrecaching();

        executor.execute(() -> {
            try {
                File[] cacheFiles = cacheDir.listFiles();
                if (cacheFiles != null) {
                    for (File file : cacheFiles) {
                        file.delete();
                    }
                }
                metadataCache.clear(); // Clear in-memory cache too
                Log.d(TAG, "Cache cleared");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing cache", e);
            }
        });
    }

    public void shutdown() {
        cancelPrecaching();
        executor.shutdown();
        precacheExecutor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            if (!precacheExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                precacheExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            precacheExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}