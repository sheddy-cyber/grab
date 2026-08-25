from flask import Flask, request, jsonify, Response
from flask_cors import CORS
import yt_dlp
import logging
import os
import requests
import urllib.parse
import time
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FuturesTimeoutError
from urllib.parse import urlparse, parse_qs

app = Flask(__name__)
CORS(app)  # Enable CORS for all routes

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Cache for cobalt.directory instance list (avoids re-fetching on fallback)
_cobalt_cache = {'urls': {}, 'ts': 0}
_COBALT_CACHE_TTL = 300  # seconds — longer TTL to reduce API calls

def _get_cobalt_instances(platform):
    """Fetch working Cobalt instances, with a short TTL cache."""
    now = time.time()
    cache_key = platform
    cached_entry = _cobalt_cache['urls'].get(cache_key) if isinstance(_cobalt_cache['urls'], dict) else None
    if cached_entry is not None and (now - _cobalt_cache['ts']) < _COBALT_CACHE_TTL:
        logger.info(f"Using cached Cobalt instance list for {cache_key} ({len(cached_entry)} instances)")
        return list(cached_entry)  # return a copy

    browser_ua = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
    logger.info("Fetching working Cobalt APIs from cobalt.directory...")
    urls = []
    try:
        res = requests.get(
            'https://cobalt.directory/api/working?type=api',
            headers={'User-Agent': browser_ua},
            timeout=8
        )
        if res.status_code == 200:
            data = res.json()
            # cobalt.directory may return different response shapes:
            #   - { "data": { "youtube": [...], "instagram": [...] } }  (grouped by service)
            #   - { "data": [ { "url": "...", "services": [...] }, ... ] }  (flat list)
            #   - [ { "api": "...", ... }, ... ]  (legacy flat array)
            raw = data.get('data', data) if isinstance(data, dict) else data

            key_map = {
                'youtube': 'youtube',
                'twitter_x': 'twitter',
                'facebook': 'facebook',
                'instagram': 'instagram',
            }
            key = key_map.get(platform, 'twitter')

            if isinstance(raw, dict):
                # Grouped-by-service shape
                urls = raw.get(key, [])
                # If the values are dicts with a 'url' field, extract them
                if urls and isinstance(urls[0], dict):
                    urls = [u.get('url') or u.get('api') for u in urls if u.get('url') or u.get('api')]
            elif isinstance(raw, list):
                # Flat list of instance objects — filter by service support
                for item in raw:
                    if isinstance(item, str):
                        urls.append(item)
                    elif isinstance(item, dict):
                        instance_url = item.get('url') or item.get('api') or item.get('endpoint')
                        services = item.get('services', [])
                        # Include if it supports our platform or has no service filter
                        if instance_url:
                            if not services or key in services or platform in services:
                                urls.append(instance_url)

            # Normalize URLs: strip trailing slashes, ensure they look like API endpoints
            cleaned = []
            for u in urls:
                if isinstance(u, str) and u.startswith('http'):
                    cleaned.append(u.rstrip('/'))
            urls = cleaned

            logger.info(f"Found {len(urls)} working Cobalt instances for {key}")
    except Exception as e:
        logger.error(f"Failed to fetch from cobalt.directory: {str(e)}")

    # Always include the official instance as a fallback
    if 'https://api.cobalt.tools' not in urls:
        urls.append('https://api.cobalt.tools')

    if not isinstance(_cobalt_cache['urls'], dict):
        _cobalt_cache['urls'] = {}
    _cobalt_cache['urls'][cache_key] = urls
    _cobalt_cache['ts'] = now
    return list(urls)

