# Clippd - Twitter Video Downloader

An Android app that allows users to download Twitter/X videos without leaving the app (the Twitter app, that is).

## Features

- **Direct Download**: Paste a Twitter/X video URL in the app and download
- **Share Integration**: Use the native Android share menu to share a tweet directly to Clippd
- **Quick Settings Tile**: Add a tile to your notification shade for quick access
- **Background Downloads**: Downloads continue even when the app is closed
- **Gallery Integration**: Downloaded videos are saved directly to your device's gallery

## Quick Start

For a complete setup guide, see [CONFIG.md](CONFIG.md).

## Setup Instructions

### Prerequisites

- Android Studio with Android SDK installed
- Python 3.8+ (for backend)
- A physical Android device or emulator

### 1. Android Project Setup

1. Open Android Studio
2. Import the project from the `android` directory
3. Copy `local.properties.example` to `local.properties` and add your Android SDK path
4. Sync Gradle files
5. Build and run the app

### 2. Backend Service Setup

A complete backend service implementation is provided in the `backend/` directory.

#### Quick Start

1. **Navigate to the backend directory**:
```bash
cd backend
```

2. **Install dependencies**:
```bash
pip install -r requirements.txt
```

3. **Start the backend server**:
```bash
# On Windows
start.bat

# On Linux/Mac
chmod +x start.sh
./start.sh

# Or directly
python app.py
```

The backend will start on `http://0.0.0.0:8000`

4. **Find your local IP address**:
- Windows: Open Command Prompt and run `ipconfig`
- Look for the IPv4 Address (e.g., `192.168.1.100`)

5. **Configure the Android app**:
- Open `DownloadService.kt`
- Update the `BACKEND_URL`:
```kotlin
const val BACKEND_URL = "http://192.168.1.100:8000/extract?url="
```
- Replace `192.168.1.100` with your actual IP address

For detailed backend setup instructions, see [backend/README.md](backend/README.md)

#### Backend Requirements

The backend should:
- Accept a Twitter URL as a query parameter
- Extract the actual video URL from the tweet
- Return JSON in this format: `{"download_url": "direct_video_url", "title": "video_title"}`

The provided backend uses `yt-dlp` for reliable video extraction from Twitter/X.

## Permissions

The app requires the following permissions:

- `INTERNET`: To fetch video URLs and download files
- `WRITE_EXTERNAL_STORAGE` (Legacy): To save videos to storage
- `READ_MEDIA_VIDEO`: To access video files on Android 13+
- `FOREGROUND_SERVICE`: To show download progress in notifications
- `POST_NOTIFICATIONS`: To show download notifications

## Usage

### Method 1: Share Menu
1. Open Twitter/X
2. Find a video tweet
3. Tap the share button
4. Select "Clippd" from the share menu
5. The download will start automatically

### Method 2: Quick Settings Tile
1. Add the Clippd tile to your quick settings
2. Copy a Twitter video URL to your clipboard
3. Pull down the notification shade
4. Tap the Clippd tile
5. The download will start automatically

### Method 3: Direct Paste
1. Open the Clippd app
2. Paste the Twitter video URL
3. Tap "Download"
4. The download will start

## Development

### Project Structure

```
android/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/clippd/
│   │       │   ├── MainActivity.kt          # Main UI
│   │       │   ├── service/
│   │       │   │   └── DownloadService.kt   # Download service
│   │       │   ├── quicksettings/
│   │       │   │   └── DownloadTile.kt      # Quick settings tile
│   │       │   └── utils/
│   │       │       └── NotificationHelper.kt # Notification management
│   │       ├── res/                          # Resources
│   │       └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
├── settings.gradle
└── gradle.properties
```

### Dependencies

- Kotlin Coroutines: For async operations
- OkHttp: For network requests
- Material Components: For UI components
- AndroidX: For modern Android APIs

## Troubleshooting

For detailed troubleshooting and configuration, see [CONFIG.md](CONFIG.md).

### Common Issues

**Build Errors**
- Make sure you have the latest Android SDK
- Update `local.properties` with your SDK path
- Sync Gradle files after any dependency changes

**Download Failures**
- Check that your backend service is running
- Verify the backend URL in `DownloadService.kt`
- Ensure the backend returns the correct JSON format
- Check network connectivity

**Notification Issues**
- Make sure you granted notification permissions
- Check if the app is allowed to show notifications in system settings

**Backend Connection Issues**
- Ensure phone and computer are on same network
- Disable VPN on both devices
- Check firewall settings
- Test backend URL in phone browser

## Future Enhancements

- [ ] Add support for Instagram, Facebook, and YouTube
- [ ] Implement client-side video URL extraction
- [ ] Add download history
- [ ] Support for downloading multiple videos
- [ ] Video preview before download
- [ ] Quality selection for downloads

## License

This project is for educational purposes only. Please respect Twitter's terms of service and copyright laws when using this app.
