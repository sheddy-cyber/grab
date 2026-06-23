# Use a lightweight official Python image
FROM python:3.11-slim

# Install system dependencies (like ffmpeg, curl)
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Set up a new user "user" with UID 1000
RUN useradd -m -u 1000 user

# Switch to the new user
USER user

# Set home and path environment variables
ENV HOME=/home/user \
    PATH=/home/user/.local/bin:$PATH

# Set the working directory to the user's home directory
WORKDIR $HOME/app

# Copy dependency file first for caching
COPY --chown=user requirements.txt .

# Install dependencies in user space
RUN pip install --no-cache-dir --user -r requirements.txt

# Copy the rest of the application files
COPY --chown=user . .

# Hugging Face runs on port 7860 by default
EXPOSE 7860

# Command to run the application using gunicorn
CMD ["gunicorn", "--bind", "0.0.0.0:7860", "app:app"]
