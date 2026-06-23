from flask import Flask, request, jsonify
from flask_cors import CORS
import yt_dlp
import logging
from urllib.parse import urlparse, parse_qs

app = Flask(__name__)
CORS(app)  # Enable CORS for all routes

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

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
            return jsonify({'error': 'Unsupported URL. Use a Twitter/X or YouTube video URL.'}), 400
        
        logger.info(f"Extracting video from: {video_url}")
        
        # Configure yt-dlp options
        ydl_opts = {
            # The Android client downloads one direct URL, so prefer progressive formats
            # that already include both video and audio.
            'format': (
                'best[ext=mp4][vcodec!=none][acodec!=none]/'
                'best[vcodec!=none][acodec!=none]/best'
            ),
            'quiet': True,
            'no_warnings': True,
            'extract_flat': False,
            'noplaylist': True,
            'cachedir': False,
        }
        
        # Extract video info
        info = None
        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(video_url, download=False)
        except Exception as e:
            error_msg = str(e)
            if "Sign in to confirm you" in error_msg or "Sign in to confirm your" in error_msg or "bot" in error_msg.lower():
                logger.warning("YouTube bot detection triggered. Attempting to bypass using local browser cookies...")
                
                # List of popular browsers to try for cookie extraction
                browsers = ['chrome', 'edge', 'firefox', 'brave']
                success = False
                
                for browser in browsers:
                    logger.info(f"Trying cookies from: {browser}")
                    ydl_opts['cookiesfrombrowser'] = (browser, )
                    try:
                        with yt_dlp.YoutubeDL(ydl_opts) as ydl_cookie:
                            info = ydl_cookie.extract_info(video_url, download=False)
                            success = True
                            logger.info(f"Successfully bypassed with {browser} cookies!")
                            break
                    except Exception as browser_e:
                        logger.debug(f"Failed with {browser} cookies: {str(browser_e)}")
                
                if not success:
                    logger.error("Failed to bypass bot detection with local browser cookies.")
                    return jsonify({'error': 'YouTube bot detection blocked the request. Please export a cookies.txt file or use the app directly.'}), 500
            else:
                logger.error("Failed to extract video info")
                return jsonify({'error': 'Failed to extract video information: ' + error_msg}), 500
            
        if not info:
            logger.error("Failed to extract video info")
            return jsonify({'error': 'Failed to extract video information'}), 500

        format_info = choose_single_file_format(info)
        download_url = format_info.get('url')
        
        if not download_url:
            logger.error("No download URL found in extracted info")
            return jsonify({'error': 'No download URL found'}), 500
            
        # Get video title
        title = info.get('title', f'grab_am_video_{info.get("id", "unknown")}')
        
        # Sanitize title
        title = title.replace('/', '_').replace('\\', '_').replace(':', '_')
        headers = format_info.get('http_headers') or info.get('http_headers') or {}
        
        logger.info(f"Successfully extracted: {title}")
        
        return jsonify({
            'download_url': download_url,
            'title': title,
            'duration': info.get('duration'),
            'thumbnail': info.get('thumbnail'),
            'ext': format_info.get('ext') or info.get('ext') or 'mp4',
            'mime_type': format_info.get('mime_type') or 'video/mp4',
            'headers': headers,
            'platform': detect_platform(video_url),
        })
    
    except Exception as e:
        logger.error(f"Error extracting video: {str(e)}")
        return jsonify({'error': str(e)}), 500

def choose_single_file_format(info):
    """Return the best direct format that includes both audio and video."""
    if info.get('url') and has_audio_and_video(info):
        return info

    formats = info.get('formats') or []
    candidates = [
        fmt for fmt in formats
        if fmt.get('url') and has_audio_and_video(fmt)
    ]

    if not candidates:
        if info.get('url'):
            return info
        return {}

    mp4_candidates = [fmt for fmt in candidates if fmt.get('ext') == 'mp4']
    preferred = mp4_candidates or candidates

    def score(fmt):
        return (
            fmt.get('height') or 0,
            fmt.get('width') or 0,
            fmt.get('tbr') or 0,
            fmt.get('filesize') or fmt.get('filesize_approx') or 0,
        )

    return max(preferred, key=score)

def has_audio_and_video(format_info):
    return (
        format_info.get('vcodec') not in (None, 'none') and
        format_info.get('acodec') not in (None, 'none')
    )

def is_supported_video_url(url):
    """Validate if the URL points to a supported Twitter/X or YouTube video."""
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

    return False

def detect_platform(url):
    host = (urlparse(url).hostname or '').lower()
    if is_twitter_host(host):
        return 'twitter_x'
    if is_youtube_host(host) or host == 'youtu.be' or host.endswith('.youtu.be'):
        return 'youtube'
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
