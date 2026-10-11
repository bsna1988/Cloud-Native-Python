from library_api.services.recognition_strategies.barcode import (
    BarcodeRecognitionStrategy,
)
from library_api.services.recognition_strategies.base import RecognitionStrategy
from library_api.services.recognition_strategies.cover_ocr import (
    CoverOcrRecognitionStrategy,
)
from library_api.services.recognition_strategies.lm_studio import (
    LmStudioVisionRecognitionStrategy,
)

__all__ = [
    "BarcodeRecognitionStrategy",
    "CoverOcrRecognitionStrategy",
    "LmStudioVisionRecognitionStrategy",
    "RecognitionStrategy",
]
