import base64
import os
import tempfile
import unittest
from io import BytesIO
from pathlib import Path
from unittest.mock import Mock, patch

from library_api import create_app
from library_api.services.recognition import RecognitionPipeline
from library_api.services.recognition_strategies.base import (
    RecognitionProviderError,
)
from library_api.services.recognition_strategies.openrouter import (
    OPENROUTER_CHAT_COMPLETIONS_URL,
    OpenRouterVisionRecognitionStrategy,
)


class OpenRouterRecognitionTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        root = Path(self.temp_dir.name)
        self.app = create_app({
            "DATABASE": str(root / "books.db"),
            "UPLOAD_FOLDER": str(root / "uploads"),
            "API_KEY": "test-app-key",
            "OPENROUTER_API_KEY": "test-openrouter-key",
            "OPENROUTER_MODEL": "nvidia/nemotron-3.5-super-vl:free",
        })
        self.image_path = root / "cover.jpg"
        self.image_path.write_bytes(b"test image")

    @staticmethod
    def make_response(content, status_code=200):
        response = Mock()
        response.status_code = status_code
        response.ok = 200 <= status_code < 400
        response.json.return_value = {
            "choices": [{"message": {"content": content}}],
        }
        return response

    def test_sends_image_to_openrouter_and_returns_book_candidate(self):
        response = self.make_response(
            '{"title": "Test Book", "author": "A. Writer"}'
        )

        with (
            self.app.app_context(),
            patch(
                "library_api.services.recognition_strategies.openrouter.requests.post",
                return_value=response,
            ) as post,
            patch(
                "library_api.services.recognition_strategies.openrouter.query_book_metadata",
                return_value=[],
            ),
        ):
            result = OpenRouterVisionRecognitionStrategy().recognize(
                [], [str(self.image_path)]
            )

        self.assertEqual(
            result,
            [{
                "title": "Test Book",
                "author": "A. Writer",
                "barcode": "",
            }],
        )
        args, kwargs = post.call_args
        self.assertEqual(args[0], OPENROUTER_CHAT_COMPLETIONS_URL)
        self.assertEqual(
            kwargs["headers"]["Authorization"],
            "Bearer test-openrouter-key",
        )
        self.assertEqual(
            kwargs["json"]["model"],
            "nvidia/nemotron-3.5-super-vl:free",
        )
        image_content = kwargs["json"]["messages"][0]["content"][1]
        self.assertEqual(
            image_content["image_url"]["url"],
            "data:image/jpeg;base64,"
            + base64.b64encode(b"test image").decode("ascii"),
        )

    def test_requires_api_key_without_sending_request(self):
        self.app.config["OPENROUTER_API_KEY"] = ""

        with (
            self.app.app_context(),
            patch(
                "library_api.services.recognition_strategies.openrouter.requests.post"
            ) as post,
            self.assertRaisesRegex(
                RecognitionProviderError,
                "OPENROUTER_API_KEY is not configured",
            ),
        ):
            OpenRouterVisionRecognitionStrategy().recognize(
                [], [str(self.image_path)]
            )

        post.assert_not_called()

    def test_refuses_non_free_model_configuration(self):
        self.app.config["OPENROUTER_MODEL"] = "provider/vision-paid"

        with (
            self.app.app_context(),
            patch(
                "library_api.services.recognition_strategies.openrouter.requests.post"
            ) as post,
            self.assertRaisesRegex(RecognitionProviderError, "ending in ':free'"),
        ):
            OpenRouterVisionRecognitionStrategy().recognize(
                [], [str(self.image_path)]
            )

        post.assert_not_called()

    def test_retries_rate_limit_once_and_surfaces_final_limit(self):
        rate_limited = self.make_response("", status_code=429)

        with (
            self.app.app_context(),
            patch(
                "library_api.services.recognition_strategies.openrouter.requests.post",
                return_value=rate_limited,
            ) as post,
            patch("library_api.services.recognition_strategies.openrouter.time.sleep"),
            self.assertRaisesRegex(
                RecognitionProviderError,
                "rate limit was reached",
            ),
        ):
            OpenRouterVisionRecognitionStrategy().recognize(
                [], [str(self.image_path)]
            )

        self.assertEqual(post.call_count, 2)

    def test_rejects_malformed_model_json(self):
        response = self.make_response("not json")

        with (
            self.app.app_context(),
            patch(
                "library_api.services.recognition_strategies.openrouter.requests.post",
                return_value=response,
            ),
            self.assertRaisesRegex(
                RecognitionProviderError,
                "malformed book metadata JSON",
            ),
        ):
            OpenRouterVisionRecognitionStrategy().recognize(
                [], [str(self.image_path)]
            )

    def test_does_not_call_catalog_for_author_only_result(self):
        response = self.make_response('{"title": "", "author": "A. Writer"}')

        with (
            self.app.app_context(),
            patch(
                "library_api.services.recognition_strategies.openrouter.requests.post",
                return_value=response,
            ),
            patch(
                "library_api.services.recognition_strategies.openrouter.query_book_metadata"
            ) as lookup,
        ):
            result = OpenRouterVisionRecognitionStrategy().recognize(
                [], [str(self.image_path)]
            )

        self.assertEqual(
            result,
            [{"title": "", "author": "A. Writer", "barcode": ""}],
        )
        lookup.assert_not_called()

    def test_recognition_provider_errors_are_returned_as_service_unavailable(self):
        with patch(
            "library_api.routes.books.recognize_image",
            side_effect=RecognitionProviderError("OpenRouter rate limit reached."),
        ):
            response = self.app.test_client().post(
                "/api/v1/books/recognize",
                headers={"X-API-Key": "test-app-key"},
                data={"images": (BytesIO(b"image"), "cover.jpg")},
                content_type="multipart/form-data",
            )

        self.assertEqual(response.status_code, 503)
        self.assertEqual(
            response.get_json(),
            {"error": "OpenRouter rate limit reached."},
        )

    def test_pipeline_uses_openrouter_not_local_or_ocr_strategy(self):
        strategy_names = [
            strategy.name for strategy in RecognitionPipeline().strategies
        ]

        self.assertEqual(strategy_names, ["barcode", "openrouter_vision"])

    def test_pipeline_skips_vision_when_barcode_result_is_complete(self):
        primary = Mock()
        primary.is_primary = True
        primary.name = "barcode"
        primary.recognize.return_value = [{
            "title": "Known Book",
            "author": "A. Writer",
        }]
        secondary = Mock()
        secondary.is_primary = False
        secondary.name = "openrouter_vision"

        pipeline = RecognitionPipeline()
        pipeline.strategies = [primary, secondary]

        with self.app.app_context():
            result = pipeline.recognize([], [])

        self.assertEqual(result, primary.recognize.return_value)
        secondary.recognize.assert_not_called()

    def test_app_reads_openrouter_settings_from_environment(self):
        with patch.dict(os.environ, {
            "OPENROUTER_API_KEY": "environment-key",
            "OPENROUTER_MODEL": "test/model:free",
        }):
            app = create_app({
                "DATABASE": str(Path(self.temp_dir.name) / "env-books.db"),
                "UPLOAD_FOLDER": str(Path(self.temp_dir.name) / "env-uploads"),
            })

        self.assertEqual(app.config["OPENROUTER_API_KEY"], "environment-key")
        self.assertEqual(app.config["OPENROUTER_MODEL"], "test/model:free")


if __name__ == "__main__":
    unittest.main()
