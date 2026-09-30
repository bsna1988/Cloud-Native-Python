## python -m spacy download en_core_web_sm
from flask import Flask, jsonify, make_response, abort, request
from werkzeug.utils import secure_filename
import os
import sqlite3
import uuid
import requests
import spacy
import re
from flask_cors import CORS, cross_origin


try:
    import easyocr
except ImportError:  # pragma: no cover
    easyocr = None

app = Flask(__name__)
UPLOAD_FOLDER = os.path.join(os.getcwd(), 'uploads')
os.makedirs(UPLOAD_FOLDER, exist_ok=True)


@app.route('/api/v1/info', methods=['GET'])
def home_index():
    conn = sqlite3.connect('books.db')
    print("Opened database successfully")
    api_list = []
    cursor = conn.execute("SELECT buildtime, version, methods, links FROM apirelease")
    for row in cursor:
        a_dict = {}
        a_dict["buildtime"] = row[0]
        a_dict["version"] = row[1]
        a_dict["methods"] = row[2]
        a_dict["links"] = row[3]
        api_list.append(a_dict)
    conn.close()
    return jsonify({'api_version': api_list}), 200


@app.route('/api/v1/books', methods=['GET'])
def get_books():
    return list_books()


def list_books():
    conn = sqlite3.connect('books.db')
    print("Opened database successfully")
    book_list = []
    cursor = conn.execute("SELECT id, barcode, title, author, release_date, price, publisher, description, thumbnail FROM book")
    for row in cursor:
        a_dict = to_book(row)
        book_list.append(a_dict)
    conn.close()
    return jsonify({'books': book_list}), 200


@app.route('/api/v1/books/<int:book_id>', methods=['GET'])
def get_book(book_id):
    return list_book(book_id)


def list_book(book_id):
    conn = sqlite3.connect('books.db')
    print("Opened database successfully")
    cursor = conn.execute("SELECT id, barcode, title, author, release_date, price, publisher, description, thumbnail FROM book WHERE id=?", (book_id,))
    row = cursor.fetchone()
    if row:
        a_dict = to_book(row)
        conn.close()
        return jsonify({'book': a_dict}), 200
    else:
        conn.close()
        return jsonify({'error': 'Book not found'}), 404


def to_book(row):
    a_dict = {}
    a_dict["id"] = row[0]
    a_dict["barcode"] = row[1]
    a_dict["title"] = row[2]
    a_dict["author"] = row[3]
    a_dict["release_date"] = row[4]
    a_dict["price"] = row[5]
    a_dict["publisher"] = row[6]
    a_dict["description"] = row[7]
    a_dict["thumbnail"] = row[8]
    return a_dict


@app.errorhandler(404)
def resource_not_found(error):
    return make_response(jsonify({'error': 'Resource not found'}), 404)


def extract_best_title_from_ocr(results):
    detected_lines = []
    max_height = 0
    for (bbox, text, confidence) in results:
        top_left_y = bbox[0][1]
        bottom_left_y = bbox[3][1]
        line_height = bottom_left_y - top_left_y
        if (line_height > max_height):
            max_height = line_height
            
        detected_lines.append({
            "text": text,
            "confidence": confidence,
            "height": line_height,
            "y_position": top_left_y
        })
    
    title_components = []
    for line in detected_lines:
        print(f"Detected line: {line['text']} with height {line['height']} and y_position {line['y_position']}")
        if line["height"] >= 0.4 * max_height:
            title_components.append(line)
            
    title_components.sort(key=lambda x: x["y_position"])
    title = " ".join([line["text"] for line in title_components])
    return title.strip()

def normalize_spaced_name(text):
    text = text.strip()

    lines = []
    for line in text.splitlines():
        words = line.split()
        line_text = line.strip()
        if len(words) >= 3 and sum(len(word) <= 2 for word in words) >= len(words) * 0.7:
            line_text = "".join(words)
        
        # Common OCR correction at the end of a name
        line_text = re.sub(r"(?<=\w)2$", "Z", line_text)
        lines.append(line_text)

    return " ".join(lines)

def extract_author_names_from_ocr(results):
    raw_text = "\n".join(
        text.strip()
        for _, text, confidence in results
        if confidence >= 0.25
    )
    print(f"Raw text extracted from OCR: {raw_text}")

    author_text = normalize_spaced_name(raw_text)
    print(f"Normalized author text: {author_text}")
    
    nlp = spacy.load("en_core_web_sm")
    doc = nlp(author_text)
    author_names = [ent.text for ent in doc.ents if ent.label_ == "PERSON"]
    return author_names

    
