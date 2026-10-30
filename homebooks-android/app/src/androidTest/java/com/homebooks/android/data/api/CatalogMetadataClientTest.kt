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
    fun searchesBothPublicCatalogsWithoutBackendApiKeyAndMapsCandidates() {
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                assertFalse(request.headers.names().contains("X-API-Key"))
                requests += request.url.host
                val body = when (request.url.host) {
                    "www.googleapis.com" -> """
                        {"items":[{"volumeInfo":{
                          "title":"Catalog title",
                          "authors":["Catalog author"],
                          "publishedDate":"2024",
                          "description":"Google description",
                          "industryIdentifiers":[{"type":"ISBN_13","identifier":"9781234567890"}]
                        }}]}
                    """.trimIndent()
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

        val result = CatalogMetadataClient(client).search(Book(title = "Existing title"))

        assertTrue("Lookup failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertEquals(setOf("www.googleapis.com", "openlibrary.org"), requests.toSet())
        val candidates = result.getOrThrow()
        assertEquals(2, candidates.size)
        assertEquals("Catalog author", candidates.first().author)
        assertEquals("9781234567890", candidates.first().barcode)
        assertEquals("2024", candidates.first().releaseDate)
        assertEquals("Google Books", candidates.first().source)
        assertEquals("Open Library", candidates.last().source)
    }

    @Test
    fun reportsFailureWhenNoCandidatesAndAProviderFails() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (request.url.host == "www.googleapis.com") 503 else 200)
                    .message(if (request.url.host == "www.googleapis.com") "Unavailable" else "OK")
                    .body(
                        (if (request.url.host == "www.googleapis.com") "" else """{"docs":[]}""")
                            .toResponseBody()
                    )
                    .build()
            }
            .build()

        val result = CatalogMetadataClient(client).search(Book(title = "Existing title"))

        assertTrue(result.isFailure)
    }
}
