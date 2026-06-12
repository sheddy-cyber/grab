from flask import Flask, request, jsonify
from flask_cors import CORS
import yt_dlp
import logging

app = Flask(__name__)
CORS(app)  # Enable CORS for all routes

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

@app.route('/extract', methods=['GET'])
def extract_video():
    """
    Extract video URL from Twitter/X tweet URL
    
    Query Parameters:
        url: The Twitter/X video URL
    
    Returns:
        JSON with 'download_url' and 'title'
    """
    try:
        video_url = request.args.get('url')
        
        if not video_url:
            logger.error("No URL provided")
            return jsonify({'error': 'No URL provided'}), 400
        
        # Validate Twitter/X URL
        if not is_valid_twitter_url(video_url):
            logger.error(f"Invalid Twitter URL: {video_url}")
            return jsonify({'error': 'Invalid Twitter/X URL'}), 400
        
        logger.info(f"Extracting video from: {video_url}")
        
        # Configure yt-dlp options
        ydl_opts = {
            'format': 'best',  # Download best quality
            'quiet': True,
            'no_warnings': True,
            'extract_flat': False,
            'cookiefile': None,  # Path to cookies file if needed for authentication
        }
        
        # Extract video info
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(video_url, download=False)
            
            if not info:
                logger.error("Failed to extract video info")
                return jsonify({'error': 'Failed to extract video information'}), 500
            
            # Get the direct download URL
            download_url = info.get('url')
            
            if not download_url:
                # Try to get URL from formats
                formats = info.get('formats', [])
                if formats:
                    # Get the best format
                    download_url = formats[0].get('url')
            
            if not download_url:
                logger.error("No download URL found in extracted info")
                return jsonify({'error': 'No download URL found'}), 500
            
            # Get video title
            title = info.get('title', f'twitter_video_{info.get("id", "unknown")}')
            
            # Sanitize title
            title = title.replace('/', '_').replace('\\', '_').replace(':', '_')
            
            logger.info(f"Successfully extracted: {title}")
            
            return jsonify({
                'download_url': download_url,
                'title': title,
                'duration': info.get('duration'),
                'thumbnail': info.get('thumbnail')
            })
    
    except Exception as e:
        logger.error(f"Error extracting video: {str(e)}")
        return jsonify({'error': str(e)}), 500

def is_valid_twitter_url(url):
    """Validate if the URL is a valid Twitter/X URL"""
    valid_domains = [
        'twitter.com',
        'www.twitter.com',
        'x.com',
        'www.x.com',
        'mobile.twitter.com',
        'm.twitter.com'
    ]
    
    for domain in valid_domains:
        if domain in url.lower():
            return True
    return False

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({'status': 'healthy'})

if __name__ == '__main__':
    # Run the app
    # Use 0.0.0.0 to make it accessible from other devices on the network
    app.run(host='0.0.0.0', port=8000, debug=True)
