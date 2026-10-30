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
    fun searchesOpenLibraryWithoutBackendApiKeyAndMapsCandidates() {
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                assertFalse(request.headers.names().contains("X-API-Key"))
                requests += request.url.host
                val body = when (request.url.host) {
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
        assertEquals(listOf("openlibrary.org"), requests)
        val candidates = result.getOrThrow()
        assertEquals(1, candidates.size)
        assertEquals("Other author", candidates.first().author)
        assertEquals("0123456789", candidates.first().barcode)
        assertEquals("1998", candidates.first().releaseDate)
        assertEquals("Open Library", candidates.first().source)
    }

    @Test
    fun reportsFailureWhenOpenLibraryFails() {
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

        val result = CatalogMetadataClient(client).search(Book(title = "Existing title"))

        assertTrue(result.isFailure)
    }
}
