#!/bin/bash

echo "Starting Clippd Backend Service..."
echo "Installing dependencies..."
pip install -r requirements.txt

echo "Starting Flask server..."
python app.py
