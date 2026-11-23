# grab Backend Service

Flask service that uses `yt-dlp` to extract direct download URLs for supported videos.

## Supported Sources

- Twitter/X status videos
- Facebook videos/reels
- Instagram reels/posts with video
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

- `url`: Twitter/X, YouTube, Facebook, or Instagram video URL.

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

- Keep `yt-dlp` updated; YouTube, Twitter/X, Facebook, and Instagram extraction rules change often.
- Some age-restricted, private, region-locked, or login-gated videos may require cookies.
- Make sure your firewall allows inbound connections on port `8000` for physical devices.
- Confirm the phone and computer are on the same network and not isolated by VPN settings.

## Optional Cookies

Some videos (and YouTube bot checks) need a logged-in session. Export browser cookies in
Netscape `cookies.txt` format, ideally from a spare account, and give them to the backend
**without committing them**. The backend checks, in order:

1. `COOKIES_TXT` environment variable containing the file's contents
2. `COOKIES_FILE` environment variable pointing at a file
3. `/etc/secrets/cookies.txt` (Render **Environment → Secret Files**, filename `cookies.txt`)
4. `backend/cookies.txt` for local development (git-ignored)

It copies them to a private temp file at startup because yt-dlp writes refreshed cookies back,
and secret mounts are read-only. The startup log says where the cookies were loaded from.
Restart the service after changing them.

## YouTube on cloud hosts

YouTube blocks many datacenter IPs (Render, AWS, etc.): logs show `HTTP Error 429` on the
first webpage request, then `403` on API calls. Cookies alone don't fix that. Set
`YTDLP_PROXY` (e.g. `http://user:pass@host:port` or `socks5://...`, ideally a residential
proxy) and YouTube extraction and downloads go through it; other platforms are unaffected.

PO tokens come from the bgutil server that `start.sh` launches on port 4416; yt-dlp finds it
through the `youtubepot-bgutilhttp` extractor argument.

## Production Notes

- Add authentication or rate limiting before exposing the backend publicly.
- Use HTTPS in production.
- Run behind a production WSGI server instead of Flask debug mode.
