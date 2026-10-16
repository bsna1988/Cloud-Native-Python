#!/bin/bash
set -e

REPO_DIR="/home/pi/Cloud-Native-Python"
cd "$REPO_DIR"

echo "Pulling latest changes from GitHub..."
git pull origin main

echo "Building frontend with Vite..."
cd frontend
npm install
npm run build

echo "Updating backend dependencies..."
cd ../backend
pip install -r requirements.txt

echo "Restarting backend service..."
sudo systemctl restart homebooks-backend

echo "Deployment completed successfully!"
