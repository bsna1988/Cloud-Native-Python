import sqlite3
from contextlib import contextmanager

from flask import current_app

@contextmanager
def database_connection():
    connection = sqlite3.connect(current_app.config["DATABASE"])
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")

    try:
        try:
            connection.execute("ALTER TABLE book ADD COLUMN idempotency_key TEXT UNIQUE")
            connection.commit()
        except sqlite3.OperationalError:
            pass
        yield connection
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()