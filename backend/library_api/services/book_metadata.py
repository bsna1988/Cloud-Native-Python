import copy
import hashlib
import json
import logging
import queue
import time
from collections import OrderedDict
from concurrent.futures import Future, TimeoutError as FutureTimeoutError
from threading import Lock, RLock, Thread, get_ident
from urllib.parse import urlparse

from limits import RateLimitItemPerSecond
from limits.storage import MemoryStorage
from limits.strategies import MovingWindowRateLimiter
import requests
from tenacity import (
    Retrying,
    retry_if_exception,
    stop_after_attempt,
    wait_exponential,
)

logger = logging.getLogger(__name__)

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
    cache_key = hashlib.sha256(encoded_query.encode("utf-8")).hexdigest()
    logger.debug("Generated metadata cache key")
    return cache_key


def _get_cached_metadata(cache_key):
    now = time.monotonic()
    with _metadata_results_cache_lock:
        cached_entry = _metadata_results_cache.get(cache_key)
        if cached_entry is None:
            logger.debug("Metadata cache miss")
            return None
        expires_at, results = cached_entry
        if expires_at <= now:
            del _metadata_results_cache[cache_key]
            logger.debug("Expired metadata cache entry removed")
            return None
        _metadata_results_cache.move_to_end(cache_key)
        logger.info("Metadata cache hit")
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
        logger.debug(
            "Cached metadata result (entries=%d, ttl_seconds=%d)",
            len(_metadata_results_cache),
            ttl_seconds,
        )


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
        logger.info(
            "Started metadata provider dispatcher (queue_capacity=%d)",
            queue_max_size,
        )

    def get(self, url, params, rate_limit_key):
        host = urlparse(url).hostname or "unknown"
        response_future = Future()
        request = (url, params, rate_limit_key, response_future)
        queued_at = time.monotonic()
        try:
            self._requests.put(
                request,
                timeout=PROVIDER_QUEUE_ENQUEUE_TIMEOUT_SECONDS,
            )
        except queue.Full as error:
            logger.warning(
                "Metadata provider queue is full; rejecting request (provider=%s)",
                host,
            )
            raise MetadataRequestQueueFullError(
                "Metadata provider request queue is full"
            ) from error

        logger.debug(
            "Queued metadata provider request (provider=%s, queue_size=%d)",
            host,
            self._requests.qsize(),
        )
        try:
            response = response_future.result(timeout=self._response_timeout)
            logger.debug(
                "Metadata provider request completed (provider=%s, "
                "elapsed_seconds=%.3f)",
                host,
                time.monotonic() - queued_at,
            )
            return response
        except FutureTimeoutError as error:
            response_future.cancel()
            logger.error(
                "Timed out waiting for metadata provider queue "
                "(provider=%s, timeout_seconds=%d)",
                host,
                self._response_timeout,
            )
            raise requests.Timeout(
                "Timed out waiting for metadata provider request queue"
            ) from error

    def shutdown(self):
        logger.info("Stopping metadata provider dispatcher")
        self._requests.put(None)
        self._worker.join(timeout=5)
        if self._worker.is_alive():
            logger.warning("Metadata provider dispatcher did not stop in time")
        else:
            logger.info("Metadata provider dispatcher stopped")

    def _wait_until_request_allowed(self, rate_limit_key):
        while not self._rate_limiter.hit(self._rate_limit, rate_limit_key):
            stats = self._rate_limiter.get_window_stats(
                self._rate_limit,
                rate_limit_key,
            )
            delay = max(stats.reset_time - time.time(), 0)
            logger.debug(
                "Waiting for metadata provider rate limit (delay_seconds=%.3f)",
                delay,
            )
            time.sleep(delay)

    def _dispatch_requests(self):
        logger.debug("Metadata provider dispatcher thread is ready")
        next_request_at = 0.0
        while True:
            request = self._requests.get()
            try:
                if request is None:
                    logger.debug("Received metadata dispatcher stop signal")
                    return
                url, params, rate_limit_key, response_future = request
                host = urlparse(url).hostname or "unknown"
                if response_future.cancelled():
                    logger.debug(
                        "Skipping cancelled metadata request (provider=%s)",
                        host,
                    )
                    continue

                try:
                    delay = next_request_at - time.monotonic()
                    if delay > 0:
                        logger.debug(
                            "Pacing metadata provider request "
                            "(provider=%s, delay_seconds=%.3f)",
                            host,
                            delay,
                        )
                        time.sleep(delay)
                    self._wait_until_request_allowed(rate_limit_key)
                    if not response_future.set_running_or_notify_cancel():
                        logger.debug(
                            "Metadata request cancelled before dispatch "
                            "(provider=%s)",
                            host,
                        )
                        continue

                    next_request_at = (
                        time.monotonic() + self._request_interval
                    )
                    request_started_at = time.monotonic()
                    logger.info(
                        "Sending metadata provider request (provider=%s)",
                        host,
                    )
                    response = self._request_func(
                        url,
                        params=params,
                        timeout=30,
                    )
                    response_future.set_result(response)
                    logger.info(
                        "Metadata provider responded "
                        "(provider=%s, status=%d, duration_seconds=%.3f)",
                        host,
                        response.status_code,
                        time.monotonic() - request_started_at,
                    )
                except Exception as error:
                    logger.warning(
                        "Metadata provider request failed "
                        "(provider=%s, error_type=%s)",
                        host,
                        type(error).__name__,
                    )
                    if not response_future.done():
                        response_future.set_exception(error)
            finally:
                self._requests.task_done()


