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

echo "=== Build complete ==="
