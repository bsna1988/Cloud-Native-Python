import copy
import hashlib
import json
import queue
import time
from collections import OrderedDict
from concurrent.futures import Future, TimeoutError as FutureTimeoutError
from threading import Lock, RLock, Thread, get_ident

from limits import RateLimitItemPerSecond
from limits.storage import MemoryStorage
from limits.strategies import MovingWindowRateLimiter
import requests
from flask import current_app
from tenacity import (
    Retrying,
    retry_if_exception,
    stop_after_attempt,
    wait_exponential,
)

CACHE_TTL_SECONDS = 24 * 60 * 60
NEGATIVE_CACHE_TTL_SECONDS = 15 * 60
CACHE_MAX_ENTRIES = 1000
MAX_REQUEST_ATTEMPTS = 3
PROVIDER_QUEUE_MAX_SIZE = 50
PROVIDER_QUEUE_ENQUEUE_TIMEOUT_SECONDS = 1
PROVIDER_QUEUE_RESPONSE_TIMEOUT_SECONDS = 120
PROVIDER_REQUEST_INTERVAL_SECONDS = 1 / 3
PROVIDER_RATE_LIMIT = RateLimitItemPerSecond(1)

_metadata_results_cache = OrderedDict()
_metadata_results_cache_lock = RLock()
_provider_dispatcher = None
_provider_dispatcher_lock = Lock()


def _metadata_cache_key(title=None, author=None, isbn=None):
    query = {
        "title": (title or "").strip().casefold(),
        "author": (author or "").strip().casefold(),
        "isbn": (isbn or "").strip().casefold(),
    }
    encoded_query = json.dumps(query, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(encoded_query.encode("utf-8")).hexdigest()


def _get_cached_metadata(cache_key):
    now = time.monotonic()
    with _metadata_results_cache_lock:
        cached_entry = _metadata_results_cache.get(cache_key)
        if cached_entry is None:
            return None
        expires_at, results = cached_entry
        if expires_at <= now:
            del _metadata_results_cache[cache_key]
            return None
        _metadata_results_cache.move_to_end(cache_key)
        return copy.deepcopy(results)


def _cache_metadata(cache_key, results, ttl_seconds):
    expires_at = time.monotonic() + ttl_seconds
    with _metadata_results_cache_lock:
        _metadata_results_cache[cache_key] = (
            expires_at,
            copy.deepcopy(results),
        )
        _metadata_results_cache.move_to_end(cache_key)
        while len(_metadata_results_cache) > CACHE_MAX_ENTRIES:
            _metadata_results_cache.popitem(last=False)


class MetadataRequestQueueFullError(RuntimeError):
    pass


class _MetadataProviderDispatcher:
    def __init__(
        self,
        request_func=requests.get,
        queue_max_size=PROVIDER_QUEUE_MAX_SIZE,
        request_interval=PROVIDER_REQUEST_INTERVAL_SECONDS,
        response_timeout=PROVIDER_QUEUE_RESPONSE_TIMEOUT_SECONDS,
        rate_limit=PROVIDER_RATE_LIMIT,
    ):
        self._request_func = request_func
        self._request_interval = request_interval
        self._response_timeout = response_timeout
        self._requests = queue.Queue(maxsize=queue_max_size)
        self._rate_limit = rate_limit
        self._rate_limiter = MovingWindowRateLimiter(MemoryStorage())
        self._worker = Thread(
            target=self._dispatch_requests,
            name="metadata-provider-dispatcher",
            daemon=True,
        )
        self._worker.start()

    def get(self, url, params, rate_limit_key):
        response_future = Future()
        request = (url, params, rate_limit_key, response_future)
        try:
            self._requests.put(
                request,
                timeout=PROVIDER_QUEUE_ENQUEUE_TIMEOUT_SECONDS,
            )
        except queue.Full as error:
            raise MetadataRequestQueueFullError(
                "Metadata provider request queue is full"
            ) from error

        try:
            return response_future.result(timeout=self._response_timeout)
        except FutureTimeoutError as error:
            response_future.cancel()
            raise requests.Timeout(
                "Timed out waiting for metadata provider request queue"
            ) from error

    def shutdown(self):
        self._requests.put(None)
        self._worker.join(timeout=5)

    def _wait_until_request_allowed(self, rate_limit_key):
        while not self._rate_limiter.hit(self._rate_limit, rate_limit_key):
            stats = self._rate_limiter.get_window_stats(
                self._rate_limit,
                rate_limit_key,
            )
            time.sleep(max(stats.reset_time - time.time(), 0))

    def _dispatch_requests(self):
        next_request_at = 0.0
        while True:
            request = self._requests.get()
            try:
                if request is None:
                    return
                url, params, rate_limit_key, response_future = request
                if response_future.cancelled():
                    continue

                try:
                    delay = next_request_at - time.monotonic()
                    if delay > 0:
                        time.sleep(delay)
                    self._wait_until_request_allowed(rate_limit_key)
                    if not response_future.set_running_or_notify_cancel():
                        continue

                    next_request_at = (
                        time.monotonic() + self._request_interval
                    )
                    response = self._request_func(
                        url,
                        params=params,
                        timeout=30,
                    )
                    response_future.set_result(response)
                except Exception as error:
                    if not response_future.done():
                        response_future.set_exception(error)
            finally:
                self._requests.task_done()


def _get_provider_dispatcher():
    global _provider_dispatcher
    with _provider_dispatcher_lock:
        if _provider_dispatcher is None:
            _provider_dispatcher = _MetadataProviderDispatcher()
        return _provider_dispatcher


def _is_retryable_provider_error(error):
    if isinstance(error, requests.HTTPError):
        status_code = error.response.status_code if error.response else None
        return status_code == 429 or status_code is not None and status_code >= 500
    return isinstance(error, requests.RequestException)


def _get_provider_response(url, params):
    retrying = Retrying(
        retry=retry_if_exception(_is_retryable_provider_error),
        stop=stop_after_attempt(MAX_REQUEST_ATTEMPTS),
        wait=wait_exponential(multiplier=1, min=1, max=30),
        sleep=time.sleep,
        reraise=True,
    )
    for attempt in retrying:
        with attempt:
            response = _get_provider_dispatcher().get(
                url,
                params,
                rate_limit_key=f"thread-{get_ident()}",
            )
            response.raise_for_status()
            return response


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

        response = _get_provider_response(
            "https://www.googleapis.com/books/v1/volumes", params
        )
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

        response = _get_provider_response(
            "https://openlibrary.org/search.json", params
        )
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


def query_book_metadata(title=None, author=None, isbn=None):
    """Search metadata providers with a bounded process-local cache and shared request rate."""
    if not any((title, author, isbn)):
        return []

    cache_key = _metadata_cache_key(title=title, author=author, isbn=isbn)
    cached_results = _get_cached_metadata(cache_key)
    if cached_results is not None:
        return cached_results

    providers = [
        GoogleBooksMetadataProvider(),
        OpenLibraryMetadataProvider(),
    ]
    all_providers_succeeded = True

    for provider in providers:
        try:
            results = provider.search(title=title, author=author, isbn=isbn)
            if results:
                _cache_metadata(cache_key, results, CACHE_TTL_SECONDS)
                return results
        except Exception:
            current_app.logger.exception(f"Metadata provider '{provider.name}' failed")
            all_providers_succeeded = False

    if all_providers_succeeded:
        _cache_metadata(cache_key, [], NEGATIVE_CACHE_TTL_SECONDS)

    return []
