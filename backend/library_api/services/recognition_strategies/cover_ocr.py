import re

import requests
from flask import current_app

try:
    import spacy
except ImportError:
    spacy = None

try:
    import easyocr
except ImportError:  # EasyOCR may be unavailable in some environments.
    easyocr = None

from library_api.services.book_metadata import query_book_metadata
from library_api.services.recognition_strategies.barcode import (
    normalize_isbn,
    read_isbn_from_barcode,
)
from library_api.services.recognition_strategies.base import RecognitionStrategy


def extract_isbn_from_ocr(results):
    """Find a checksum-valid ISBN in OCR text."""
    text = "\n".join(result[1] for result in results)
    candidates = re.findall(
        r"(?<![A-Z0-9])(?:ISBN(?:-1[03])?\s*[:.]?\s*)?"
        r"((?:\d[\s-]*){9,12}[\dX])(?![A-Z0-9])",
        text.upper(),
    )
    for candidate in candidates:
        isbn = normalize_isbn(candidate)
        if isbn:
            return isbn
    return None


def read_text_from_image(image_path):
    """Run EasyOCR and return its detected text regions."""
    if easyocr is None:
        return []
    try:
        reader = easyocr.Reader(["en", "fr"], gpu=False)
        return reader.readtext(image_path, detail=1, paragraph=False)
    except Exception as error:
        current_app.logger.exception("EasyOCR failed")
        raise ValueError("OCR could not read the image") from error


def extract_best_title_from_ocr(results):
    """Choose prominent text lines as the likely book title."""
    detected_lines = []
    max_height = 0

    for bounding_box, text, confidence in results:
        top_left_y = bounding_box[0][1]
        bottom_left_y = bounding_box[3][1]
        line_height = bottom_left_y - top_left_y
        max_height = max(max_height, line_height)

        detected_lines.append({
            "text": text,
            "confidence": confidence,
            "height": line_height,
            "y_position": top_left_y,
        })

    title_components = [
        line for line in detected_lines
        if line["height"] >= 0.4 * max_height
    ]
    title_components.sort(key=lambda line: line["y_position"])

    return " ".join(line["text"] for line in title_components).strip()


def normalize_spaced_name(text):
    """Join OCR-separated letters when a line looks like a spaced name."""
    normalized_lines = []

    for line in text.strip().splitlines():
        words = line.split()
        line_text = line.strip()

        if (
            len(words) >= 3
            and sum(len(word) <= 2 for word in words) >= len(words) * 0.7
        ):
            line_text = "".join(words)

        # Correct a common OCR substitution at the end of a name.
        line_text = re.sub(r"(?<=\w)2$", "Z", line_text)
        normalized_lines.append(line_text)

    return " ".join(normalized_lines)


def extract_author_names_from_ocr(results):
    """Use spaCy named-entity recognition to find person names in OCR text."""
    if spacy is None:
        return []

    raw_text = "\n".join(
        text.strip()
        for _, text, confidence in results
        if confidence >= 0.25
    )
    author_text = normalize_spaced_name(raw_text)

    try:
        nlp = spacy.load("en_core_web_sm")
    except OSError as error:
        current_app.logger.exception("spaCy English model is unavailable")
        return []

    document = nlp(author_text)
    return [
        entity.text
        for entity in document.ents
        if entity.label_ == "PERSON"
    ]


class CoverOcrRecognitionStrategy(RecognitionStrategy):
    name = "cover_ocr"
    is_primary = False

    def recognize(self, uploaded_files, temp_paths):
        if not easyocr or spacy is None:
            return None

        cover_image_path = None
        for temp_path in temp_paths:
            if not read_isbn_from_barcode(temp_path):
                cover_image_path = temp_path
                break

        if not cover_image_path:
            return None

        try:
            ocr_results = read_text_from_image(cover_image_path)
        except Exception:
            return None

        if not ocr_results:
            return None

        ocr_title = extract_best_title_from_ocr(ocr_results)
        ocr_authors = extract_author_names_from_ocr(ocr_results)
        ocr_author = " ".join(ocr_authors)

        if not ocr_title and not ocr_author:
            return None

        try:
            metadata = query_book_metadata(ocr_title, author=ocr_author)
            if metadata:
                return metadata
        except requests.RequestException:
            pass

        return [{"title": ocr_title, "author": ocr_author, "barcode": ""}]
