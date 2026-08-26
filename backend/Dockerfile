# Use a lightweight official Python image
FROM python:3.11-slim

# Install system dependencies (ffmpeg, curl, git)
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

# Install Node.js 22 (bgutil server requires >= 22)
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# Set up a new user "user" with UID 1000
RUN useradd -m -u 1000 user

# Switch to the new user
USER user

# Set home and path environment variables
ENV HOME=/home/user \
    PATH=/home/user/.local/bin:$PATH

# Set the working directory
WORKDIR $HOME/app

# Copy dependency file first for caching
COPY --chown=user requirements.txt .

# Install Python dependencies in user space
RUN pip install --no-cache-dir --user -r requirements.txt

# Clone and build the bgutil PO Token server for YouTube bot-detection bypass
# Step 1: Clone repo and keep only the server directory
RUN git clone --depth 1 https://github.com/Brainicism/bgutil-ytdlp-pot-provider.git bgutil-repo \
    && mv bgutil-repo/server bgutil-server \
    && rm -rf bgutil-repo

# Step 2: Install ALL deps (including devDependencies for TypeScript compiler)
RUN cd bgutil-server && npm ci --no-audit --no-fund

# Step 3: Compile TypeScript to JavaScript
RUN cd bgutil-server && npx tsc

# Step 4: Verify the build output exists
RUN ls -la bgutil-server/build/main.js

# Copy the rest of the application files
COPY --chown=user . .

# Make start script executable
RUN chmod +x start.sh

# Expose port
EXPOSE 7860

# Start PO token server + gunicorn via start.sh
CMD ["bash", "start.sh"]