def extract_with_cobalt(video_url):
    """
    Extract video direct URL using cobalt.tools API instances.
    Tries ALL available instances (skipping auth-required ones).
    """
    platform = detect_platform(video_url)
    
    browser_user_agent = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
    
    # 1. Determine Cobalt API URLs to try
    custom_cobalt_url = os.environ.get('COBALT_API_URL')
    
    if custom_cobalt_url:
        cobalt_urls = [custom_cobalt_url]
    else:
        cobalt_urls = _get_cobalt_instances(platform)
            
    # 2. Try the Cobalt URLs
    headers = {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        'User-Agent': browser_user_agent
    }
    cobalt_api_key = os.environ.get('COBALT_API_KEY')
    if cobalt_api_key:
        headers['Authorization'] = f"Api-Key {cobalt_api_key}"
        
    payload = {
        'url': video_url,
        'videoQuality': '1080',
        'alwaysProxy': True
    }
    
    real_attempts = 0  # count only instances that actually responded with usable data
    
    for cobalt_url in cobalt_urls:
        logger.info(f"Attempting Cobalt extraction using: {cobalt_url}")
        
        try:
            # Increased timeout to 60s to handle cold starts on free hosting platforms (like Render)
            response = requests.post(cobalt_url, json=payload, headers=headers, timeout=60)
            
            if response.status_code == 200:
                data = response.json()
                status = data.get('status')
                logger.info(f"Cobalt response status from {cobalt_url}: {status}")
                
                if status in ('tunnel', 'redirect'):
                    download_url = data.get('url')
                    title = data.get('filename')
                    if download_url:
                        # For 'redirect' URLs, validate the stream.
                        # For 'tunnel' URLs, skip validation — tunnel endpoints are
                        # ephemeral proxies that don't support partial/HEAD reads.
                        if status == 'tunnel' or is_valid_download_url(download_url):
                            ext = 'mp4'
                            if title:
                                _, parsed_ext = os.path.splitext(title)
                                if parsed_ext:
                                    ext = parsed_ext.lstrip('.')
                            else:
                                title = f"grab_am_video_{int(time.time())}"
                            
                            title = title.replace('/', '_').replace('\\', '_').replace(':', '_')

                            return {
                                'download_url': download_url,
                                'title': title,
                                'duration': None,
                                'thumbnail': None,
                                'ext': ext,
                                'mime_type': 'video/mp4',
                                'headers': {},
                                'platform': platform,
                                'is_cobalt': True
                            }
                        else:
                            logger.warning(f"Cobalt instance {cobalt_url} returned an invalid/empty stream. Skipping.")
                            real_attempts += 1
                elif status == 'picker':
                    picker_items = data.get('picker', [])
                    if picker_items:
                        for item in picker_items:
                            download_url = item.get('url')
                            if download_url:
                                title = f"grab_am_picker_{int(time.time())}"
                                return {
                                    'download_url': download_url,
                                    'title': title,
                                    'duration': None,
                                    'thumbnail': item.get('thumb'),
                                    'ext': 'mp4',
                                    'mime_type': 'video/mp4',
                                    'headers': {},
                                    'platform': platform,
                                    'is_cobalt': True
                                }
                elif status == 'error':
                    error_info = data.get('error', {})
                    error_code = error_info.get('code') if isinstance(error_info, dict) else str(error_info)
                    # Skip auth-required instances without counting as a real attempt
                    if 'auth' in str(error_code).lower() or 'jwt' in str(error_code).lower():
                        logger.info(f"Cobalt instance {cobalt_url} requires auth — skipping.")
                        continue
                    logger.warning(f"Cobalt instance {cobalt_url} returned error: {error_code}")
                    real_attempts += 1
            else:
                # Check if it's an auth error in the HTTP body
                try:
                    err_body = response.json()
                    err_code = str(err_body.get('error', {}).get('code', ''))
                    if 'auth' in err_code.lower() or 'jwt' in err_code.lower():
                        logger.info(f"Cobalt instance {cobalt_url} requires auth (HTTP {response.status_code}) — skipping.")
                        continue
                except Exception:
                    pass
                logger.warning(f"Cobalt instance {cobalt_url} returned HTTP {response.status_code}")
                real_attempts += 1
        except requests.exceptions.Timeout:
            logger.warning(f"Cobalt instance {cobalt_url} timed out — skipping.")
            real_attempts += 1
        except Exception as e:
            logger.error(f"Error during Cobalt extraction on {cobalt_url}: {str(e)}")
            real_attempts += 1
    
    logger.warning(f"All Cobalt instances exhausted ({real_attempts} real attempts, {len(cobalt_urls)} total).")
    return None

