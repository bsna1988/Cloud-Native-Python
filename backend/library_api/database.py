import sqlite3
from contextlib import contextmanager
from pathlib import Path
from threading import Lock

from flask import current_app

_schema_migration_lock = Lock()
_migrated_database_paths = set()


def _ensure_idempotency_schema(connection, database_path):
    normalized_path = str(Path(database_path).resolve())
    with _schema_migration_lock:
        if normalized_path in _migrated_database_paths:
            return

        columns = connection.execute("PRAGMA table_info(book)").fetchall()
        if not columns:
            raise sqlite3.OperationalError(
                "Database schema is not initialized: missing book table"
            )

        column_names = {column[1] for column in columns}
        if "idempotency_key" not in column_names:
            connection.execute(
                "ALTER TABLE book ADD COLUMN idempotency_key TEXT"
            )

        connection.execute(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_book_idempotency_key
            ON book(idempotency_key)
            """
        )
        connection.commit()
        _migrated_database_paths.add(normalized_path)


@contextmanager
def database_connection():
    database_path = current_app.config["DATABASE"]
    connection = sqlite3.connect(database_path, timeout=30)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")

    try:
        _ensure_idempotency_schema(connection, database_path)
        yield connection
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()