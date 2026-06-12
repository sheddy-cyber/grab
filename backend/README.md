# Clippd Backend Service

This is a Python Flask backend service that extracts video URLs from Twitter/X tweets using yt-dlp.

## Prerequisites

- Python 3.8 or higher
- pip (Python package manager)

## Installation

1. Navigate to the backend directory:
```bash
cd backend
```

2. Install the required dependencies:
```bash
pip install -r requirements.txt
```

## Running the Backend

### Local Development

Run the server:
```bash
python app.py
```

The server will start on `http://0.0.0.0:8000`

### Network Access (for Android App)

To make the backend accessible from your Android device:

1. **Find your local IP address**:
   - Windows: Open Command Prompt and run `ipconfig`
   - Look for the IPv4 Address (e.g., 192.168.1.100)

2. **Run the server**:
```bash
python app.py
```

3. **Update Android app configuration**:
   - Open `DownloadService.kt`
   - Update the BACKEND_URL:
   ```kotlin
   const val BACKEND_URL = "http://YOUR_IP:8000/extract?url="
   ```
   - Replace `YOUR_IP` with your actual IP address

## API Endpoints

### Extract Video URL

**Endpoint**: `GET /extract`

**Query Parameters**:
- `url`: The Twitter/X video URL

**Example Request**:
```
http://192.168.1.100:8000/extract?url=https://twitter.com/user/status/1234567890
```

**Example Response**:
```json
{
  "download_url": "https://video.twimg.com/ext_tw_video/1234567890/pu/vid/720x1280/...",
  "title": "Amazing video tweet",
  "duration": 30,
  "thumbnail": "https://pbs.twimg.com/ext_tw_video_thumb/1234567890/pu/img/..."
}
```

**Error Response**:
```json
{
  "error": "Invalid Twitter/X URL"
}
```

### Health Check

**Endpoint**: `GET /health`

**Response**:
```json
{
  "status": "healthy"
}
```

## Testing

Test the backend using curl or a web browser:

```bash
curl "http://localhost:8000/extract?url=https://twitter.com/user/status/1234567890"
```

Or use a tool like Postman to test the API.

## Troubleshooting

### Connection Refused

- Make sure the backend is running
- Check that you're using the correct IP address
- Ensure your firewall allows connections on port 8000

### yt-dlp Errors

- yt-dlp may need updates if Twitter changes their API
- Update yt-dlp: `pip install --upgrade yt-dlp`
- Some videos may be age-restricted or require authentication

### CORS Issues

- The backend includes CORS support, but if you encounter CORS issues, check your browser console for specific errors

## Deployment Options

### Local Network

Run on your local machine and access from devices on the same network.

### Cloud Deployment

Deploy to a cloud service like:
- Heroku
- AWS Lambda
- Google Cloud Functions
- DigitalOcean
- Railway

For cloud deployment, you'll need to:
1. Create a requirements.txt file
2. Configure the cloud service to run Flask
3. Set up proper environment variables
4. Update the Android app with the cloud URL

## Security Considerations

- This backend does not include authentication
- For production use, add API keys or rate limiting
- Consider using HTTPS for production deployments
- Implement input validation and sanitization

## Advanced Configuration

### Using Cookies for Authentication

Some Twitter content may require authentication. You can provide cookies:

1. Export cookies from your browser
2. Save as `cookies.txt` in the backend directory
3. Update the cookiefile path in `app.py`:
```python
'cookiefile': 'cookies.txt',
```

### Custom Output Format

Modify the `ydl_opts` dictionary in `app.py` to change video quality or format:

```python
ydl_opts = {
    'format': 'worst',  # Lowest quality
    # or
    'format': 'best[height<=720]',  # Best quality up to 720p
}
```

## Support

For issues with yt-dlp, check their documentation: https://github.com/yt-dlp/yt-dlp

For backend issues, ensure:
- Python version is compatible
- All dependencies are installed
- The URL is a valid Twitter/X video URL
