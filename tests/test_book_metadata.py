import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

import library_api.services.book_metadata as book_metadata
import requests
from limits import RateLimitItemPerSecond
from library_api import create_app
from library_api.services.book_metadata import (
    _MetadataProviderDispatcher,
    GoogleBooksMetadataProvider,
    _cache_metadata,
    _get_cached_metadata,
    _metadata_results_cache,
    _metadata_results_cache_lock,
    query_book_metadata,
)


class BookMetadataTests(unittest.TestCase):
    def setUp(self):
        with _metadata_results_cache_lock:
            _metadata_results_cache.clear()
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        root = Path(self.temp_dir.name)
        self.app = create_app({
            "DATABASE": str(root / "books.db"),
            "UPLOAD_FOLDER": str(root / "uploads"),
        })

    @staticmethod
    def provider_response(status_code, data, headers=None):
        response = Mock()
        response.status_code = status_code
        response.headers = headers or {}
        response.json.return_value = data
        if status_code >= 400:
            response.raise_for_status.side_effect = requests.HTTPError(
                f"HTTP {status_code}",
                response=response,
            )
        return response

    def make_request_dispatcher(self):
        def dispatch(url, params, rate_limit_key):
            return requests.get(url, params=params, timeout=30)

        return Mock(get=Mock(side_effect=dispatch))

    def test_reuses_cached_google_books_result(self):
        response = self.provider_response(200, {
            "items": [{
                "volumeInfo": {
                    "title": "Test Book",
                    "authors": ["Test Author"],
                    "industryIdentifiers": [{
                        "type": "ISBN_13",
                        "identifier": "9780000000001",
                    }],
                },
            }],
        })

        with (
            self.app.app_context(),
            patch(
                "library_api.services.book_metadata.requests.get",
                return_value=response,
            ) as get,
            patch(
                "library_api.services.book_metadata._get_provider_dispatcher",
                return_value=self.make_request_dispatcher(),
            ),
        ):
            first_result = query_book_metadata(isbn="9780000000001")
            cached_result = query_book_metadata(isbn="9780000000001")

        self.assertEqual(first_result[0]["title"], "Test Book")
        self.assertEqual(cached_result, first_result)
        get.assert_called_once()

    def test_retries_rate_limited_request_with_exponential_backoff(self):
        rate_limited = self.provider_response(
            429, {}, headers={"Retry-After": "3"}
        )
        success = self.provider_response(200, {"items": []})

        with (
            self.app.app_context(),
            patch(
                "library_api.services.book_metadata._get_provider_dispatcher"
            ) as get_dispatcher,
            patch("library_api.services.book_metadata.time.sleep") as sleep,
        ):
            get_dispatcher.return_value.get.side_effect = [
                rate_limited,
                success,
            ]
            results = GoogleBooksMetadataProvider().search(isbn="9780000000001")

        self.assertEqual(results, [])
        self.assertEqual(get_dispatcher.return_value.get.call_count, 2)
        sleep.assert_called_once_with(1)

    def test_dispatcher_sends_queued_requests_in_fifo_order(self):
        request_order = []
        dispatcher = _MetadataProviderDispatcher(
            request_func=lambda url, params, timeout: (
                request_order.append(params["id"]) or Mock()
            ),
            request_interval=0,
            rate_limit=RateLimitItemPerSecond(100),
        )
        self.addCleanup(dispatcher.shutdown)

        first = dispatcher.get("/provider", {"id": 1}, "test-thread")
        second = dispatcher.get("/provider", {"id": 2}, "test-thread")

        self.assertIsNotNone(first)
        self.assertIsNotNone(second)
        self.assertEqual(request_order, [1, 2])

    def test_dispatcher_paces_provider_requests(self):
        request_times = []
        dispatcher = _MetadataProviderDispatcher(
            request_func=lambda url, params, timeout: (
                request_times.append(time.monotonic()) or Mock()
            ),
            request_interval=0.03,
            rate_limit=RateLimitItemPerSecond(100),
        )
        self.addCleanup(dispatcher.shutdown)

        dispatcher.get("/provider", {}, "test-thread")
        dispatcher.get("/provider", {}, "test-thread")

        self.assertGreaterEqual(
            request_times[1] - request_times[0],
            0.025,
        )

    def test_caches_not_found_result_and_skips_providers_on_next_lookup(self):
        google_response = self.provider_response(200, {"items": []})
        openlibrary_response = self.provider_response(200, {"docs": []})

        with (
            self.app.app_context(),
            patch(
                "library_api.services.book_metadata.requests.get",
                side_effect=[google_response, openlibrary_response],
            ) as get,
            patch(
                "library_api.services.book_metadata._get_provider_dispatcher",
                return_value=self.make_request_dispatcher(),
            ) as get_dispatcher,
        ):
            self.assertEqual(
                query_book_metadata(isbn="9780000000001"),
                [],
            )
            self.assertEqual(
                query_book_metadata(isbn="9780000000001"),
                [],
            )

        self.assertEqual(get.call_count, 2)
        self.assertEqual(get_dispatcher.return_value.get.call_count, 2)

    def test_cache_evicts_oldest_entries_when_size_limit_is_reached(self):
        with patch("library_api.services.book_metadata.CACHE_MAX_ENTRIES", 2):
            for index in range(3):
                _cache_metadata(str(index), [{"title": str(index)}], 3600)

            self.assertEqual(len(_metadata_results_cache), 2)
            self.assertIsNone(_get_cached_metadata("0"))
            self.assertEqual(_get_cached_metadata("1"), [{"title": "1"}])
            self.assertEqual(_get_cached_metadata("2"), [{"title": "2"}])


if __name__ == "__main__":
    unittest.main()
