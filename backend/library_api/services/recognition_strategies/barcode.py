import re

import requests
import zxingcpp
from flask import current_app
from PIL import Image

from library_api.services.book_metadata import query_book_metadata
from library_api.services.recognition_strategies.base import RecognitionStrategy


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


class BarcodeRecognitionStrategy(RecognitionStrategy):
    name = "barcode"
    is_primary = True

    def recognize(self, uploaded_files, temp_paths):
        isbn = None
        for temp_path in temp_paths:
            file_isbn = read_isbn_from_barcode(temp_path)
            if file_isbn:
                isbn = file_isbn
                break

        if not isbn:
            return None

        try:
            metadata = query_book_metadata(isbn=isbn)
            if metadata:
                for book in metadata:
                    book["barcode"] = isbn
                return metadata
        except requests.RequestException:
            current_app.logger.exception(
                "OpenLibrary ISBN lookup failed for detected ISBN"
            )

        return [{"title": "", "author": "", "barcode": isbn}]
