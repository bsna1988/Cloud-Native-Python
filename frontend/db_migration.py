import sqlite3

conn = sqlite3.connect("books.db")  # Same database path your app uses
conn.execute("PRAGMA foreign_keys = OFF")

try:
    with conn:
        conn.execute("""
            CREATE TABLE book_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                barcode varchar(30),
                title varchar(30),
                author varchar(30),
                release_date date,
                price decimal(5,2),
                publisher varchar(30),
                description varchar(100),
                thumbnail BLOB
            )
        """)

        conn.execute("""
            INSERT INTO book_new
                (id, barcode, title, author, release_date, price,
                 publisher, description, thumbnail)
            SELECT
                id, barcode, title, author, release_date, price,
                publisher, description, thumbnail
            FROM book
            ORDER BY id IS NULL, id, rowid
        """)

        conn.execute("DROP TABLE book")
        conn.execute("ALTER TABLE book_new RENAME TO book")
finally:
    conn.execute("PRAGMA foreign_keys = ON")
    conn.close()