# Raspberry Pi Deployment & One-Time Setup Guide

This guide covers the one-time provisioning of the Raspberry Pi (`192.168.178.37`) to host the HomeBooks backend and web application as a background service with automated CI/CD deployment.

---

## Prerequisites
- SSH access to the Raspberry Pi:
  ```bash
  ssh pi@192.168.178.37
  ```
- Git, Python 3, and Node.js/npm installed on the Raspberry Pi.

---

## One-Time Setup on Raspberry Pi

### 1. Clone or Navigate to Repository
```bash
cd /home/pi/Cloud-Native-Python
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
sudo cp deploy/homebooks-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable homebooks-backend
sudo systemctl start homebooks-backend
```

Verify service status:
```bash
sudo systemctl status homebooks-backend
```

### 5. Optional: Automated GitHub Webhook Listener
To enable automatic redeployment on every push to GitHub:
```bash
cd backend
../.venv/bin/pip install flask
nohup python3 ../deploy/webhook_server.py > webhook.log 2>&1 &
```

---

## Manual Redeployment
Whenever you want to manually redeploy updates from your Mac/PC:
```bash
bash deploy/deploy.sh
```
