import base64
import json
import mimetypes
from pathlib import Path
import time

import requests
from flask import current_app

from library_api.services.book_metadata import query_book_metadata
from library_api.services.recognition_strategies.base import (
    RecognitionProviderError,
    RecognitionStrategy,
)


OPENROUTER_CHAT_COMPLETIONS_URL = (
    "https://openrouter.ai/api/v1/chat/completions"
)
OPENROUTER_FREE_ROUTER_MODEL = "openrouter/free"
MAX_REQUEST_ATTEMPTS = 2


def validate_free_model(model):
    if not isinstance(model, str) or not model:
        raise RecognitionProviderError(
            "Image recognition is unavailable: OPENROUTER_MODEL is not configured."
        )
    if model != OPENROUTER_FREE_ROUTER_MODEL and not model.endswith(":free"):
        raise RecognitionProviderError(
            "Image recognition requires 'openrouter/free' or a model ID ending in ':free'."
        )


class OpenRouterVisionRecognitionStrategy(RecognitionStrategy):
    name = "openrouter_vision"
    is_primary = False

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
                    "Identify the book shown on the cover. Read text from the image "
                    "rather than inferring from visual style. Return an object with "
                    '"title" and "author" string fields. Copy the title and author '
                    "as printed; use an empty string if either cannot be read. Do not "
                    "guess, invent details, or include markdown."
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

        response = self._request(
            api_key,
            model,
            content,
            response_format={"type": "json_object"},
        )
        response_content = self._extract_response_content(response)
        book_data = self._parse_book_data(response_content)

        title = book_data.get("title", "")
        author = book_data.get("author", "")
        if not isinstance(title, str) or not isinstance(author, str):
            raise RecognitionProviderError(
                "OpenRouter returned invalid book metadata: title and author must be strings."
            )

        title = title.strip()
        author = author.strip()
        if not title and not author:
            return None

        if title:
            try:
                metadata = query_book_metadata(title, author=author)
                if metadata:
                    return metadata
            except requests.RequestException:
                current_app.logger.exception(
                    "OpenLibrary lookup for OpenRouter recognition result failed"
                )

        return [{"title": title, "author": author, "barcode": ""}]

    @staticmethod
    def _request(api_key, model, content, response_format=None):
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": model,
            "messages": [{"role": "user", "content": content}],
            "temperature": 0,
            "max_tokens": 200,
        }
        if response_format is not None:
            payload["response_format"] = response_format

        for attempt in range(MAX_REQUEST_ATTEMPTS):
            try:
                response = requests.post(
                    OPENROUTER_CHAT_COMPLETIONS_URL,
                    headers=headers,
                    json=payload,
                    timeout=90,
                )
            except requests.RequestException as error:
                if attempt + 1 < MAX_REQUEST_ATTEMPTS:
                    time.sleep(1)
                    continue
                raise RecognitionProviderError(
                    "OpenRouter image recognition could not reach the provider."
                ) from error

            if response.status_code == 429 or response.status_code >= 500:
                if attempt + 1 < MAX_REQUEST_ATTEMPTS:
                    time.sleep(1)
                    continue
                if response.status_code == 429:
                    raise RecognitionProviderError(
                        "OpenRouter's free vision-model rate limit was reached. Try again later."
                    )
                raise RecognitionProviderError(
                    f"OpenRouter image recognition is temporarily unavailable "
                    f"(HTTP {response.status_code})."
                )

            if not response.ok:
                raise RecognitionProviderError(
                    f"OpenRouter rejected the image recognition request "
                    f"(HTTP {response.status_code}). Check the API key, model ID, "
                    "and vision-model availability."
                )
            return response

        raise RecognitionProviderError(
            "OpenRouter image recognition failed after retrying."
        )

    @staticmethod
    def _extract_response_content(response):
        try:
            response_data = response.json()
            choice = response_data["choices"][0]
            message = choice["message"]
            content = message["content"]
        except (IndexError, KeyError, TypeError, ValueError) as error:
            raise RecognitionProviderError(
                "OpenRouter returned an invalid chat-completion response."
            ) from error

        if isinstance(content, str):
            return content

        if isinstance(content, list):
            text_parts = [
                part["text"]
                for part in content
                if isinstance(part, dict)
                and part.get("type") == "text"
                and isinstance(part.get("text"), str)
            ]
            if text_parts:
                return "\n".join(text_parts)

        if content is None:
            details = []
            routed_model = response_data.get("model")
            finish_reason = choice.get("finish_reason")
            if routed_model:
                details.append(f"routed model: {routed_model}")
            if finish_reason:
                details.append(f"finish reason: {finish_reason}")
            diagnostic = f" ({'; '.join(details)})" if details else ""
            raise RecognitionProviderError(
                "OpenRouter returned no text for book metadata. "
                "Check the selected model's response format"
                f"{diagnostic}."
            )
        raise RecognitionProviderError(
            "OpenRouter returned book metadata in an unsupported content format."
        )

    @staticmethod
    def _parse_book_data(response_content):
        try:
            book_data = json.loads(response_content)
        except json.JSONDecodeError as error:
            raise RecognitionProviderError(
                "OpenRouter returned malformed book metadata JSON."
            ) from error

        if not isinstance(book_data, dict):
            raise RecognitionProviderError(
                "OpenRouter book metadata must be a JSON object."
            )
        return book_data
