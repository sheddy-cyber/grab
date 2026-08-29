# grab - Social Video Downloader

An Android app for downloading supported social media videos from the app, the Android share sheet, or a Quick Settings tile.

## Supported Sources

- Twitter/X status videos
- Facebook videos/reels
- Instagram reels/posts with video
- YouTube videos
- YouTube Shorts

## Features

- **Direct Download**: Paste a supported video URL in the app and download it.
- **Share Integration**: Share a Twitter/X, YouTube, Facebook, or Instagram link to grab from another app.
- **Quick Settings Tile**: Copy a supported URL, tap the tile, and start the download.
- **Background Downloads**: Downloads run through a foreground service with progress notifications.
- **Gallery Integration**: Downloads are saved to `Movies/grab`.

## Quick Start

For setup details, see [CONFIG.md](CONFIG.md).

### Android Project

1. Open Android Studio.
2. Import the project from the `android` directory.
3. Ensure `android/local.properties` contains your Android SDK path.
4. Sync Gradle files.
5. Build and run the app.

### Backend Service

The backend in `backend/` uses `yt-dlp` to extract direct media URLs.

```bash
cd backend
pip install -r requirements.txt
python app.py
```

The backend starts on `http://0.0.0.0:8000`.

Update `DownloadService.BACKEND_URL` in `android/app/src/main/java/com/grab/service/DownloadService.kt` so the Android device can reach your backend:

```kotlin
const val BACKEND_URL = "http://192.168.1.100:8000/extract?url="
```

Use `10.0.2.2` when testing from the Android emulator against a backend running on your computer.

## Backend Contract

`GET /extract?url=<encoded-url>` accepts a Twitter/X, YouTube, Facebook, or Instagram URL and returns:

```json
{
  "download_url": "https://...",
  "title": "video title",
  "duration": 30,
  "thumbnail": "https://...",
  "ext": "mp4",
  "mime_type": "video/mp4",
  "headers": {},
  "platform": "youtube"
}
```

The Android client downloads one direct media URL, so the backend prefers single-file formats that already include both video and audio. Keep `yt-dlp` current because YouTube, Twitter/X, Facebook, and Instagram extraction can change.

## Permissions

- `INTERNET`: Fetch extraction metadata and download files.
- `WRITE_EXTERNAL_STORAGE` and `READ_EXTERNAL_STORAGE`: Legacy storage access.
- `READ_MEDIA_VIDEO`: Android 13+ media access.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC`: Download progress service.
- `POST_NOTIFICATIONS`: Download notifications on Android 13+.

## Usage

### Share Menu

1. Open Twitter/X, YouTube, Facebook, or Instagram.
2. Share a video, status, or Shorts link.
3. Select grab.
4. The download starts automatically.

### Quick Settings Tile

1. Add the grab tile to Quick Settings.
2. Copy a supported video URL.
3. Tap the grab tile.
4. The download starts from the clipboard link.

### Direct Paste

1. Open grab.
2. Paste a supported video URL.
3. Tap **Download**.

## Project Structure

```text
android/
  app/src/main/java/com/grab/
    MainActivity.kt
    ShareActivity.kt
    quicksettings/
      ClipboardActivity.kt
      DownloadTile.kt
    service/
      DownloadService.kt
    utils/
      NotificationHelper.kt
      UrlUtils.kt
backend/
  app.py
  requirements.txt
```

## Troubleshooting

- Confirm the backend is running and reachable from the phone or emulator.
- Verify `BACKEND_URL` points to the correct IP address and port.
- Test `http://YOUR_IP:8000/health` from the device browser.
- Update `yt-dlp` if extraction starts failing for YouTube, Twitter/X, Facebook, or Instagram.
- Grant notification permission if progress notifications do not appear.

## Notes

This project is for educational use. Respect platform terms and copyright law when downloading content.
