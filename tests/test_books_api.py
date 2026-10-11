import json
import sqlite3
import tempfile
import unittest
from contextlib import closing
from io import BytesIO
from pathlib import Path

from library_api import create_app


class BooksApiTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        directory = Path(self.temp_dir.name)
        database_path = directory / "books.db"
        schema_path = Path(__file__).resolve().parents[1] / "db" / "schema.sql"

        with closing(sqlite3.connect(database_path)) as connection:
            with connection:
                connection.executescript(schema_path.read_text())

        app = create_app({
            "TESTING": True,
            "DATABASE": str(database_path),
            "UPLOAD_FOLDER": str(directory / "uploads"),
        })
        self.client = app.test_client()

    def test_get_books_returns_empty_list(self):
        response = self.client.get("/api/v1/books")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json(), {"books": []})

    def test_get_missing_book_returns_not_found(self):
        response = self.client.get("/api/v1/books/1")

        self.assertEqual(response.status_code, 404)
        self.assertEqual(response.get_json(), {"error": "Book not found"})

    def test_create_book_and_get_details_with_images(self):
        response = self.client.post(
            "/api/v1/books",
            data={
                "book": json.dumps({
                    "barcode": "9780306406157",
                    "title": "Test Book",
                    "author": "Test Author",
                    "release_date": "2024",
                    "description": "A test description.",
                }),
                "images": [
                    (BytesIO(b"cover image"), "cover.jpg"),
                    (BytesIO(b"back image"), "back.jpg"),
                ],
            },
            content_type="multipart/form-data",
        )

        self.assertEqual(response.status_code, 201)
        created_book = response.get_json()["book"]
        self.assertEqual(created_book["barcode"], "9780306406157")
        self.assertEqual(created_book["thumbnail"], created_book["images"][0])
        self.assertEqual(len(created_book["images"]), 2)

        details_response = self.client.get(
            f"/api/v1/books/{created_book['id']}"
        )

        self.assertEqual(details_response.status_code, 200)
        self.assertEqual(details_response.get_json()["book"], created_book)

    def test_create_duplicate_book_returns_conflict(self):
        data = {
            "book": json.dumps({"title": "Test Book", "author": "Test Author"}),
            "images": (BytesIO(b"cover image"), "cover.jpg"),
        }
        first_response = self.client.post(
            "/api/v1/books",
            data=data,
            content_type="multipart/form-data",
        )
        self.assertEqual(first_response.status_code, 201)

        duplicate_response = self.client.post(
            "/api/v1/books",
            data={
                "book": json.dumps({
                    "title": "Test Book",
                    "author": "Another Author",
                }),
                "images": (BytesIO(b"another cover"), "another.jpg"),
            },
            content_type="multipart/form-data",
        )

        self.assertEqual(duplicate_response.status_code, 409)
        self.assertEqual(
            duplicate_response.get_json(),
            {"error": "A book with this title already exists"},
        )

    def test_create_book_without_image_returns_bad_request(self):
        response = self.client.post(
            "/api/v1/books",
            data={
                "book": json.dumps({"title": "Test Book", "author": "Test Author"}),
            },
            content_type="multipart/form-data",
        )

        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.get_json(), {"error": "Upload at least one image"})


if __name__ == "__main__":
    unittest.main()
