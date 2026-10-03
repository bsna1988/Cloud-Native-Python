import os
import re
import tempfile
from pathlib import Path

import requests
import spacy
from flask import current_app

try:
    import easyocr
except ImportError:  # EasyOCR may be unavailable in some environments.
    easyocr = None


def recognize_image(uploaded_file):
    """Return book metadata candidates recognized from an uploaded cover."""
    if easyocr is None:
        raise ValueError("OCR is unavailable because EasyOCR is not installed")

    upload_folder = current_app.config["UPLOAD_FOLDER"]
    suffix = Path(uploaded_file.filename or "").suffix or ".jpg"
    temp_path = None

    try:
        with tempfile.NamedTemporaryFile(
            dir=upload_folder,
            suffix=suffix,
            delete=False,
        ) as temp_file:
            temp_path = temp_file.name
            uploaded_file.save(temp_file)

        results = read_text_from_image(temp_path)
        if not results:
            raise ValueError("OCR could not extract text from the image")

        title = extract_best_title_from_ocr(results)
        authors = extract_author_names_from_ocr(results)
        author = " ".join(authors)

        try:
            metadata = query_book_metadata(title, author=author)
        except requests.RequestException:
            # OCR may still provide useful title and author when OpenLibrary
            # is unavailable, so return those as a candidate.
            metadata = None

        if metadata:
            return metadata

        return [{"title": title, "author": ", ".join(authors)}]

    finally:
        if temp_path and os.path.exists(temp_path):
            os.remove(temp_path)


def read_text_from_image(image_path):
    """Run EasyOCR and return its detected text regions."""
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
        raise ValueError(
            "The spaCy model en_core_web_sm is not installed"
        ) from error

    document = nlp(author_text)
    return [
        entity.text
        for entity in document.ents
        if entity.label_ == "PERSON"
    ]


def doc_to_book(document):
    """Convert one OpenLibrary search result into this app's book shape."""
    return {
        "title": document.get("title", ""),
        "author": ", ".join(document.get("author_name", [])),
        "release_date": str(document.get("first_publish_year", "")),
        "publisher": document.get("publisher", [""])[0],
        "description": "",
        "barcode": document.get("isbn", [None])[0],
    }


def query_book_metadata(title, author=None):
    """Search OpenLibrary and return up to five matching book candidates."""
    params = {
        "title": title,
        "limit": 5,
    }

    if author:
        params["author"] = author

    response = requests.get(
        "https://openlibrary.org/search.json",
        params=params,
        timeout=60,
    )
    response.raise_for_status()

    documents = response.json().get("docs", [])
    return [doc_to_book(document) for document in documents]