def build_upstream_headers(video_url, client_request):
    """Build CDN-friendly headers for the upstream video request."""
    browser_user_agent = (
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
        '(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36'
    )
    headers = {
        'User-Agent': client_request.headers.get('User-Agent') or browser_user_agent,
        'Accept': '*/*',
    }

    range_header = client_request.headers.get('Range')
    if range_header:
        headers['Range'] = range_header

    host = (urlparse(video_url).hostname or '').lower()
    if 'googlevideo.com' in host:
        headers['Referer'] = 'https://www.youtube.com/'
        headers['Origin'] = 'https://www.youtube.com'
    elif 'twimg.com' in host or is_twitter_host(host):
        headers['Referer'] = 'https://x.com/'
        headers['Origin'] = 'https://x.com'
    elif 'fbcdn.net' in host or is_facebook_host(host):
        headers['Referer'] = 'https://www.facebook.com/'
        headers['Origin'] = 'https://www.facebook.com'
    elif 'cdninstagram.com' in host or is_instagram_host(host):
        headers['Referer'] = 'https://www.instagram.com/'
        headers['Origin'] = 'https://www.instagram.com'

    return headers

def proxied_download_url(url):
    """Route all client downloads through our backend so headers/IP stay consistent."""
    return f"{request.host_url.rstrip('/')}/proxy?url={urllib.parse.quote(url, safe='')}"

def finalize_download_response(payload):
    """Ensure the Android client always receives a proxied, playable download URL."""
    url = payload.get('download_url', '')
    is_cobalt = payload.pop('is_cobalt', False)
    
    # Don't double proxy Cobalt URLs (tunnel/redirects are handled by Cobalt).
    # Double proxying causes Gunicorn worker timeouts for large files and stream corruption.
    if payload.get('download_url') and not is_cobalt:
        payload['download_url'] = proxied_download_url(payload['download_url'])
        payload['headers'] = {}
    return payload

def is_valid_download_url(url):
    """
    Validate that the download URL returns actual stream data (not empty or 404/error).
    """
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
    }
    try:
        logger.info(f"Checking validity of download URL: {url}")
        res_get = requests.get(url, headers=headers, stream=True, timeout=6)
        if res_get.status_code == 200:
            content_type = (res_get.headers.get('content-type') or '').lower()
            if 'text/html' in content_type or 'application/json' in content_type:
                logger.warning("Download URL check failed: non-video content type returned.")
                return False

            for chunk in res_get.iter_content(chunk_size=64):
                if len(chunk) > 0:
                    if _looks_like_error_payload(chunk):
                        logger.warning("Download URL check failed: payload looks like HTML/JSON error.")
                        return False
                    logger.info("Download URL is valid (returned non-error bytes).")
                    return True
                break
            logger.warning("Download URL check failed: Stream contains 0 bytes.")
            return False
        else:
            logger.warning(f"Download URL check failed: HTTP status {res_get.status_code}")
            return False
    except Exception as e:
        logger.error(f"Download URL check failed with exception: {str(e)}")
        return False

def _looks_like_error_payload(data):
    prefix = data[:64].lstrip().decode('utf-8', errors='ignore').lower()
    return (
        prefix.startswith('<!doctype') or
        prefix.startswith('<html') or
        prefix.startswith('<?xml') or
        prefix.startswith('{') or
        prefix.startswith('[') or
        prefix.startswith('#extm3u')
    )

def _detect_media_format(data):
    if len(data) < 12:
        return None
    if data[4:8] == b'ftyp' or data[4:8] == b'moov':
        return {'ext': 'mp4', 'mime_type': 'video/mp4'}
    if data[:4] == b'\x1a\x45\xdf\xa3':
        return {'ext': 'webm', 'mime_type': 'video/webm'}
    # Check for MPEG-TS (used in HLS segments)
    if len(data) >= 188 and data[0] == 0x47:
        return {'ext': 'ts', 'mime_type': 'video/mp2t'}
    return None

def is_direct_progressive_format(format_info):
    protocol = (format_info.get('protocol') or '').lower()
    # Allow only http and https (direct progressive streams).
    # Reject all segmented formats: DASH, HLS (m3u8, m3u8_native), ISM, etc.
    # These need ffmpeg or a player that understands manifests — our android
    # client expects a single playable URL.
    if protocol not in {'http', 'https'}:
        return False

    url = (format_info.get('url') or '').lower()
    if '.mpd' in url or 'manifest' in url or '.m3u8' in url:
        return False

    return True
