import base64
import json
import mimetypes
from pathlib import Path

import requests
from flask import current_app

from library_api.services.book_metadata import query_book_metadata
from library_api.services.recognition_strategies.base import RecognitionStrategy


class LmStudioVisionRecognitionStrategy(RecognitionStrategy):
    name = "lm_studio_vision"
    is_primary = False

    def recognize(self, uploaded_files, temp_paths):
        content = [
            {
                "type": "text",
                "text": (
                    "Read the book cover images. Return only a JSON object with "
                    '"title" and "author" string fields. Use empty strings for '
                    "unreadable fields; do not guess."
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

        base_url = current_app.config["LM_STUDIO_BASE_URL"].rstrip("/")
        response = requests.post(
            f"{base_url}/chat/completions",
            json={
                "model": current_app.config["LM_STUDIO_MODEL"],
                "messages": [{"role": "user", "content": content}],
                "temperature": 0,
                "max_tokens": 200,
            },
            timeout=120,
        )
        response.raise_for_status()

        response_content = response.json()["choices"][0]["message"]["content"]
        try:
            book_data = json.loads(response_content)
        except json.JSONDecodeError:
            json_start = response_content.find("{")
            json_end = response_content.rfind("}")
            if json_start < 0 or json_end <= json_start:
                raise ValueError("LM Studio returned invalid book metadata JSON")
            book_data = json.loads(response_content[json_start:json_end + 1])

        if not isinstance(book_data, dict):
            raise ValueError("LM Studio book metadata must be a JSON object")

        title = book_data.get("title", "")
        author = book_data.get("author", "")
        if not isinstance(title, str) or not isinstance(author, str):
            raise ValueError("LM Studio title and author must be strings")

        title = title.strip()
        author = author.strip()
        if not title and not author:
            return None

        try:
            metadata = query_book_metadata(title, author=author)
            if metadata:
                return metadata
        except requests.RequestException:
            current_app.logger.exception(
                "OpenLibrary lookup for LM Studio result failed"
            )

        return [{"title": title, "author": author, "barcode": ""}]