def _get_provider_dispatcher():
    global _provider_dispatcher
    with _provider_dispatcher_lock:
        if _provider_dispatcher is None:
            _provider_dispatcher = _MetadataProviderDispatcher()
            logger.info("Initialized metadata provider dispatcher")
        return _provider_dispatcher


def _is_retryable_provider_error(error):
    if isinstance(error, requests.HTTPError):
        status_code = error.response.status_code if error.response else None
        retryable = (
            status_code == 429
            or status_code is not None and status_code >= 500
        )
    else:
        retryable = isinstance(error, requests.RequestException)
    logger.debug(
        "Checked metadata provider retryability (error_type=%s, retryable=%s)",
        type(error).__name__,
        retryable,
    )
    return retryable


def _log_provider_retry(retry_state):
    error = retry_state.outcome.exception()
    logger.warning(
        "Retrying metadata provider request "
        "(attempt=%d, next_wait_seconds=%.1f, error_type=%s)",
        retry_state.attempt_number,
        retry_state.next_action.sleep,
        type(error).__name__,
    )


def _get_provider_response(url, params):
    host = urlparse(url).hostname or "unknown"
    logger.debug("Starting metadata provider request (provider=%s)", host)
    retrying = Retrying(
        retry=retry_if_exception(_is_retryable_provider_error),
        stop=stop_after_attempt(MAX_REQUEST_ATTEMPTS),
        wait=wait_exponential(multiplier=1, min=1, max=30),
        before_sleep=_log_provider_retry,
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
            logger.debug(
                "Metadata provider response accepted (provider=%s, status=%d)",
                host,
                response.status_code,
            )
            return response


class MetadataProvider:
    name = "base"

    def search(self, title=None, author=None, isbn=None):
        logger.debug("Base metadata provider search is not implemented")
        raise NotImplementedError


class GoogleBooksMetadataProvider(MetadataProvider):
    name = "google_books"

    def search(self, title=None, author=None, isbn=None):
        logger.debug("Searching metadata provider (provider=%s)", self.name)
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
            logger.info("Skipping Google Books search without query")
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
        logger.info(
            "Google Books search completed (result_count=%d)",
            len(books),
        )
        return books


class OpenLibraryMetadataProvider(MetadataProvider):
    name = "open_library"

    def search(self, title=None, author=None, isbn=None):
        logger.debug("Searching metadata provider (provider=%s)", self.name)
        params = {"limit": 5}
        if isbn:
            params["isbn"] = isbn
        elif title:
            params["title"] = title
        else:
            logger.info("Skipping OpenLibrary search without query")
            return []

        if author and not isbn:
            params["author"] = author

        response = _get_provider_response(
            "https://openlibrary.org/search.json", params
        )
        documents = response.json().get("docs", [])
        books = [
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
        logger.info(
            "OpenLibrary search completed (result_count=%d)",
            len(books),
        )
        return books


def query_book_metadata(title=None, author=None, isbn=None):
    """Search metadata providers with a bounded process-local cache and shared request rate."""
    if not any((title, author, isbn)):
        logger.info("Skipping metadata lookup without search criteria")
        return []

    lookup_started_at = time.monotonic()
    logger.info("Starting metadata lookup")
    cache_key = _metadata_cache_key(title=title, author=author, isbn=isbn)
    cached_results = _get_cached_metadata(cache_key)
    if cached_results is not None:
        logger.info(
            "Returning cached metadata result (result_count=%d)",
            len(cached_results),
        )
        return cached_results

    providers = [
        GoogleBooksMetadataProvider(),
        OpenLibraryMetadataProvider(),
    ]
    all_providers_succeeded = True

    for provider in providers:
        logger.debug("Trying metadata provider (provider=%s)", provider.name)
        try:
            results = provider.search(title=title, author=author, isbn=isbn)
            if results:
                _cache_metadata(cache_key, results, CACHE_TTL_SECONDS)
                logger.info(
                    "Metadata lookup succeeded (provider=%s, result_count=%d, "
                    "duration_seconds=%.3f)",
                    provider.name,
                    len(results),
                    time.monotonic() - lookup_started_at,
                )
                return results
            logger.info(
                "Metadata provider returned no matches (provider=%s)",
                provider.name,
            )
        except Exception as error:
            status_code = (
                error.response.status_code
                if isinstance(error, requests.HTTPError) and error.response
                else None
            )
            logger.warning(
                "Metadata provider failed; trying fallback "
                "(provider=%s, error_type=%s, status_code=%s)",
                provider.name,
                type(error).__name__,
                status_code,
            )
            all_providers_succeeded = False

    if all_providers_succeeded:
        _cache_metadata(cache_key, [], NEGATIVE_CACHE_TTL_SECONDS)
        logger.info(
            "No metadata found; cached negative result "
            "(ttl_seconds=%d, duration_seconds=%.3f)",
            NEGATIVE_CACHE_TTL_SECONDS,
            time.monotonic() - lookup_started_at,
        )
    else:
        logger.warning(
            "Metadata lookup incomplete due to provider failure; "
            "not caching result (duration_seconds=%.3f)",
            time.monotonic() - lookup_started_at,
        )

    return []
