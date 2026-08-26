# Use a lightweight official Python image
FROM python:3.11-slim

# Install system dependencies (ffmpeg, curl, git, Node.js)
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    curl \
    git \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
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

# Install Python dependencies in user space
RUN pip install --no-cache-dir --user -r requirements.txt

# Clone and build the bgutil PO Token server for YouTube bot-detection bypass
RUN git clone --depth 1 https://github.com/Brainicism/bgutil-ytdlp-pot-provider.git bgutil-repo \
    && mv bgutil-repo/server bgutil-server \
    && rm -rf bgutil-repo \
    && cd bgutil-server \
    && npm ci --production \
    && npx tsc 2>/dev/null || true

# Copy the rest of the application files
COPY --chown=user . .

# Copy start script and make it executable
COPY --chown=user start.sh .
RUN chmod +x start.sh

# Expose port (Render uses PORT env var, HF uses 7860)
EXPOSE 7860

# Start PO token server + gunicorn via start.sh
CMD ["bash", "start.sh"]
