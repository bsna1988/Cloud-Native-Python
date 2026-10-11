import os
import re
import tempfile
from pathlib import Path

import requests
import spacy
import zxingcpp
from flask import current_app
from PIL import Image, ImageOps

try:
    import easyocr
except ImportError:  # EasyOCR may be unavailable in some environments.
    easyocr = None


def recognize_image(uploaded_files):
    """Return book metadata candidates recognized from uploaded book images."""
    if not isinstance(uploaded_files, (list, tuple)):
        uploaded_files = [uploaded_files]
    if not uploaded_files:
        raise ValueError("Upload at least one image")

    upload_folder = current_app.config["UPLOAD_FOLDER"]
    temp_paths = []

    try:
        isbn = None
        all_ocr_results = []
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

            isbn = isbn or read_isbn_from_barcode(temp_path)
            results = read_text_from_image(temp_path) if easyocr else []
            all_ocr_results.extend(results)
            if not isbn:
                isbn = extract_isbn_from_ocr(results)

        openlibrary_metadata = None
        if isbn:
            try:
                openlibrary_metadata = query_book_metadata(isbn=isbn)
            except requests.RequestException:
                current_app.logger.exception(
                    "OpenLibrary ISBN lookup failed for detected ISBN"
                )

        ocr_title = extract_best_title_from_ocr(all_ocr_results) if all_ocr_results else ""
        ocr_authors = extract_author_names_from_ocr(all_ocr_results) if all_ocr_results else []
        ocr_author = " ".join(ocr_authors)

        if openlibrary_metadata:
            for book in openlibrary_metadata:
                book["barcode"] = isbn
                if not book.get("title") and ocr_title:
                    book["title"] = ocr_title
                if not book.get("author") and ocr_author:
                    book["author"] = ocr_author
            return openlibrary_metadata

        if not all_ocr_results:
            if isbn:
                return [{"title": "", "author": "", "barcode": isbn}]
            if easyocr is None:
                raise ValueError("OCR is unavailable because EasyOCR is not installed")
            raise ValueError("OCR could not extract text from the images")

        title = ocr_title
        author = ocr_author

        try:
            metadata = query_book_metadata(title, author=author)
        except requests.RequestException:
            metadata = None

        if metadata:
            if isbn:
                for book in metadata:
                    book["barcode"] = isbn
            return metadata

        return [{"title": title, "author": ", ".join(ocr_authors), "barcode": isbn}]

    finally:
        for temp_path in temp_paths:
            if os.path.exists(temp_path):
                os.remove(temp_path)


def read_isbn_from_barcode(image_path):
    """Return a checksum-valid ISBN decoded from an image barcode, if present."""
    try:
        with Image.open(image_path) as image:
            barcodes = zxingcpp.read_barcodes(image)
    except Exception:
        current_app.logger.exception("Barcode scanning failed")
        return None

    for barcode in barcodes:
        isbn = normalize_isbn(barcode.text)
        if isbn:
            return isbn
    return None


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


def normalize_isbn(value):
    """Normalize and validate an ISBN-10 or ISBN-13."""
    isbn = re.sub(r"[\s-]", "", value).upper()
    if len(isbn) == 10:
        if not isbn[:9].isdigit() or isbn[-1] not in "0123456789X":
            return None
        checksum = sum(
            int(digit) * (10 - index)
            for index, digit in enumerate(isbn[:9])
        )
        checksum += 10 if isbn[-1] == "X" else int(isbn[-1])
        return isbn if checksum % 11 == 0 else None

    if len(isbn) == 13 and isbn.isdigit() and isbn.startswith(("978", "979")):
        checksum = sum(
            int(digit) * (1 if index % 2 == 0 else 3)
            for index, digit in enumerate(isbn[:12])
        )
        check_digit = (10 - checksum % 10) % 10
        return isbn if check_digit == int(isbn[-1]) else None

    return None


def read_text_from_image(image_path):
    """Run EasyOCR and return its detected text regions."""
    try:
        reader = easyocr.Reader(["en", "fr"], gpu=True)
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


def query_book_metadata(title=None, author=None, isbn=None):
    """Search OpenLibrary and return up to five matching book candidates."""
    params = {"limit": 5}

    if isbn:
        params["isbn"] = isbn
    elif title:
        params["title"] = title
    else:
        return []

    if author and not isbn:
        params["author"] = author

    response = requests.get(
        "https://openlibrary.org/search.json",
        params=params,
        timeout=60,
    )
    response.raise_for_status()

    documents = response.json().get("docs", [])
    return [doc_to_book(document) for document in documents]