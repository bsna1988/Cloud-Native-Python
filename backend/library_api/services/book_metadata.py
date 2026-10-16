import requests
from flask import current_app


class MetadataProvider:
    name = "base"

    def search(self, title=None, author=None, isbn=None):
        raise NotImplementedError


class GoogleBooksMetadataProvider(MetadataProvider):
    name = "google_books"

    def search(self, title=None, author=None, isbn=None):
        params = {"maxResults": 5}
        query_parts = []
        if isbn:
            query_parts.append(f"isbn:{isbn}")
        else:
            if title:
                query_parts.append(f"intitle:{title}")
            if author:
                query_parts.append(f"inauthor:{author}")

        if not query_parts:
            return []

        params["q"] = " ".join(query_parts)

        try:
            response = requests.get(
                "https://www.googleapis.com/books/v1/volumes",
                params=params,
                timeout=30,
            )
            response.raise_for_status()
            data = response.json()
            items = data.get("items", [])
            books = []
            for item in items:
                info = item.get("volumeInfo", {})
                identifiers = info.get("industryIdentifiers", [])
                isbn_val = next(
                    (id_obj.get("identifier") for id_obj in identifiers if "ISBN" in id_obj.get("type", "")),
                    isbn,
                )

                books.append({
                    "title": info.get("title", ""),
                    "author": ", ".join(info.get("authors", [])),
                    "release_date": (info.get("publishedDate") or "")[:4],
                    "publisher": info.get("publisher", ""),
                    "description": info.get("description", ""),
                    "barcode": isbn_val,
                })
            return books
        except Exception:
            current_app.logger.exception("Google Books API search failed")
            return []


class OpenLibraryMetadataProvider(MetadataProvider):
    name = "open_library"

    def search(self, title=None, author=None, isbn=None):
        params = {"limit": 5}
        if isbn:
            params["isbn"] = isbn
        elif title:
            params["title"] = title
        else:
            return []

        if author and not isbn:
            params["author"] = author

        try:
            response = requests.get(
                "https://openlibrary.org/search.json",
                params=params,
                timeout=30,
            )
            response.raise_for_status()
            documents = response.json().get("docs", [])
            return [
                {
                    "title": doc.get("title", ""),
                    "author": ", ".join(doc.get("author_name", [])),
                    "release_date": str(doc.get("first_publish_year", "")),
                    "publisher": doc.get("publisher", [""])[0],
                    "description": "",
                    "barcode": doc.get("isbn", [None])[0],
                }
                for doc in documents
            ]
        except Exception:
            current_app.logger.exception("OpenLibrary search failed")
            return []


def query_book_metadata(title=None, author=None, isbn=None):
    """Iterate through metadata providers (starting from Google Books, then OpenLibrary) until results are found."""
    providers = [
        GoogleBooksMetadataProvider(),
        OpenLibraryMetadataProvider(),
    ]

    for provider in providers:
        try:
            results = provider.search(title=title, author=author, isbn=isbn)
            if results:
                return results
        except Exception:
            current_app.logger.exception(f"Metadata provider '{provider.name}' failed")

    return []
