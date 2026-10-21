from library_api.services.recognition_strategies.barcode import (
    BarcodeRecognitionStrategy,
)
from library_api.services.recognition_strategies.base import RecognitionStrategy
from library_api.services.recognition_strategies.cover_ocr import (
    CoverOcrRecognitionStrategy,
)
from library_api.services.recognition_strategies.openrouter import (
    OpenRouterVisionRecognitionStrategy,
)

__all__ = [
    "BarcodeRecognitionStrategy",
    "CoverOcrRecognitionStrategy",
    "OpenRouterVisionRecognitionStrategy",
    "RecognitionStrategy",
]
