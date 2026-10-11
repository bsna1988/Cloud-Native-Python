import base64
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from library_api import create_app
from library_api.services.recognition import RecognitionPipeline
from library_api.services.recognition_strategies.lm_studio import (
    LmStudioVisionRecognitionStrategy,
)


class LmStudioRecognitionTests(unittest.TestCase):
    def test_lm_studio_image_response_returns_book_candidate(self):
        app = create_app()
        response = Mock()
        response.json.return_value = {
            "choices": [{
                "message": {
                    "content": '{"title": "Test Book", "author": "A. Writer"}',
                },
            }],
        }

        with tempfile.TemporaryDirectory() as directory:
            image_path = Path(directory) / "cover.jpg"
            image_path.write_bytes(b"test image")

            with (
                app.app_context(),
                patch(
                    "library_api.services.recognition_strategies.lm_studio.requests.post",
                    return_value=response,
                ) as post,
                patch(
                    "library_api.services.recognition_strategies.lm_studio.query_book_metadata",
                    return_value=[],
                ),
            ):
                result = LmStudioVisionRecognitionStrategy().recognize(
                    [], [str(image_path)]
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
        self.assertEqual(
            args[0],
            "http://127.0.0.1:1234/v1/chat/completions",
        )
        self.assertEqual(kwargs["json"]["model"], "google/gemma-3-4b")
        image_content = kwargs["json"]["messages"][0]["content"][1]
        self.assertEqual(
            image_content["image_url"]["url"],
            "data:image/jpeg;base64,"
            + base64.b64encode(b"test image").decode("ascii"),
        )

    def test_pipeline_uses_lm_studio_instead_of_cover_ocr(self):
        strategy_names = [
            strategy.name for strategy in RecognitionPipeline().strategies
        ]

        self.assertEqual(strategy_names, ["barcode", "lm_studio_vision"])


if __name__ == "__main__":
    unittest.main()
