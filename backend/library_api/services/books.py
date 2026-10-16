from pathlib import Path
from uuid import uuid4

from flask import current_app
from werkzeug.utils import secure_filename

from library_api.repositories.books import (
    DuplicateBookError,
    create_book as insert_book,
    get_book as fetch_book,
    list_books as fetch_books,
    delete_book as remove_book,
    update_book as modify_book,
)

ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


def list_books():
    return fetch_books()


def get_book(book_id):
    return fetch_book(book_id)

def create_book(book, uploaded_files):
    if not book.get("title", "").strip():
        raise ValueError("Title is required")
    if not book.get("author", "").strip():
        raise ValueError("Author is required")
    if not uploaded_files:
        raise ValueError("Upload at least one image")

    upload_folder = Path(current_app.config["UPLOAD_FOLDER"])
    saved_paths = []
    image_urls = []

    try:
        for uploaded_file in uploaded_files:
            original_name = secure_filename(uploaded_file.filename or "")
            extension = Path(original_name).suffix.lower()

            if extension not in ALLOWED_EXTENSIONS:
                raise ValueError("Images must be JPG, PNG, or WEBP files")

            filename = f"{uuid4().hex}{extension}"
            file_path = upload_folder / filename
            uploaded_file.save(file_path)

            saved_paths.append(file_path)
            image_urls.append(f"/uploads/{filename}")

        return insert_book(book, image_urls)

    except Exception:
        for file_path in saved_paths:
            file_path.unlink(missing_ok=True)
        raise


def delete_book(book_id):
    result = remove_book(book_id)
    if not result:
        return None
    upload_folder = Path(current_app.config["UPLOAD_FOLDER"])
    for image_url in result["image_urls"]:
        filename = image_url.split("/")[-1]
        file_path = upload_folder / filename
        file_path.unlink(missing_ok=True)
    return result["book"]


def update_book(book_id, book, uploaded_files, removed_image_urls):
    if not book.get("title", "").strip():
        raise ValueError("Title is required")
    if not book.get("author", "").strip():
        raise ValueError("Author is required")

    upload_folder = Path(current_app.config["UPLOAD_FOLDER"])
    saved_paths = []
    new_image_urls = []

    try:
        for uploaded_file in uploaded_files:
            original_name = secure_filename(uploaded_file.filename or "")
            extension = Path(original_name).suffix.lower()

            if extension not in ALLOWED_EXTENSIONS:
                raise ValueError("Images must be JPG, PNG, or WEBP files")

            filename = f"{uuid4().hex}{extension}"
            file_path = upload_folder / filename
            uploaded_file.save(file_path)

            saved_paths.append(file_path)
            new_image_urls.append(f"/uploads/{filename}")

        updated_book = modify_book(book_id, book, new_image_urls, removed_image_urls)
        if not updated_book:
            for file_path in saved_paths:
                file_path.unlink(missing_ok=True)
            return None

        for image_url in removed_image_urls:
            filename = image_url.split("/")[-1]
            file_path = upload_folder / filename
            file_path.unlink(missing_ok=True)

        return updated_book

    except Exception:
        for file_path in saved_paths:
            file_path.unlink(missing_ok=True)
        raise