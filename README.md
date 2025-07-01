# Kids Videos Android App

A simple Android app that allows you to browse and play videos from your device's storage.

## Features

- Browse videos from common folders (Downloads, Movies, DCIM)
- Display video list with file names and durations
- Simple video player with minimal controls:
  - Play/Pause button
  - Seek bar for navigation
  - Duration display
  - Tap to show/hide controls
  - Fullscreen landscape mode

## Supported Video Formats

- MP4 (.mp4)
- AVI (.avi)
- MKV (.mkv)
- MOV (.mov)
- WMV (.wmv)
- FLV (.flv)
- WebM (.webm)
- M4V (.m4v)
- 3GP (.3gp)

## Permissions Required

- `READ_EXTERNAL_STORAGE` - To access video files on device storage
- `READ_MEDIA_VIDEO` - To read video files on Android 13+

## How to Build

### Option 1: Command Line (macOS)
1. Run the setup script: `./setup.sh`
2. Build the app: `./build.sh`
3. Install on device: `adb install app/build/outputs/apk/debug/app-debug.apk`

### Option 2: Android Studio
1. Open the project in Android Studio
2. Sync the project with Gradle files
3. Build and run on a device or emulator (API level 21+)

### Requirements
- Java JDK 8 or higher
- Android SDK (via Android Studio)
- macOS with Homebrew (recommended)

## How to Use

1. Launch the app
2. Grant storage permissions when prompted
3. The app will automatically scan common video folders
4. Tap on any video to play it
5. In the video player:
   - Tap the screen to show/hide controls
   - Use the play/pause button to control playback
   - Drag the seek bar to navigate through the video
   - Use the back button to return to the video list

## Technical Details

- **Minimum SDK:** 21 (Android 5.0)
- **Target SDK:** 34 (Android 14)
- **Video Player:** Built-in Android VideoView
- **UI Components:** RecyclerView, CardView, Material Design
- **Permissions:** Runtime permission handling

## Notes

- The app automatically scans Downloads, Movies, and DCIM folders
- Video duration is extracted using MediaMetadataRetriever
- The video player is optimized for landscape mode
- Controls auto-hide after 3 seconds of inactivity