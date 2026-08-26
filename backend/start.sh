#!/bin/bash

# Start the bgutil PO Token server in the background (warmed up for instant responses)
echo "Starting bgutil PO Token server on port 4416..."
if [ -f "bgutil-server/build/main.js" ]; then
    # Restrict memory to 64MB so it doesn't trigger Render OOM kills alongside Gunicorn
    node --max-old-space-size=64 bgutil-server/build/main.js &
    POT_PID=$!
    echo "PO Token server started (PID $POT_PID). Waiting for it to bind..."
    # Render's free tier CPU is extremely slow at JIT-compiling jsdom. Wait up to 60 seconds!
    pot_ready=false
    for i in {1..60}; do
        if curl -s -f http://127.0.0.1:4416/ > /dev/null 2>&1 || [ $? -eq 22 ]; then
            echo "PO Token server is fully up and listening after $i seconds!"
            pot_ready=true
            break
        fi
        sleep 1
    done
    if [ "$pot_ready" = false ]; then
        echo "WARNING: PO Token server failed to bind after 60 seconds. YouTube downloads will likely fail."
    fi
else
    echo "WARNING: bgutil server not found — YouTube downloads may fail"
fi

# Start gunicorn (use PORT env var from Render, fallback to 7860)
PORT="${PORT:-7860}"
echo "Starting gunicorn on port $PORT..."
# Reduce workers to 1 to prevent OOM kills on Render free tier (512MB RAM limit)
exec gunicorn app:app --bind "0.0.0.0:$PORT" --timeout 3600 --graceful-timeout 30 --workers 1 --threads 4
