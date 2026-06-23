# Configuration Guide

## Backend URL

File: `android/app/src/main/java/com/grabam/service/DownloadService.kt`

```kotlin
const val BACKEND_URL = "http://YOUR_BACKEND_IP:8000/extract?url="
```

Use your computer's LAN IP for a physical Android device:

```kotlin
const val BACKEND_URL = "http://192.168.1.100:8000/extract?url="
```

Use the Android emulator host alias when the backend runs on the same computer:

```kotlin
const val BACKEND_URL = "http://10.0.2.2:8000/extract?url="
```

The current source uses `http://172.16.0.2:8000/extract?url=`. Change it if that IP is not reachable from your test device.

## Android SDK

File: `android/local.properties`

```properties
sdk.dir=C\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
```

Use the matching Android SDK path for your machine, then sync Gradle in Android Studio.

## Backend Port

File: `backend/app.py`

```python
app.run(host='0.0.0.0', port=8000, debug=True)
```

If you change the port, update `BACKEND_URL` in the Android app too.

## App ID

File: `android/app/build.gradle`

```gradle
applicationId "com.grabam"
```

Change this only if you want a different package name.

## App Name

File: `android/app/src/main/res/values/strings.xml`

```xml
<string name="app_name">grab am</string>
```

## Test Backend Locally

```bash
cd backend
python app.py
```

Then test:

```bash
curl "http://localhost:8000/health"
curl "http://localhost:8000/extract?url=https://www.youtube.com/shorts/abcD_123-xy"
curl "http://localhost:8000/extract?url=https://x.com/example/status/1234567890"
```

## Test Android Connection

1. Make sure the phone and computer are on the same network.
2. Disable VPN on both devices during testing.
3. Open `http://YOUR_IP:8000/health` in the phone browser.
4. Confirm it returns `{"status":"healthy"}`.

## Troubleshooting

### Android app cannot connect to backend

- Verify the backend is running.
- Verify `BACKEND_URL` uses an IP reachable from the device.
- Check firewall rules for port `8000`.
- Use `10.0.2.2` only for the Android emulator.

### Downloads fail

- Check backend logs for the yt-dlp error.
- Update backend dependencies with `pip install --upgrade yt-dlp`.
- Confirm the URL is a Twitter/X status video, YouTube watch URL, YouTube short URL, `youtu.be` URL, embed URL, or live URL.
- Some private, age-restricted, region-locked, or login-required videos may need cookies.

### Notifications do not appear

- Grant notification permission on Android 13+.
- Check that system notification settings allow grab am notifications.

### Quick Settings tile does not download

- Copy a supported video URL before tapping the tile.
- Unlock the phone if Android asks; clipboard reads happen from a foreground activity.
- If the clipboard text is unsupported, the tile shows a short error toast instead of starting the service.
