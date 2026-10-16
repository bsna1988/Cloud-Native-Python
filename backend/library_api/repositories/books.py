from library_api.database import database_connection


class DuplicateBookError(Exception):
    pass


def create_book(book, image_urls):
    thumbnail = image_urls[0] if image_urls else None

    with database_connection() as connection:
        existing = connection.execute(
            "SELECT id FROM book WHERE title = ?",
            (book["title"],),
        ).fetchone()

        if existing:
            raise DuplicateBookError(book["title"])

        cursor = connection.execute(
            """
            INSERT INTO book
                (barcode, title, author, release_date, description, thumbnail)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                book.get("barcode"),
                book["title"],
                book["author"],
                book.get("release_date", ""),
                book.get("description", ""),
                thumbnail,
            ),
        )
        book_id = cursor.lastrowid

        connection.executemany(
            "INSERT INTO book_images (book_id, image_url) VALUES (?, ?)",
            [(book_id, image_url) for image_url in image_urls],
        )

        row = connection.execute(
            "SELECT * FROM book WHERE id = ?",
            (book_id,),
        ).fetchone()

        created_book = dict(row)
        created_book["images"] = image_urls
        return created_book


def get_book(book_id):
    with database_connection() as connection:
        row = connection.execute(
            "SELECT * FROM book WHERE id = ?",
            (book_id,),
        ).fetchone()

        if row is None:
            return None

        book = dict(row)
        image_rows = connection.execute(
            "SELECT image_url FROM book_images WHERE book_id = ? ORDER BY id",
            (book_id,),
        ).fetchall()
        book["images"] = [image["image_url"] for image in image_rows]
        return book
    
def list_books():
    with database_connection() as connection:
        rows = connection.execute(
            "SELECT * FROM book ORDER BY title"
        ).fetchall()

        if not rows:
            return []

        books = [dict(row) for row in rows]
        book_ids = [book["id"] for book in books]
        placeholders = ",".join("?" for _ in book_ids)

        image_rows = connection.execute(
            f"""
            SELECT book_id, image_url
            FROM book_images
            WHERE book_id IN ({placeholders})
            ORDER BY id
            """,
            book_ids,
        ).fetchall()

        images_by_book = {book_id: [] for book_id in book_ids}
        for image_row in image_rows:
            images_by_book[image_row["book_id"]].append(image_row["image_url"])

        for book in books:
            book["images"] = images_by_book[book["id"]]

        return books


def delete_book(book_id):
    with database_connection() as connection:
        row = connection.execute(
            "SELECT * FROM book WHERE id = ?",
            (book_id,),
        ).fetchone()

        if row is None:
            return None

        book = dict(row)
        image_rows = connection.execute(
            "SELECT image_url FROM book_images WHERE book_id = ?",
            (book_id,),
        ).fetchall()
        image_urls = [img["image_url"] for img in image_rows]

        connection.execute("DELETE FROM book_images WHERE book_id = ?", (book_id,))
        connection.execute("DELETE FROM book WHERE id = ?", (book_id,))
        return {"book": book, "image_urls": image_urls}


def update_book(book_id, book, new_image_urls, removed_image_urls):
    with database_connection() as connection:
        row = connection.execute("SELECT * FROM book WHERE id = ?", (book_id,)).fetchone()
        if row is None:
            return None

        connection.execute(
            """
            UPDATE book
            SET barcode = ?, title = ?, author = ?, release_date = ?, description = ?
            WHERE id = ?
            """,
            (
                book.get("barcode"),
                book["title"],
                book["author"],
                book.get("release_date", ""),
                book.get("description", ""),
                book_id,
            ),
        )

        for img_url in removed_image_urls:
            connection.execute("DELETE FROM book_images WHERE book_id = ? AND image_url = ?", (book_id, img_url))

        for img_url in new_image_urls:
            connection.execute("INSERT INTO book_images (book_id, image_url) VALUES (?, ?)", (book_id, img_url))

        first_img = connection.execute("SELECT image_url FROM book_images WHERE book_id = ? ORDER BY id LIMIT 1", (book_id,)).fetchone()
        thumbnail = first_img["image_url"] if first_img else None
        connection.execute("UPDATE book SET thumbnail = ? WHERE id = ?", (thumbnail, book_id))

        updated_row = connection.execute("SELECT * FROM book WHERE id = ?", (book_id,)).fetchone()
        updated_book = dict(updated_row)
        image_rows = connection.execute("SELECT image_url FROM book_images WHERE book_id = ? ORDER BY id", (book_id,)).fetchall()
        updated_book["images"] = [img["image_url"] for img in image_rows]
        return updated_book