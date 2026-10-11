import os
import tempfile
from pathlib import Path

from flask import current_app
from PIL import Image, ImageOps

from library_api.services.recognition_strategies import (
    BarcodeRecognitionStrategy,
    LmStudioVisionRecognitionStrategy,
    RecognitionStrategy,
)

__all__ = [
    "RecognitionPipeline",
    "recognize_image",
]


class RecognitionPipeline:
    def __init__(self):
        self.strategies = [
            BarcodeRecognitionStrategy(),
            LmStudioVisionRecognitionStrategy(),
        ]

    def register_strategy(self, strategy: RecognitionStrategy):
        self.strategies.append(strategy)

    def recognize(self, uploaded_files, temp_paths):
        primary_results = None

        # Run primary strategies first (authoritative)
        for strategy in self.strategies:
            if strategy.is_primary:
                try:
                    result = strategy.recognize(uploaded_files, temp_paths)
                    if result:
                        primary_results = result
                        break
                except Exception:
                    current_app.logger.exception(
                        f"Primary recognition strategy '{strategy.name}' failed"
                    )

        # Run secondary strategies (to fill gaps)
        secondary_results = None
        for strategy in self.strategies:
            if not strategy.is_primary:
                try:
                    result = strategy.recognize(uploaded_files, temp_paths)
                    if result:
                        secondary_results = result
                        break
                except Exception:
                    current_app.logger.exception(
                        f"Secondary recognition strategy '{strategy.name}' failed"
                    )

        if primary_results:
            if secondary_results and len(secondary_results) > 0:
                sec_book = secondary_results[0]
                for book in primary_results:
                    if not book.get("title") and sec_book.get("title"):
                        book["title"] = sec_book["title"]
                    if not book.get("author") and sec_book.get("author"):
                        book["author"] = sec_book["author"]
                    if not book.get("release_date") and sec_book.get("release_date"):
                        book["release_date"] = sec_book["release_date"]
            return primary_results

        if secondary_results:
            return secondary_results

        raise ValueError("Could not recognize book from images")


def recognize_image(uploaded_files):
    """Return book metadata candidates recognized from uploaded book images using pluggable strategies."""
    if not isinstance(uploaded_files, (list, tuple)):
        uploaded_files = [uploaded_files]
    if not uploaded_files:
        raise ValueError("Upload at least one image")

    upload_folder = current_app.config["UPLOAD_FOLDER"]
    temp_paths = []

    try:
        for uploaded_file in uploaded_files:
            suffix = Path(uploaded_file.filename or "").suffix or ".jpg"
            with tempfile.NamedTemporaryFile(
                dir=upload_folder,
                suffix=suffix,
                delete=False,
            ) as temp_file:
                temp_path = temp_file.name
                temp_paths.append(temp_path)
                uploaded_file.save(temp_file)

            # Transpose based on EXIF orientation so mobile photos are upright
            try:
                with Image.open(temp_path) as img:
                    transposed = ImageOps.exif_transpose(img)
                    if transposed:
                        transposed.save(temp_path)
            except Exception:
                pass

        pipeline = RecognitionPipeline()
        return pipeline.recognize(uploaded_files, temp_paths)

    finally:
        for temp_path in temp_paths:
            if os.path.exists(temp_path):
                os.remove(temp_path)
