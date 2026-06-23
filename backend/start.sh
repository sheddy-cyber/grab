#!/bin/bash

echo "Starting grab am Backend Service..."
echo "Installing dependencies..."
pip install -r requirements.txt

echo "Starting Flask server..."
python app.py