def doc_to_book(doc):
    return {
        "title": doc.get("title", ""),
        "author": ", ".join(doc.get("author_name", [])),
        "release_date": str(doc.get("first_publish_year", "")),
        "publisher": doc.get("publisher", [""])[0],
        "description": "",
        "barcode": doc.get("isbn", [None])[0]
    }    


def query_book_metadata(title, author=None):
    params = {
        "title": title,
        "limit": 5
    }

    if author:
        params["author"] = author

    response = requests.get(
        "https://openlibrary.org/search.json",
        params=params,
        timeout=60
    )
    response.raise_for_status()
    
    docs = response.json().get("docs", [])
    if not docs:
        return None
    
    for doc in docs:
        print (f"Found book metadata: {doc.get('title')} by {', '.join(doc.get('author_name', []))}")
    
    books = [doc_to_book(doc) for doc in docs]
    
    return books

def recognize_book_from_image(file_storage):
    filename = secure_filename(file_storage.filename or 'book.jpg')
    unique_name = f"{uuid.uuid4().hex}_{filename}"
    file_path = os.path.join(UPLOAD_FOLDER, unique_name)
    file_storage.save(file_path)

    if easyocr is not None:
        try:
            reader = easyocr.Reader(['en', 'fr'], gpu=False)
            results = reader.readtext(file_path, detail=1, paragraph=False)
        except Exception:
            results = []

    if not results:
        abort(422, description="OCR failed to extract text from the image.")

    title = extract_best_title_from_ocr(results)
    print (f"Extracted title from OCR: {title}")
    author_names = extract_author_names_from_ocr(results)
    print (f"Extracted author names from OCR: {author_names}")
    metadata = query_book_metadata(title,  author=" ".join(author_names))

    if metadata:
        return metadata

    return [
        {
            "title": title,
            "author": ", ".join(author_names)
        }
    ]


@app.route('/api/v1/books/recognize', methods=['POST'])
def recognize_book():
    if 'image' in request.files:
        image = request.files['image']
        if image.filename == '':
            abort(400)
        recognition = recognize_book_from_image(image)
        return jsonify({'status': 'recognized', 'books': recognition}), 200

    abort(400)


@app.route('/api/v1/books', methods=['POST'])
def create_book():
    if not 'title' in request.json or not 'author' in request.json:
        abort(400)
    book = {
        'title': request.json.get('title'),
        'author': request.json.get('author'),
        'release_date': request.json.get('release_date') or '',
        'description': request.json.get('description')
    }
    book_id = add_book(book)
    (response, status) =  list_book(book_id)
    return response, 201

@app.route('/api/v1/books', methods=['DELETE'])
def delete_book():
    if not request.json or not 'title' in request.json or not 'author' in request.json:
        abort(400)
    book = (request.json.get('title'), request.json.get('author'))
    return jsonify({'status': del_book(book)}), 200

def del_book(book):
    conn = sqlite3.connect('books.db')
    print("Opened database successfully")
    try:
        cursor = conn.cursor()
        cursor.execute("DELETE FROM book WHERE title = ? AND author = ?", book)
        conn.commit()
        if cursor.rowcount == 0:
            abort(404)
        return "deleted"
    finally:
        conn.close()

def add_book(book):
    conn = sqlite3.connect('books.db')
    print("Opened database successfully")
    try:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM book WHERE title = ?", (book['title'],))
        data = cursor.fetchall()
        if len(data) != 0:
            abort(409)

        cursor.execute(
            "INSERT INTO book (title, author, release_date, description) VALUES (?, ?, ?, ?)",
            (book['title'], book['author'], book['release_date'], book['description'])
        )
        conn.commit()
        return cursor.lastrowid
    finally:
        conn.close()

@app.route('/api/v1/books/<int:book_id>', methods=['PUT'])
def update_book(book_id):
    if not request.json:
        abort(400)
    book = {}
    for field in request.json.keys():
        book[field] = request.json.get(field)
    print(f"Updating book with ID {book_id} with data: {book}")
    update_book_in_db(book_id, book)
    return list_book(book_id)

def update_book_in_db(book_id, book):
    conn = sqlite3.connect('books.db')
    print("Opened database successfully")
    try:
        cursor = conn.cursor()
        set_clause = ', '.join([f"{field} = ?" for field in book.keys()])
        values = list(book.values())
        values.append(book_id)
        cursor.execute(f"UPDATE book SET {set_clause} WHERE id = ?", values)
        conn.commit()
        if cursor.rowcount == 0:
            abort(404)
    finally:
        conn.close()

CORS(app)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5005, debug=True)