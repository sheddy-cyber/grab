#!/bin/bash

# Start the bgutil PO Token server in the background
echo "Starting bgutil PO Token server on port 4416..."
if [ -f "bgutil-server/build/main.js" ]; then
    node --max-old-space-size=64 bgutil-server/build/main.js &
    POT_PID=$!
    echo "PO Token server started (PID $POT_PID). Waiting for it to bind..."
    # Wait until it responds (up to 20 seconds)
    for i in {1..20}; do
        if curl -s -f http://127.0.0.1:4416/ > /dev/null 2>&1 || [ $? -eq 22 ]; then
            # 22 is curl's HTTP page not retrieved (400 Bad Request, which is what / returns)
            echo "PO Token server is fully up and listening!"
            break
        fi
        sleep 1
    done
elif [ -f "bgutil-server/main.js" ]; then
    node --max-old-space-size=64 bgutil-server/main.js &
    POT_PID=$!
    echo "PO Token server started (PID $POT_PID)"
    sleep 5
else
    echo "WARNING: bgutil server not found — YouTube downloads may fail"
fi

# Start gunicorn (use PORT env var from Render, fallback to 7860)
PORT="${PORT:-7860}"
echo "Starting gunicorn on port $PORT..."
# Reduce workers to 1 to prevent OOM kills on Render free tier (512MB RAM limit)
exec gunicorn app:app --bind "0.0.0.0:$PORT" --timeout 3600 --graceful-timeout 30 --workers 1 --threads 4
