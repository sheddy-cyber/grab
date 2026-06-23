# grab am Backend Service

Flask service that uses `yt-dlp` to extract direct download URLs for supported videos.

## Supported Sources

- Twitter/X status videos
- YouTube videos
- YouTube Shorts

## Installation

```bash
cd backend
pip install -r requirements.txt
```

## Running

```bash
python app.py
```

The server listens on `http://0.0.0.0:8000`.

For a physical Android device, use your computer's LAN IP in the Android app. For the Android emulator, use `10.0.2.2`.

## API

### `GET /extract`

Query parameters:

- `url`: Twitter/X or YouTube video URL.

Example:

```text
http://localhost:8000/extract?url=https://www.youtube.com/shorts/abcD_123-xy
```

Response:

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

The Android app downloads one direct URL, so the backend prefers progressive formats that already contain both audio and video.

### `GET /health`

```json
{
  "status": "healthy"
}
```

## Testing

```bash
curl "http://localhost:8000/health"
curl "http://localhost:8000/extract?url=https://www.youtube.com/watch?v=dQw4w9WgXcQ"
curl "http://localhost:8000/extract?url=https://x.com/example/status/1234567890"
```

## Troubleshooting

- Keep `yt-dlp` updated; YouTube and Twitter/X extraction rules change often.
- Some age-restricted, private, region-locked, or login-gated videos may require cookies.
- Make sure your firewall allows inbound connections on port `8000` for physical devices.
- Confirm the phone and computer are on the same network and not isolated by VPN settings.

## Optional Cookies

For content that requires login:

1. Export browser cookies to `cookies.txt`.
2. Save the file in `backend/`.
3. Add this to `ydl_opts` in `app.py`:

```python
'cookiefile': 'cookies.txt',
```

## Production Notes

- Add authentication or rate limiting before exposing the backend publicly.
- Use HTTPS in production.
- Run behind a production WSGI server instead of Flask debug mode.
