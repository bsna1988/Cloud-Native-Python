import base64
import json
import os
import mimetypes
import tempfile
from pathlib import Path

from flask import current_app
from PIL import Image, ImageOps

from library_api.services.book_metadata import query_book_metadata
from library_api.services.recognition_strategies import (
    BarcodeRecognitionStrategy,
    OpenRouterVisionRecognitionStrategy,
    RecognitionStrategy,
)
from library_api.services.recognition_strategies.openrouter import validate_free_model
from library_api.services.recognition_strategies.base import (
    RecognitionProviderError,
)

__all__ = [
    "RecognitionPipeline",
    "recognize_image",
    "recognize_shelf_image",
    "OpenRouterShelfRecognitionStrategy",
]


class RecognitionPipeline:
    def __init__(self):
        self.strategies = [
            BarcodeRecognitionStrategy(),
            OpenRouterVisionRecognitionStrategy(),
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
        provider_error = None
        needs_secondary = not primary_results or any(
            not book.get("title") or not book.get("author")
            for book in primary_results
        )
        if needs_secondary:
            for strategy in self.strategies:
                if not strategy.is_primary:
                    try:
                        result = strategy.recognize(uploaded_files, temp_paths)
                        if result:
                            secondary_results = result
                            break
                    except RecognitionProviderError as error:
                        provider_error = error
                        current_app.logger.warning(
                            "Secondary recognition strategy '%s' unavailable: %s",
                            strategy.name,
                            error,
                        )
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

        if provider_error:
            raise provider_error

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


class OpenRouterShelfRecognitionStrategy:
    name = "openrouter_shelf"

    def recognize(self, uploaded_files, temp_paths):
        api_key = current_app.config["OPENROUTER_API_KEY"]
        if not api_key:
            raise RecognitionProviderError(
                "Image recognition is unavailable: OPENROUTER_API_KEY is not configured."
            )

        model = current_app.config["OPENROUTER_MODEL"]
        validate_free_model(model)

        content = [
            {
                "type": "text",
                "text": (
                    "Inspect the bookshelf image and identify each distinct book whose "
                    "cover is visible. Return one JSON object with a 'books' array; each "
                    "item must have 'title' and 'author' string fields. Transcribe only "
                    "text that is readable on the covers. Use empty strings for unreadable "
                    "authors, omit objects whose titles cannot be read, and do not guess "
                    "or invent books. Do not include markdown."
                ),
            }
        ]
        for temp_path in temp_paths:
            mime_type = mimetypes.guess_type(temp_path)[0] or "image/jpeg"
            encoded_image = base64.b64encode(
                Path(temp_path).read_bytes()
            ).decode("ascii")
            content.append({
                "type": "image_url",
                "image_url": {
                    "url": f"data:{mime_type};base64,{encoded_image}",
                },
            })

        response = OpenRouterVisionRecognitionStrategy._request(
            api_key,
            model,
            content,
            response_format={"type": "json_object"},
        )
        response_content = OpenRouterVisionRecognitionStrategy._extract_response_content(response)
        candidates = self._parse_shelf_data(response_content)

        enriched_books = []
        for candidate in candidates:
            if not isinstance(candidate, dict):
                continue
            title = candidate.get("title", "")
            author = candidate.get("author", "")
            if not isinstance(title, str) or not isinstance(author, str):
                continue
            title = title.strip()
            author = author.strip()
            if not title:
                continue
            if len(title) > 300:
                title = title[:300]
            if len(author) > 300:
                author = author[:300]

            book_entry = {
                "title": title,
                "author": author,
                "barcode": "",
                "release_date": "",
                "description": "",
                "publisher": "",
            }

            try:
                metadata_list = query_book_metadata(title=title, author=author if author else None)
                if metadata_list:
                    best_meta = metadata_list[0]
                    book_entry["title"] = best_meta.get("title") or title
                    book_entry["author"] = best_meta.get("author") or author
                    book_entry["barcode"] = best_meta.get("barcode", "")
                    book_entry["release_date"] = best_meta.get("release_date", "")
                    book_entry["description"] = best_meta.get("description", "")
                    book_entry["publisher"] = best_meta.get("publisher", "")
            except Exception:
                current_app.logger.exception(
                    "Metadata lookup for bookshelf candidate failed"
                )

            enriched_books.append(book_entry)

        return enriched_books

    @staticmethod
    def _parse_shelf_data(response_content):
        try:
            data = json.loads(response_content)
        except json.JSONDecodeError as error:
            raise RecognitionProviderError(
                "OpenRouter returned malformed bookshelf metadata JSON."
            ) from error

        if isinstance(data, list):
            candidates = data
        elif isinstance(data, dict):
            candidates = data.get("books") or data.get("candidates") or data.get("items") or []
            if not isinstance(candidates, list):
                raise RecognitionProviderError(
                    "OpenRouter bookshelf metadata must contain a JSON list."
                )
        else:
            raise RecognitionProviderError(
                "OpenRouter bookshelf metadata must be a JSON list or object."
            )

        if len(candidates) > 50:
            candidates = candidates[:50]

        return candidates


def recognize_shelf_image(uploaded_files):
    """Return multiple book metadata candidates recognized from uploaded bookshelf images."""
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

            try:
                with Image.open(temp_path) as img:
                    transposed = ImageOps.exif_transpose(img)
                    if transposed:
                        transposed.save(temp_path)
            except Exception:
                pass

        strategy = OpenRouterShelfRecognitionStrategy()
        return strategy.recognize(uploaded_files, temp_paths)

    finally:
        for temp_path in temp_paths:
            if os.path.exists(temp_path):
                os.remove(temp_path)
