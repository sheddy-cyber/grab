# Use a lightweight official Python image
FROM python:3.11-slim

# Install system dependencies (like ffmpeg, curl)
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Set the working directory inside the container
WORKDIR /app

# Copy dependency file first for caching
COPY requirements.txt .

# Install dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy the rest of the application files
COPY . .

# Hugging Face runs on port 7860 by default
EXPOSE 7860

# Command to run the application using gunicorn
CMD ["gunicorn", "--bind", "0.0.0.0:7860", "app:app"]
