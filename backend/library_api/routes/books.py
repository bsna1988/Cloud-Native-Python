import json

from flask import Blueprint, jsonify, request

from library_api.repositories.books import DuplicateBookError
from library_api.services.books import create_book
from library_api.services.recognition import recognize_image
from library_api.services.books import (
    create_book,
    get_book as find_book,
    list_books as find_books,
    delete_book as remove_book_service,
    update_book as update_book_service,
)
from library_api.services.book_metadata import query_book_metadata

books_bp = Blueprint("books", __name__)

@books_bp.get("")
def get_books():
    return jsonify({"books": find_books()}), 200


@books_bp.get("/<int:book_id>")
def get_one_book(book_id):
    book = find_book(book_id)

    if book is None:
        return jsonify({"error": "Book not found"}), 404

    return jsonify({"book": book}), 200

@books_bp.post("")
def add_book():
    book_json = request.form.get("book")
    if not book_json:
        return jsonify({"error": "Missing book form field"}), 400

    try:
        book = json.loads(book_json)
    except json.JSONDecodeError:
        return jsonify({"error": "Book data must be valid JSON"}), 400

    images = [
        image for image in request.files.getlist("images")
        if image.filename
    ]

    try:
        created_book = create_book(book, images)
    except ValueError as error:
        return jsonify({"error": str(error)}), 400
    except DuplicateBookError:
        return jsonify({"error": "A book with this title already exists"}), 409

    return jsonify({"book": created_book}), 201


@books_bp.post("/recognize")
def recognize_book():
    images = [
        image for image in request.files.getlist("images")
        if image.filename
    ]
    if not images:
        image = request.files.get("image")
        if image and image.filename:
            images = [image]

    if not images:
        return jsonify({"error": "Upload an image in the 'image' field"}), 400

    try:
        books = recognize_image(images)
    except ValueError as error:
        return jsonify({"error": str(error)}), 422

    return jsonify({"status": "recognized", "books": books}), 200


@books_bp.delete("/<int:book_id>")
def delete_one_book(book_id):
    book = remove_book_service(book_id)
    if book is None:
        return jsonify({"error": "Book not found"}), 404
    return jsonify({"status": "deleted", "book": book}), 200


@books_bp.put("/<int:book_id>")
def update_one_book(book_id):
    book_json = request.form.get("book")
    if not book_json:
        return jsonify({"error": "Missing book form field"}), 400

    try:
        book = json.loads(book_json)
    except json.JSONDecodeError:
        return jsonify({"error": "Book data must be valid JSON"}), 400

    removed_images_json = request.form.get("removed_images", "[]")
    try:
        removed_images = json.loads(removed_images_json)
    except json.JSONDecodeError:
        removed_images = []

    images = [
        image for image in request.files.getlist("images")
        if image.filename
    ]

    try:
        updated_book = update_book_service(book_id, book, images, removed_images)
        if updated_book is None:
            return jsonify({"error": "Book not found"}), 404
    except ValueError as error:
        return jsonify({"error": str(error)}), 400
    except DuplicateBookError:
        return jsonify({"error": "A book with this title already exists"}), 409

    return jsonify({"book": updated_book}), 200


@books_bp.get("/barcode/<isbn>")
def search_by_barcode(isbn):
    books = query_book_metadata(isbn=isbn)
    if not books:
        return jsonify({"error": "Book not found for barcode"}), 404
    return jsonify({"status": "found", "books": books}), 200