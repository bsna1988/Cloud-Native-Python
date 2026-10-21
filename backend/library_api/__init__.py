import os
from pathlib import Path

from flask import Flask, send_from_directory
from flask_cors import CORS

from library_api.routes.books import books_bp
from library_api.routes.uploads import uploads_bp


def create_app(test_config=None):
    project_root = Path(__file__).resolve().parent.parent

    app = Flask(__name__)
    app.config.from_mapping(
        DATABASE=str(project_root / "books.db"),
        UPLOAD_FOLDER=str(project_root / "uploads"),
        MAX_CONTENT_LENGTH=16 * 1024 * 1024,
        OPENROUTER_API_KEY=os.environ.get("OPENROUTER_API_KEY", ""),
        OPENROUTER_MODEL=os.environ.get("OPENROUTER_MODEL", ""),
        API_KEY="homebooks_secret_key_12345",
    )

    if test_config:
        app.config.update(test_config)

    Path(app.config["UPLOAD_FOLDER"]).mkdir(parents=True, exist_ok=True)

    app.register_blueprint(books_bp, url_prefix="/api/v1/books")
    app.register_blueprint(uploads_bp)

    @app.route("/", defaults={"path": ""})
    @app.route("/<path:path>")
    def serve_frontend(path):
        dist_dir = project_root.parent / "frontend" / "dist"
        if path and (dist_dir / path).exists():
            return send_from_directory(str(dist_dir), path)
        if (dist_dir / "index.html").exists():
            return send_from_directory(str(dist_dir), "index.html")
        return "Frontend build not found. Run 'npm run build' in frontend directory.", 404

    CORS(app)

    return app
