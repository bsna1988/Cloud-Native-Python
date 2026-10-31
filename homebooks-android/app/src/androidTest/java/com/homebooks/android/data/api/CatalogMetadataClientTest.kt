package com.homebooks.android.data.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homebooks.android.data.model.Book
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogMetadataClientTest {

    @Test
    fun searchesGoogleBooksAndOpenLibraryWithConfiguredGoogleApiKey() {
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                assertFalse(request.headers.names().contains("X-API-Key"))
                requests += request.url.host
                val body = when (request.url.host) {
                    "www.googleapis.com" -> {
                        assertEquals("test-google-books-key", request.url.queryParameter("key"))
                        assertEquals(
                            "109 RUE DES SOUPIRS FANTÔMES A DOMICILE",
                            request.url.queryParameter("q")
                        )
                        """
                            {"items":[{"volumeInfo":{"title":"Google title","authors":["Google author"],
                              "publishedDate":"2001","description":"A description",
                              "industryIdentifiers":[{"type":"ISBN_13","identifier":"9781234567890"}]}}]}
                        """.trimIndent()
                    }
                    "openlibrary.org" -> """
                        {"docs":[{"title":"Another title","author_name":["Other author"],
                          "first_publish_year":1998,"isbn":["0123456789"]}]}
                    """.trimIndent()
                    else -> error("Unexpected catalog host ${request.url.host}")
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val result = CatalogMetadataClient(
            httpClient = client,
            googleBooksApiKey = "test-google-books-key"
        ).search(
            Book(
                title = "109 RUE DES SOUPIRS FANTÔMES A DOMICILE",
                author = "Some author"
            )
        )

        assertTrue("Lookup failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertEquals(listOf("www.googleapis.com", "openlibrary.org"), requests)
        val candidates = result.getOrThrow()
        assertEquals(2, candidates.size)
        assertEquals("Google title", candidates[0].title)
        assertEquals("Google author", candidates[0].author)
        assertEquals("9781234567890", candidates[0].barcode)
        assertEquals("2001", candidates[0].releaseDate)
        assertEquals("Google Books", candidates[0].source)
        assertEquals("Another title", candidates[1].title)
        assertEquals("Other author", candidates[1].author)
        assertEquals("0123456789", candidates[1].barcode)
        assertEquals("1998", candidates[1].releaseDate)
        assertEquals("Open Library", candidates[1].source)
    }

    @Test
    fun returnsOpenLibraryCandidatesWhenGoogleBooksFails() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url.host == "www.googleapis.com") {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(429)
                        .message("Too Many Requests")
                        .body("".toResponseBody())
                        .build()
                } else {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"docs":[{"title":"Open Library title"}]}"""
                            .toResponseBody("application/json".toMediaType()))
                        .build()
                }
            }
            .build()

        val result = CatalogMetadataClient(client, "test-google-books-key")
            .search(Book(title = "Existing title"))

        assertTrue("Lookup failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertEquals("Open Library title", result.getOrThrow().single().title)
        assertEquals("Open Library", result.getOrThrow().single().source)
    }

    @Test
    fun reportsFailureWhenBothCatalogsFail() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Unavailable")
                    .body("".toResponseBody())
                    .build()
            }
            .build()

        val result = CatalogMetadataClient(client, "test-google-books-key")
            .search(Book(title = "Existing title"))

        assertTrue(result.isFailure)
    }
}