@app.route('/extract', methods=['GET'])
def extract_video():
    """
    Extract a direct video URL from a supported social media URL.
    
    Query Parameters:
        url: The Twitter/X or YouTube video URL
    
    Returns:
        JSON with 'download_url', 'title', 'ext', 'mime_type', and optional 'headers'
    """
    try:
        video_url = request.args.get('url')
        
        if not video_url:
            logger.error("No URL provided")
            return jsonify({'error': 'No URL provided'}), 400
        
        if not is_supported_video_url(video_url):
            logger.error(f"Unsupported video URL: {video_url}")
            return jsonify({'error': 'Unsupported URL. Use a Twitter/X, YouTube, Facebook, or Instagram video URL.'}), 400
        
        logger.info(f"Extracting video from: {video_url}")
        platform = detect_platform(video_url)
        
        # Strategy 1: For YouTube, Facebook, and Instagram, prioritize Cobalt.
        # - YouTube: bypasses datacenter IP blocks.
        # - Facebook/Instagram: yt-dlp frequently needs authenticated cookies for
        #   these platforms, so Cobalt (which proxies through its own session
        #   handling) tends to succeed far more often without any cookies file.
        if platform in ('youtube', 'facebook', 'instagram'):
            cobalt_res = extract_with_cobalt(video_url)
            if cobalt_res:
                logger.info(f"Successfully extracted video using Cobalt: {cobalt_res['title']}")
                return jsonify(finalize_download_response(cobalt_res))
            logger.warning(f"Cobalt extraction failed for {platform}. Falling back to yt-dlp...")
            
        # Strategy 2: For Twitter/X, or if Cobalt failed for YouTube/Facebook/Instagram, try yt-dlp
        # On Hugging Face, Instagram/YouTube SSL connections often timeout.
        # Use a shorter yt-dlp timeout to fail fast and avoid killing the Gunicorn worker.
        is_hugging_face = 'SPACE_ID' in os.environ
        if is_hugging_face and platform in ('instagram', 'youtube'):
            ytdlp_socket_timeout = 8
        else:
            ytdlp_socket_timeout = 12

        # Configure yt-dlp options
        ydl_opts = {
            # Strictly prefer progressive MP4 (single file with audio+video) or native HLS (m3u8_native).
            # REJECT DASH formats (bestvideo+bestaudio) which need ffmpeg to merge and aren't directly playable.
            'format': (
                'best[ext=mp4][protocol^=http][vcodec!=none][acodec!=none]/'
                'best[protocol^=http][vcodec!=none][acodec!=none]/'
                'best[vcodec!=none][acodec!=none]/'
                'best'
            ),
            'format_sort': ['res', 'ext:mp4:m4a', 'proto:http'],
            'quiet': True,
            'no_warnings': True,
            'extract_flat': False,
            'noplaylist': True,
            'cachedir': False,
            'socket_timeout': ytdlp_socket_timeout,
            'retries': 0,
            'fragment_retries': 0,
            'extractor_args': {
                'youtube': {
                    'clients': ['web', 'tv']
                },
                'facebook': {
                    'use_graph_api': ['false']
                },
                'instagram': {
                    'api_version': 'v1'
                }
            }
        }

        # If a custom cookies file exists in the directory, use it to authenticate
        # This is especially important for Facebook and Instagram
        cookies_loaded = False
        if os.path.exists('cookies.txt'):
            logger.info("Loading custom cookies from cookies.txt")
            ydl_opts['cookiefile'] = 'cookies.txt'
            cookies_loaded = True
        
        # Also try browser cookies for platforms that need them (Facebook, Instagram, YouTube)
        # This can be enabled via environment variable
        if os.environ.get('USE_BROWSER_COOKIES', '').lower() in ('1', 'true', 'yes'):
            browsers = ['chrome', 'edge', 'firefox', 'brave', 'safari', 'chromium']
            for browser in browsers:
                try:
                    logger.info(f"Trying cookies from browser: {browser}")
                    # Test by creating a temporary YDL instance with the browser cookies
                    test_opts = {**ydl_opts, 'cookiesfrombrowser': (browser,), 'quiet': True}
                    with yt_dlp.YoutubeDL(test_opts) as test_ydl:
                        # Just verify we can create the extractor without error
                        pass
                    ydl_opts['cookiesfrombrowser'] = (browser,)
                    cookies_loaded = True
                    logger.info(f"Successfully loaded cookies from {browser}")
                    break
                except Exception as e:
                    logger.debug(f"Failed to load cookies from {browser}: {e}")
                    ydl_opts.pop('cookiesfrombrowser', None)
                    continue
        
        if not cookies_loaded and platform in ('facebook', 'instagram'):
            logger.warning(f"No cookies loaded for {platform} - extraction may fail due to authentication requirements. Create a cookies.txt file or set USE_BROWSER_COOKIES=1")
        
        # Extract video info with a hard timeout to prevent Gunicorn worker death
        def _extract(url, opts):
            with yt_dlp.YoutubeDL(opts) as ydl:
                return ydl.extract_info(url, download=False)

        EXTRACT_TIMEOUT = 20  # seconds — must be well under Gunicorn's worker timeout
        info = None
        yt_dlp_error = None
        
        try:
            with ThreadPoolExecutor(max_workers=1) as pool:
                future = pool.submit(_extract, video_url, ydl_opts)
                info = future.result(timeout=EXTRACT_TIMEOUT)
        except FuturesTimeoutError:
            logger.error(f"yt-dlp extraction timed out after {EXTRACT_TIMEOUT}s for: {video_url}")
            yt_dlp_error = f"Video extraction timed out after {EXTRACT_TIMEOUT}s."
        except Exception as e:
            yt_dlp_error = str(e)
            
        if yt_dlp_error:
            # Only try Cobalt fallback if we didn't ALREADY try it as Strategy 1.
            # For youtube/facebook/instagram, Cobalt was already attempted above —
            # retrying with the same instances would just fail again.
            already_tried_cobalt = platform in ('youtube', 'facebook', 'instagram')
            if not already_tried_cobalt:
                logger.warning(f"yt-dlp failed: {yt_dlp_error}. Trying Cobalt fallback...")
                cobalt_res = extract_with_cobalt(video_url)
                if cobalt_res:
                    logger.info(f"Successfully extracted video using Cobalt fallback: {cobalt_res['title']}")
                    return jsonify(finalize_download_response(cobalt_res))
            else:
                logger.warning(f"yt-dlp failed: {yt_dlp_error}. Cobalt was already tried — skipping redundant retry.")
            
            # If both failed, return the original yt-dlp error or bot block message
            error_msg = yt_dlp_error
            is_bot_detection = "Sign in to confirm you" in error_msg or "Sign in to confirm your" in error_msg or "bot" in error_msg.lower()
            is_login_required = "login required" in error_msg.lower() or "private video" in error_msg.lower() or "this video is private" in error_msg.lower() or "sign in" in error_msg.lower()
            is_hugging_face = 'SPACE_ID' in os.environ

            if is_bot_detection and not is_hugging_face:
                logger.warning("YouTube bot detection triggered. Attempting to bypass using local browser cookies...")
                browsers = ['chrome', 'edge', 'firefox', 'brave']
                success = False
                
                for browser in browsers:
                    logger.info(f"Trying cookies from: {browser}")
                    ydl_opts['cookiesfrombrowser'] = (browser, )
                    try:
                        info = _extract(video_url, ydl_opts)
                        success = True
                        logger.info(f"Successfully bypassed with {browser} cookies!")
                        break
                    except Exception as browser_e:
                        logger.debug(f"Failed with {browser} cookies: {str(browser_e)}")
                
                if not success:
                    logger.error("Failed to bypass bot detection with local browser cookies.")
                    return jsonify({'error': 'YouTube bot detection blocked the request. Please export a cookies.txt file.'}), 500
            elif is_bot_detection and is_hugging_face:
                logger.error("YouTube bot detection blocked the request on Hugging Face.")
                return jsonify({'error': 'YouTube bot detection blocked the request. Please upload a cookies.txt file to your Hugging Face Space.'}), 500
            elif is_login_required and platform in ('facebook', 'instagram'):
                logger.error(f"{platform} requires authentication. Please provide cookies.txt or enable USE_BROWSER_COOKIES.")
                return jsonify({'error': f'{platform.title()} requires login. Please provide a cookies.txt file with your Facebook/Instagram session cookies, or set USE_BROWSER_COOKIES=1 environment variable.'}), 500
            else:
                logger.error(f"Failed to extract video info: {error_msg}")
                return jsonify({'error': 'Failed to extract video information: ' + error_msg}), 500

        if not info:
            logger.error("Failed to extract video info")
            cobalt_res = extract_with_cobalt(video_url)
            if cobalt_res:
                return jsonify(finalize_download_response(cobalt_res))
            return jsonify({'error': 'Failed to extract video information'}), 500

        format_info = choose_single_file_format(info)
        download_url = format_info.get('url')
        
        if not download_url:
            logger.error("No download URL found in extracted info")
            # Try Cobalt as last resort
            logger.warning("No playable format found, trying Cobalt fallback...")
            cobalt_res = extract_with_cobalt(video_url)
            if cobalt_res:
                logger.info(f"Successfully extracted video using Cobalt fallback: {cobalt_res['title']}")
                return jsonify(finalize_download_response(cobalt_res))
            # Provide helpful error message for Facebook/Instagram
            if platform in ('facebook', 'instagram'):
                return jsonify({'error': 'No playable video format found. For Facebook/Instagram, please provide cookies.txt with your session cookies, or set USE_BROWSER_COOKIES=1'}), 500
            return jsonify({'error': 'No playable video format found'}), 500
            
        # Get video title
        title = info.get('title', f'grab_am_video_{info.get("id", "unknown")}')
        
        # Sanitize title
        title = title.replace('/', '_').replace('\\', '_').replace(':', '_')
        headers = format_info.get('http_headers') or info.get('http_headers') or {}

        logger.info(f"Successfully extracted: {title}")
        
        return jsonify(finalize_download_response({
            'download_url': download_url,
            'title': title,
            'duration': info.get('duration'),
            'thumbnail': info.get('thumbnail'),
            'ext': format_info.get('ext') or info.get('ext') or 'mp4',
            'mime_type': format_info.get('mime_type') or 'video/mp4',
            'headers': headers,
            'platform': platform,
        }))
    
    except Exception as e:
        logger.error(f"Error extracting video: {str(e)}")
        return jsonify({'error': str(e)}), 500

