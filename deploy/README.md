# Raspberry Pi Deployment & One-Time Setup Guide

This guide covers the one-time provisioning of the Raspberry Pi (`192.168.178.37`) to host the HomeBooks backend and web application as a background service with automated CI/CD deployment.

---

## Prerequisites
- SSH access to the Raspberry Pi:
  ```bash
  ssh bsna1988@192.168.178.37
  ```
- Git, Python 3, and Node.js/npm installed on the Raspberry Pi.

---

## One-Time Setup on Raspberry Pi

### 1. Clone or Navigate to Repository
```bash
cd /home/bsna1988/Cloud-Native-Python
git pull origin main
```

### 2. Set Up Python Virtual Environment & Backend Dependencies
```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
pip install gunicorn
deactivate
cd ..
```

### 3. Build the React Frontend (Vite)
```bash
cd frontend
npm install
npm run build
cd ..
```

### 4. Install and Start the Systemd Backend Service
This ensures Flask (which serves both the API and built frontend on port `5005`) runs in the background and restarts automatically on system boot:
```bash
# Configure OpenRouter credentials outside the repository. Keep this file
# readable only by root; systemd reads it before dropping privileges to the
# service user.
sudo install -d -m 700 /etc/homebooks
sudo install -m 600 /dev/null /etc/homebooks/homebooks-backend.env
sudoedit /etc/homebooks/homebooks-backend.env
# Add:
# OPENROUTER_API_KEY=your_openrouter_api_key
# OPENROUTER_MODEL=nvidia/nemotron-3.5-super-vl:free

sudo cp deploy/homebooks-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable homebooks-backend
sudo systemctl start homebooks-backend
```

The key is used only by the backend and must not be committed or added to the
Android or frontend application. The default model is a free-tier vision model;
free model IDs, availability, and rate limits can change. Confirm the
configured OpenRouter model is currently free and supports image input before
changing `OPENROUTER_MODEL`. Recognition sends uploaded book-cover images to
OpenRouter for processing.

The provided Gunicorn service runs one worker with three threads
(`--workers 1 --worker-class gthread --threads 3`). A worker is a separate
operating system process; its threads handle requests concurrently within it.
Metadata requests from those threads enter a bounded in-process FIFO queue and
a single dispatcher sends them at most three times per second, evenly spaced.
The `limits` quota additionally allows each originating thread at most one
provider call per second. These limits apply to outbound calls to Google Books
and OpenLibrary, not to incoming requests to the HomeBooks API. Increasing the
worker count creates another independent queue and dispatcher, so it can
increase the aggregate provider request rate.

The queue has a fixed maximum size; if it is full, lookups fail rather than
accumulating unbounded work. Requests wait synchronously for their lookup
result, and pending queued requests are not durable across a backend restart.

The worker and thread counts are configured in
`deploy/homebooks-backend.service`, in Gunicorn's `ExecStart` arguments. The
rate limit and retry policy are configured in
`backend/library_api/services/book_metadata.py`.

Verify service status:
```bash
sudo systemctl status homebooks-backend
```

### 5. Optional: Automated GitHub Webhook Listener
To enable automatic redeployment on every push to GitHub:
```bash
cd /home/bsna1988/Cloud-Native-Python
backend/.venv/bin/pip install flask
nohup python3 deploy/webhook_server.py > webhook.log 2>&1 &
```

---

## Manual Redeployment
Whenever you want to manually redeploy updates from your Mac/PC:
```bash
bash deploy/deploy.sh
```
