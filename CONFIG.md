# Configuration Guide

## Required Configuration

### 1. Backend URL Configuration

**File**: `android/app/src/main/java/com/clippd/service/DownloadService.kt`

**Line**: 27
```kotlin
const val BACKEND_URL = "http://YOUR_BACKEND_IP:8000/extract?url="
```

**Steps**:
1. Find your local IP address (run `ipconfig` on Windows or `ifconfig` on Mac/Linux)
2. Replace `YOUR_BACKEND_IP` with your actual IP address
3. Example: `const val BACKEND_URL = "http://192.168.1.100:8000/extract?url="`

### 2. Android SDK Configuration

**File**: `android/local.properties`

**Steps**:
1. Copy `android/local.properties.example` to `android/local.properties`
2. Add your Android SDK path
3. Example (Windows):
```properties
sdk.dir=C\\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
```
4. Example (Mac/Linux):
```properties
sdk.dir=/Users/YourUsername/Library/Android/sdk
```

## Optional Configuration

### Backend Port

If you want to use a different port than 8000:

**File**: `backend/app.py`

**Last line**:
```python
app.run(host='0.0.0.0', port=8000, debug=True)
```

Change `8000` to your preferred port.

### Android App ID

**File**: `android/app/build.gradle`

**Line**: 13
```gradle
applicationId "com.clippd"
```

Change this if you want a different package name.

### App Name

**File**: `android/app/src/main/res/values/strings.xml`

**Line**: 2
```xml
<string name="app_name">Clippd</string>
```

Change this to rename the app.

## Testing Configuration

### Test Backend Locally

1. Start the backend: `cd backend && python app.py`
2. Test with curl:
```bash
curl "http://localhost:8000/extract?url=https://twitter.com/user/status/1234567890"
```

### Test Android App Connection

1. Make sure your phone and computer are on the same network
2. Disable VPN on both devices
3. Temporarily disable firewall if connection fails
4. Test backend accessibility from phone browser:
   - Open: `http://YOUR_IP:8000/health`
   - Should see: `{"status":"healthy"}`

## Troubleshooting

### Backend Connection Issues

**Problem**: Android app can't connect to backend

**Solutions**:
1. Check that backend is running
2. Verify IP address is correct
3. Ensure both devices are on same network
4. Disable VPN on both devices
5. Check firewall settings
6. Test backend URL in phone browser

### Build Errors

**Problem**: Gradle sync fails

**Solutions**:
1. Verify `local.properties` has correct SDK path
2. Update Android SDK in Android Studio
3. Clean project: `./gradlew clean`
4. Invalidate caches: File > Invalidate Caches > Invalidate and Restart

### Download Failures

**Problem**: Downloads fail with error

**Solutions**:
1. Check backend logs for errors
2. Verify Twitter URL is valid
3. Test backend with curl directly
4. Check network connectivity
5. Ensure backend URL is correct in DownloadService.kt

## Network Configuration for Different Scenarios

### Same Network (Recommended)

Both phone and computer on same WiFi:
- Use computer's local IP (e.g., 192.168.1.100)
- Backend URL: `http://192.168.1.100:8000/extract?url=`

### Emulator Development

Using Android Studio Emulator:
- Use special IP `10.0.2.2` to access host machine
- Backend URL: `http://10.0.2.2:8000/extract?url=`

### Production Deployment

Deployed backend (Heroku, AWS, etc.:
- Use actual domain name
- Backend URL: `https://your-backend.com/extract?url=`
- Ensure HTTPS is supported (Android requires HTTPS by default)