def choose_single_file_format(info):
    """Return the best direct format that yt-dlp can download to a single playable file."""
    # First check if the main info dict itself is a valid direct format
    if info.get('url') and has_audio_and_video(info) and is_direct_progressive_format(info):
        return info

    formats = info.get('formats') or []
    
    # Priority 1: Progressive formats with both audio and video (single file, directly playable)
    progressive_candidates = [
        fmt for fmt in formats
        if fmt.get('url') and has_audio_and_video(fmt) and is_direct_progressive_format(fmt)
    ]

    if progressive_candidates:
        def score(fmt):
            ext = (fmt.get('ext') or '').lower()
            return (
                1 if ext == 'mp4' else 0,
                fmt.get('height') or 0,
                fmt.get('width') or 0,
                fmt.get('tbr') or 0,
                fmt.get('filesize') or fmt.get('filesize_approx') or 0,
            )
        return max(progressive_candidates, key=score)

    # Priority 2: Video-only progressive formats (might work if audio is embedded)
    video_only_candidates = [
        fmt for fmt in formats
        if fmt.get('url')
        and fmt.get('vcodec') not in (None, 'none')
        and is_direct_progressive_format(fmt)
    ]

    if video_only_candidates:
        def score(fmt):
            ext = (fmt.get('ext') or '').lower()
            return (
                1 if ext == 'mp4' else 0,
                fmt.get('height') or 0,
                fmt.get('width') or 0,
                fmt.get('tbr') or 0,
                fmt.get('filesize') or fmt.get('filesize_approx') or 0,
            )
        return max(video_only_candidates, key=score)

    # Fallback: check main info dict
    if info.get('url') and is_direct_progressive_format(info):
        return info

    # Last resort: return any format that has a URL and is progressive
    for fmt in formats:
        if fmt.get('url') and is_direct_progressive_format(fmt):
            return fmt
    if info.get('url') and is_direct_progressive_format(info):
        return info

    return {}

