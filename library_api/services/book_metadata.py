import requests


def doc_to_book(document):
    """Convert one OpenLibrary search result into this app's book shape."""
    return {
        "title": document.get("title", ""),
        "author": ", ".join(document.get("author_name", [])),
        "release_date": str(document.get("first_publish_year", "")),
        "publisher": document.get("publisher", [""])[0],
        "description": "",
        "barcode": document.get("isbn", [None])[0],
    }


def query_book_metadata(title=None, author=None, isbn=None):
    """Search OpenLibrary and return up to five matching book candidates."""
    params = {"limit": 5}

    if isbn:
        params["isbn"] = isbn
    elif title:
        params["title"] = title
    else:
        return []

    if author and not isbn:
        params["author"] = author

    response = requests.get(
        "https://openlibrary.org/search.json",
        params=params,
        timeout=60,
    )
    response.raise_for_status()

    documents = response.json().get("docs", [])
    return [doc_to_book(document) for document in documents]
