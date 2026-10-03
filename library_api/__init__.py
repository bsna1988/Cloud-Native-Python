from pathlib import Path

from flask import Flask
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
    )

    if test_config:
        app.config.update(test_config)

    Path(app.config["UPLOAD_FOLDER"]).mkdir(parents=True, exist_ok=True)

    app.register_blueprint(books_bp, url_prefix="/api/v1/books")
    app.register_blueprint(uploads_bp)
    
    CORS(app)

    return app