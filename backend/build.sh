#!/bin/bash
set -e

echo "=== Installing Python dependencies ==="
pip install -r requirements.txt

echo "=== Setting up bgutil PO Token server for YouTube ==="
if [ ! -d "bgutil-server" ]; then
    git clone --depth 1 https://github.com/Brainicism/bgutil-ytdlp-pot-provider.git bgutil-repo
    mv bgutil-repo/server bgutil-server
    rm -rf bgutil-repo
fi

cd bgutil-server
npm ci --production
npx tsc 2>/dev/null || true  # compile TypeScript; ignore if already JS
cd ..

echo "=== Setting up ffmpeg ==="
if [ ! -f "ffmpeg" ]; then
    # Download John Van Sickle's static ffmpeg build for Linux amd64
    wget https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz -O ffmpeg.tar.xz
    tar -xf ffmpeg.tar.xz
    mv ffmpeg-*-amd64-static/ffmpeg .
    mv ffmpeg-*-amd64-static/ffprobe .
    rm -rf ffmpeg-*-amd64-static ffmpeg.tar.xz
    chmod +x ffmpeg ffprobe
    echo "ffmpeg installed successfully"
fi

echo "=== Build complete ==="
