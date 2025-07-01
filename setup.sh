#!/bin/bash

# Kids Videos Android App Setup Script for macOS
# This script sets up the development environment

set -e  # Exit on any error

echo "🛠️  Setting up Kids Videos Android App Development Environment..."
echo "================================================================="

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

# Check prerequisites
print_status "Checking prerequisites..."

# Check Java
if command -v java >/dev/null 2>&1; then
    JAVA_VERSION=$(java -version 2>&1 | head -n1 | awk -F '"' '{print $2}')
    print_success "Java found: $JAVA_VERSION"
else
    print_error "Java not found! Please install Java JDK 8 or higher."
    print_error "You can install it via Homebrew: brew install openjdk@11"
    exit 1
fi

# Check if Android SDK is available
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    print_warning "ANDROID_HOME or ANDROID_SDK_ROOT not set."

    # Common Android SDK locations on macOS
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
        export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
        print_success "Found Android SDK at: $ANDROID_HOME"
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
        export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
        print_success "Found Android SDK at: $ANDROID_HOME"
    else
        print_error "Android SDK not found! Please install Android Studio first."
        print_error "Download from: https://developer.android.com/studio"
        print_error "Or install via Homebrew: brew install --cask android-studio"
        exit 1
    fi
fi

# Setup Gradle Wrapper
print_status "Setting up Gradle wrapper..."
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    if command -v gradle >/dev/null 2>&1; then
        gradle wrapper --gradle-version 8.2
        print_success "Gradle wrapper created"
    else
        print_status "Installing Gradle via Homebrew..."
        if command -v brew >/dev/null 2>&1; then
            brew install gradle
            gradle wrapper --gradle-version 8.2
            print_success "Gradle installed and wrapper created"
        else
            print_error "Homebrew not found! Please install Gradle manually."
            print_error "Visit: https://gradle.org/install/"
            exit 1
        fi
    fi
else
    print_success "Gradle wrapper already exists"
fi

# Make scripts executable
chmod +x gradlew
chmod +x build.sh
print_success "Made scripts executable"

# Create local.properties if needed
if [ ! -f "local.properties" ]; then
    print_status "Creating local.properties..."
    echo "sdk.dir=$ANDROID_HOME" > local.properties
    print_success "Created local.properties with SDK path"
fi

print_success "Setup completed! 🎉"
echo ""
print_status "Next steps:"
echo "  1. Run './build.sh' to build the app"
echo "  2. Or open the project in Android Studio"
echo "  3. Connect an Android device and run: adb install app/build/outputs/apk/debug/app-debug.apk"
echo ""
print_status "Useful commands:"
echo "  ./build.sh           - Build debug APK"
echo "  ./gradlew clean      - Clean build files"
echo "  ./gradlew assembleRelease - Build release APK"