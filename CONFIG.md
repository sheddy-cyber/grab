# Configuration Guide

## Backend URL

Set it in the app: tap the gear icon, enter the backend URL, and use **Test connection**.

- Physical device on the same network: `http://192.168.1.100:8000` (your computer's LAN IP)
- Android emulator with the backend on the same computer: `http://10.0.2.2:8000`

The default used on first launch is `BackendConfig.DEFAULT_BACKEND_URL` in
`android/app/src/main/java/com/grab/utils/BackendConfig.kt`. A trailing `/extract?url=` is stripped automatically.

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

If you change the port, update the backend URL in the app's settings too.

## App ID

File: `android/app/build.gradle`

```gradle
applicationId "com.grab"
```

Change this only if you want a different package name.

## App Name

File: `android/app/src/main/res/values/strings.xml`

```xml
<string name="app_name">grab</string>
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
- Verify the backend URL in the app's settings uses an IP reachable from the device (use **Test connection**).
- Check firewall rules for port `8000`.
- Use `10.0.2.2` only for the Android emulator.

### Downloads fail

- Check backend logs for the yt-dlp error.
- Update backend dependencies with `pip install --upgrade yt-dlp`.
- Confirm the URL is a Twitter/X status video, YouTube watch/shorts/youtu.be/embed/live URL, a Facebook watch/videos/reel/fb.watch URL, or an Instagram reel/p/tv URL.
- Some private, age-restricted, region-locked, or login-required videos may need cookies.

### Notifications do not appear

- Grant notification permission on Android 13+.
- Check that system notification settings allow grab notifications.

### Quick Settings tile does not download

- Copy a supported video URL before tapping the tile.
- Unlock the phone if Android asks; clipboard reads happen from a foreground activity.
- The tile opens grab, which reads the clipboard; if it holds no supported link, a short message is shown instead of starting a download.
