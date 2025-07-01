#!/bin/bash

# Kids Videos Android App Build Script for macOS
# This script builds the Android app from the command line

set -e  # Exit on any error

echo "🎬 Building Kids Videos Android App..."
echo "======================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if we're in the right directory
if [ ! -f "settings.gradle" ]; then
    print_error "settings.gradle not found! Please run this script from the project root directory."
    exit 1
fi

# Check if Android SDK is available
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    print_warning "ANDROID_HOME or ANDROID_SDK_ROOT not set."
    print_warning "Trying to use Android SDK from common locations..."

    # Common Android SDK locations on macOS
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
        export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
        print_status "Using Android SDK at: $ANDROID_HOME"
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
        export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
        print_status "Using Android SDK at: $ANDROID_HOME"
    else
        print_error "Android SDK not found! Please install Android Studio or set ANDROID_HOME."
        print_error "Common locations:"
        print_error "  - ~/Library/Android/sdk"
        print_error "  - ~/Android/Sdk"
        exit 1
    fi
fi

# Set Java 17 for Android Gradle Plugin 8.x
if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11 2>/dev/null || /usr/libexec/java_home)
    print_status "Using Java: $(java -version 2>&1 | head -n1)"
fi

# Add Android tools to PATH
export PATH="$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools:$PATH"

# Setup Gradle Wrapper if needed
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    print_status "Gradle wrapper JAR not found. Setting up Gradle wrapper..."
    if command -v gradle >/dev/null 2>&1; then
        gradle wrapper --gradle-version 8.2
        print_success "Gradle wrapper created successfully"
    else
        print_error "Gradle wrapper JAR missing and 'gradle' command not found!"
        print_error "Please install Gradle or download the wrapper JAR manually:"
        print_error "  curl -L https://services.gradle.org/distributions/gradle-8.2-bin.zip -o gradle.zip"
        print_error "  unzip gradle.zip && ./gradle-8.2/bin/gradle wrapper"
        exit 1
    fi
fi

# Make gradlew executable
if [ -f "gradlew" ]; then
    chmod +x gradlew
    print_status "Made gradlew executable"
else
    print_error "gradlew not found! This might not be a valid Android project."
    exit 1
fi

# Clean previous builds
print_status "Cleaning previous builds..."
./gradlew clean

# Build the app
print_status "Building the app..."
./gradlew assembleDebug

# Check if build was successful
if [ $? -eq 0 ]; then
    print_success "Build completed successfully! 🎉"
    echo ""
    print_status "APK files generated:"
    find app/build/outputs/apk -name "*.apk" -exec echo "  - {}" \;
    echo ""
    print_status "To install on a connected device, run:"
    echo "  adb install app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    print_status "To build a release version, run:"
    echo "  ./gradlew assembleRelease"
else
    print_error "Build failed! Check the output above for errors."
    exit 1
fi