def has_audio_and_video(format_info):
    return (
        format_info.get('vcodec') not in (None, 'none') and
        format_info.get('acodec') not in (None, 'none')
    )

def is_supported_video_url(url):
    """Validate if the URL points to a supported Twitter/X, YouTube, Facebook, or Instagram video."""
    parsed = urlparse(url)
    host = (parsed.hostname or '').lower()
    path_parts = [part for part in parsed.path.split('/') if part]

    if is_twitter_host(host):
        return 'status' in [part.lower() for part in path_parts]

    if host == 'youtu.be' or host.endswith('.youtu.be'):
        return bool(path_parts)

    if is_youtube_host(host):
        first_part = path_parts[0].lower() if path_parts else ''
        if first_part == 'watch':
            return bool(parse_qs(parsed.query).get('v', [''])[0])
        return first_part in {'shorts', 'embed', 'v', 'live'} and len(path_parts) > 1

    if is_facebook_host(host):
        lowered_parts = [part.lower() for part in path_parts]
        if host == 'fb.watch' or host.endswith('.fb.watch'):
            return bool(path_parts)
        if 'videos' in lowered_parts or 'reel' in lowered_parts or 'reels' in lowered_parts:
            return True
        # share-style links, e.g. /share/v/<id>/ or /share/r/<id>/
        if 'share' in lowered_parts:
            return True
        # watch?v=<id>
        if lowered_parts and lowered_parts[0] == 'watch':
            return bool(parse_qs(parsed.query).get('v', [''])[0])
        # video.php?v=<id> and photo.php?v=<id> (common on mobile web)
        if lowered_parts and lowered_parts[0] in ('video.php', 'photo.php'):
            return bool(parse_qs(parsed.query).get('v', [''])[0])
        return False

    if is_instagram_host(host):
        lowered_parts = [part.lower() for part in path_parts]
        return any(part in {'reel', 'reels', 'p', 'tv'} for part in lowered_parts)

    return False

