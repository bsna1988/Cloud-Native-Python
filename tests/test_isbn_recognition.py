import sqlite3
import tempfile
import unittest
from contextlib import closing
from pathlib import Path

from library_api import create_app
from library_api.repositories.books import create_book
from library_api.services.recognition_strategies.barcode import (
    normalize_isbn,
    read_isbn_from_barcode,
)
from library_api.services.recognition_strategies.cover_ocr import (
    extract_isbn_from_ocr,
)


class IsbnRecognitionTests(unittest.TestCase):
    def test_normalize_and_validate_isbn_13(self):
        self.assertEqual(normalize_isbn("978-0-306-40615-7"), "9780306406157")

    def test_normalize_and_validate_isbn_10(self):
        self.assertEqual(normalize_isbn("0-306-40615-2"), "0306406152")

    def test_reject_invalid_checksum(self):
        self.assertIsNone(normalize_isbn("9780306406158"))

    def test_reject_non_isbn_ean_13(self):
        self.assertIsNone(normalize_isbn("4006381333931"))

    def test_extract_isbn_from_ocr_text(self):
        results = [([], "ISBN-13: 978-0-306-40615-7", 0.99)]

        self.assertEqual(
            extract_isbn_from_ocr(results),
            "9780306406157",
        )

    def test_recognize_isbn_from_software_architecture_back_image(self):
        image_path = (
            Path(__file__).parent
            / "fixtures"
            / "software_architecture_back.jpg"
        )

        self.assertEqual(
            read_isbn_from_barcode(str(image_path)),
            "9781098151867",
        )
        
    def test_recognize_isbn_from_je_lis_back_image(self):
        image_path = (
            Path(__file__).parent
            / "fixtures"
            / "je_lis_back.jpg"
        )

        self.assertEqual(
            read_isbn_from_barcode(str(image_path)),
            "9782244424187",
        )

    def test_create_book_persists_isbn(self):
        with tempfile.TemporaryDirectory() as directory:
            database_path = Path(directory) / "books.db"
            with closing(sqlite3.connect(database_path)) as connection:
                with connection:
                    connection.executescript(
                        """
                        CREATE TABLE book (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            barcode varchar(30),
                            title varchar(30),
                            author varchar(30),
                            release_date date,
                            description varchar(100),
                            thumbnail BLOB
                        );
                        CREATE TABLE book_images (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            book_id int,
                            image_url varchar(100),
                            FOREIGN KEY (book_id) REFERENCES book(id)
                        );
                        """
                    )

            app = create_app({
                "DATABASE": str(database_path),
                "UPLOAD_FOLDER": directory,
            })
            with app.app_context():
                book = create_book(
                    {
                        "barcode": "9780306406157",
                        "title": "Test Book",
                        "author": "Test Author",
                    },
                    [],
                )

        self.assertEqual(book["barcode"], "9780306406157")


if __name__ == "__main__":
    unittest.main()
