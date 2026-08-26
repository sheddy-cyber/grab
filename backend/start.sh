#!/bin/bash

# Start the bgutil PO Token server in the background
echo "Starting bgutil PO Token server on port 4416..."
if [ -f "bgutil-server/build/main.js" ]; then
    node bgutil-server/build/main.js &
    POT_PID=$!
    echo "PO Token server started (PID $POT_PID)"
    # Give it a moment to initialize
    sleep 2
elif [ -f "bgutil-server/main.js" ]; then
    node bgutil-server/main.js &
    POT_PID=$!
    echo "PO Token server started (PID $POT_PID)"
    sleep 2
else
    echo "WARNING: bgutil server not found — YouTube downloads may fail"
fi

# Start gunicorn (use PORT env var from Render, fallback to 7860)
PORT="${PORT:-7860}"
echo "Starting gunicorn on port $PORT..."
exec gunicorn app:app --bind "0.0.0.0:$PORT" --timeout 120 --graceful-timeout 30 --workers 2 --threads 4
