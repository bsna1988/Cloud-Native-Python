import json
import sqlite3
import tempfile
import unittest
from contextlib import closing
from io import BytesIO
from pathlib import Path
from PIL import Image

from library_api import create_app


def generate_test_jpeg():
    img = Image.new("RGB", (100, 100), color="red")
    buf = BytesIO()
    img.save(buf, "JPEG")
    buf.seek(0)
    return buf


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
        self.headers = {"X-API-Key": "homebooks_secret_key_12345"}

    def test_get_books_returns_empty_list(self):
        response = self.client.get("/api/v1/books", headers=self.headers)

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json(), {"books": []})

    def test_get_missing_book_returns_not_found(self):
        response = self.client.get("/api/v1/books/1", headers=self.headers)

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
                    (generate_test_jpeg(), "cover.jpg"),
                    (generate_test_jpeg(), "back.jpg"),
                ],
            },
            content_type="multipart/form-data",
            headers=self.headers,
        )

        self.assertEqual(response.status_code, 201)
        created_book = response.get_json()["book"]
        self.assertEqual(created_book["barcode"], "9780306406157")
        self.assertEqual(created_book["thumbnail"], created_book["images"][0])
        self.assertEqual(len(created_book["images"]), 2)

        details_response = self.client.get(
            f"/api/v1/books/{created_book['id']}",
            headers=self.headers,
        )

        self.assertEqual(details_response.status_code, 200)
        self.assertEqual(details_response.get_json()["book"], created_book)

    def test_create_book_without_author_uses_unknown(self):
        for author in (None, "", "   "):
            with self.subTest(author=author):
                book = {"title": f"Book without author {author!r}"}
                if author is not None:
                    book["author"] = author

                response = self.client.post(
                    "/api/v1/books",
                    data={
                        "book": json.dumps(book),
                        "images": (generate_test_jpeg(), "cover.jpg"),
                    },
                    content_type="multipart/form-data",
                    headers=self.headers,
                )

                self.assertEqual(response.status_code, 201)
                self.assertEqual(response.get_json()["book"]["author"], "Unknown")

    def test_create_duplicate_book_returns_conflict(self):
        data = {
            "book": json.dumps({"title": "Test Book", "author": "Test Author"}),
            "images": (generate_test_jpeg(), "cover.jpg"),
        }
        first_response = self.client.post(
            "/api/v1/books",
            data=data,
            content_type="multipart/form-data",
            headers=self.headers,
        )
        self.assertEqual(first_response.status_code, 201)

        duplicate_response = self.client.post(
            "/api/v1/books",
            data={
                "book": json.dumps({
                    "title": "Test Book",
                    "author": "Another Author",
                }),
                "images": (generate_test_jpeg(), "another.jpg"),
            },
            content_type="multipart/form-data",
            headers=self.headers,
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
            headers=self.headers,
        )

        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.get_json(), {"error": "Upload at least one image"})

    def test_create_book_with_idempotency_key_returns_existing_on_retry(self):
        idempotency_key = "uuid-12345-test"
        data = {
            "book": json.dumps({
                "title": "Idempotent Book",
                "author": "Author",
                "idempotency_key": idempotency_key,
            }),
            "images": (generate_test_jpeg(), "cover.jpg"),
        }
        first_response = self.client.post(
            "/api/v1/books",
            data=data,
            content_type="multipart/form-data",
            headers=self.headers,
        )
        self.assertEqual(first_response.status_code, 201)
        book1 = first_response.get_json()["book"]

        retry_response = self.client.post(
            "/api/v1/books",
            data={
                "book": json.dumps({
                    "title": "Different Title",
                    "author": "Different Author",
                    "idempotency_key": idempotency_key,
                }),
                "images": (generate_test_jpeg(), "another.jpg"),
            },
            content_type="multipart/form-data",
            headers=self.headers,
        )
        self.assertEqual(retry_response.status_code, 201)
        book2 = retry_response.get_json()["book"]
        self.assertEqual(book1["id"], book2["id"])
        self.assertEqual(book1["title"], book2["title"])

    def test_create_book_migrates_legacy_database_for_idempotency_key(self):
        legacy_database_path = Path(self.temp_dir.name) / "legacy-books.db"
        with closing(sqlite3.connect(legacy_database_path)) as connection:
            with connection:
                connection.executescript(
                    """
                    CREATE TABLE book (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        barcode TEXT,
                        title TEXT,
                        author TEXT,
                        release_date TEXT,
                        price REAL,
                        publisher TEXT,
                        description TEXT,
                        thumbnail BLOB
                    );
                    CREATE TABLE book_images (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        book_id INTEGER,
                        image_url TEXT,
                        FOREIGN KEY (book_id) REFERENCES book(id)
                    );
                    """
                )

        legacy_app = create_app({
            "TESTING": True,
            "DATABASE": str(legacy_database_path),
            "UPLOAD_FOLDER": str(Path(self.temp_dir.name) / "legacy-uploads"),
        })
        response = legacy_app.test_client().post(
            "/api/v1/books",
            data={
                "book": json.dumps({
                    "title": "Migrated Book",
                    "author": "Migration Author",
                    "idempotency_key": "legacy-database-key",
                }),
                "images": (generate_test_jpeg(), "cover.jpg"),
            },
            content_type="multipart/form-data",
            headers=self.headers,
        )

        self.assertEqual(response.status_code, 201)
        self.assertEqual(response.get_json()["book"]["title"], "Migrated Book")


if __name__ == "__main__":
    unittest.main()
