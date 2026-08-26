#!/bin/bash

# Start gunicorn (use PORT env var from Render, fallback to 7860)
PORT="${PORT:-7860}"
echo "Starting gunicorn on port $PORT..."
# Reduce workers to 1 to prevent OOM kills on Render free tier (512MB RAM limit)
exec gunicorn app:app --bind "0.0.0.0:$PORT" --timeout 3600 --graceful-timeout 30 --workers 1 --threads 4
