import subprocess
from flask import Flask, jsonify, request

app = Flask(__name__)
DEPLOY_SCRIPT = "/home/bsna1988/Cloud-Native-Python/deploy/deploy.sh"


@app.post("/webhook")
def github_webhook():
    event = request.headers.get("X-GitHub-Event")
    if event == "push":
        try:
            subprocess.Popen(["bash", DEPLOY_SCRIPT])
            return jsonify({"status": "deploying"}), 200
        except Exception as e:
            return jsonify({"error": str(e)}), 500
    return jsonify({"status": "ignored"}), 200


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=9000)