def detect_platform(url):
    host = (urlparse(url).hostname or '').lower()
    if is_twitter_host(host):
        return 'twitter_x'
    if is_youtube_host(host) or host == 'youtu.be' or host.endswith('.youtu.be'):
        return 'youtube'
    if is_facebook_host(host):
        return 'facebook'
    if is_instagram_host(host):
        return 'instagram'
    return 'unknown'

def is_twitter_host(host):
    return host == 'twitter.com' or host.endswith('.twitter.com') or host == 'x.com' or host.endswith('.x.com')

def is_youtube_host(host):
    return (
        host == 'youtube.com' or
        host.endswith('.youtube.com') or
        host == 'youtube-nocookie.com' or
        host.endswith('.youtube-nocookie.com')
    )

def is_facebook_host(host):
    return (
        host == 'facebook.com' or
        host.endswith('.facebook.com') or
        host == 'fb.watch' or
        host.endswith('.fb.watch') or
        host == 'fb.com' or
        host.endswith('.fb.com')
    )

def is_instagram_host(host):
    return host == 'instagram.com' or host.endswith('.instagram.com')

@app.route('/proxy', methods=['GET'])
def proxy_download():
    """
    Proxy the video download stream to bypass IP-locking and apply CDN headers.
    """
    video_url = request.args.get('url')
    if not video_url:
        return jsonify({'error': 'Missing url parameter'}), 400

    headers = build_upstream_headers(video_url, request)

    try:
        upstream = requests.get(video_url, headers=headers, stream=True, timeout=120)

        if upstream.status_code not in (200, 206):
            logger.error(f"Proxy upstream HTTP {upstream.status_code} for {video_url[:120]}")
            return jsonify({'error': f'Upstream returned HTTP {upstream.status_code}'}), 502

        first_chunk = b''
        for chunk in upstream.iter_content(chunk_size=8192):
            if chunk:
                first_chunk = chunk
                break

        if not first_chunk:
            logger.error(f"Proxy upstream returned an empty body for {video_url[:120]}")
            return jsonify({'error': 'Upstream returned an empty video stream'}), 502

        def generate():
            yield first_chunk
            for chunk in upstream.iter_content(chunk_size=8192):
                if chunk:
                    yield chunk

        response = Response(
            generate(),
            status=upstream.status_code,
            content_type=upstream.headers.get('content-type', 'video/mp4'),
            direct_passthrough=True
        )

        for header_name in ['content-length', 'content-disposition', 'accept-ranges', 'content-range']:
            if header_name in upstream.headers:
                response.headers[header_name] = upstream.headers[header_name]

        return response
    except Exception as e:
        logger.error(f"Proxy error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({'status': 'healthy'})

if __name__ == '__main__':
    import os
    # Run the app
    # Use 0.0.0.0 to make it accessible from other devices on the network
    port = int(os.environ.get('PORT', 8000))
    app.run(host='0.0.0.0', port=port, debug=False)
