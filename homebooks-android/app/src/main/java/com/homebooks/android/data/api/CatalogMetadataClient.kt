package com.homebooks.android.data.api

import com.homebooks.android.data.model.Book
import com.homebooks.android.data.model.BookMetadataCandidate
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class CatalogMetadataClient(
    private val httpClient: OkHttpClient = defaultHttpClient
) {

    fun search(book: Book): Result<List<BookMetadataCandidate>> = try {
        if (book.title.isBlank() && book.author.isBlank() && book.barcode.isNullOrBlank()) {
            Result.success(emptyList())
        } else {
            val googleResult = runCatching { requestGoogleBooks(book) }
            val openLibraryResult = runCatching { requestOpenLibrary(book) }
            if (googleResult.isFailure && openLibraryResult.isFailure) {
                throw googleResult.exceptionOrNull()
                    ?: openLibraryResult.exceptionOrNull()
                    ?: IOException("Catalog lookup failed")
            }
            val candidates = (googleResult.getOrDefault(emptyList()) +
                openLibraryResult.getOrDefault(emptyList()))
                .distinctBy { candidate ->
                    listOf(
                        candidate.title.trim().lowercase(),
                        candidate.author.orEmpty().trim().lowercase(),
                        candidate.releaseDate.orEmpty().trim().lowercase()
                    ).joinToString("|")
                }
            if (candidates.isEmpty() &&
                (googleResult.isFailure || openLibraryResult.isFailure)
            ) {
                throw googleResult.exceptionOrNull()
                    ?: openLibraryResult.exceptionOrNull()
                    ?: IOException("Catalog lookup failed")
            }
            Result.success(candidates)
        }
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    private fun requestGoogleBooks(book: Book): List<BookMetadataCandidate> {
        val query = book.barcode?.takeIf(String::isNotBlank)?.let { "isbn:$it" }
            ?: buildList {
                if (book.title.isNotBlank()) add("intitle:${book.title}")
                if (book.author.isNotBlank()) add("inauthor:${book.author}")
            }.joinToString(" ")
        if (query.isBlank()) return emptyList()

        val url = HttpUrl.Builder()
            .scheme("https")
            .host("www.googleapis.com")
            .addPathSegments("books/v1/volumes")
            .addQueryParameter("q", query)
            .addQueryParameter("maxResults", "8")
            .addQueryParameter(
                "fields",
                "items(volumeInfo(title,authors,publishedDate,description,industryIdentifiers))"
            )
            .build()
        val json = execute(url)
        val items = json.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val info = items.optJSONObject(index)?.optJSONObject("volumeInfo")
                ?: return@mapNotNull null
            val title = info.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            val authors = info.optJSONArray("authors")
            val identifiers = info.optJSONArray("industryIdentifiers")
            BookMetadataCandidate(
                title = title,
                author = authors?.let { values ->
                    (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
                        .joinToString(", ").takeIf(String::isNotBlank)
                },
                barcode = identifiers?.let { values ->
                    (0 until values.length()).mapNotNull { values.optJSONObject(it) }
                        .firstNotNullOfOrNull { identifier ->
                            identifier.optString("identifier").takeIf {
                                identifier.optString("type") == "ISBN_13" ||
                                    identifier.optString("type") == "ISBN_10"
                            }
                        }
                },
                releaseDate = info.optString("publishedDate").takeIf(String::isNotBlank),
                description = info.optString("description").takeIf(String::isNotBlank),
                source = "Google Books"
            )
        }
    }

    private fun requestOpenLibrary(book: Book): List<BookMetadataCandidate> {
        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host("openlibrary.org")
            .addPathSegments("search.json")
            .addQueryParameter(
                "fields",
                "title,author_name,first_publish_year,isbn"
            )
            .addQueryParameter("limit", "8")
        val isbn = book.barcode?.takeIf(String::isNotBlank)
        if (isbn != null) {
            urlBuilder.addQueryParameter("isbn", isbn)
        } else {
            if (book.title.isNotBlank()) urlBuilder.addQueryParameter("title", book.title)
            if (book.author.isNotBlank()) urlBuilder.addQueryParameter("author", book.author)
        }

        val json = execute(urlBuilder.build())
        val docs = json.optJSONArray("docs") ?: return emptyList()
        return (0 until docs.length()).mapNotNull { index ->
            val document = docs.optJSONObject(index) ?: return@mapNotNull null
            val title = document.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            val authors = document.optJSONArray("author_name")
            val isbns = document.optJSONArray("isbn")
            BookMetadataCandidate(
                title = title,
                author = authors?.let { values ->
                    (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
                        .joinToString(", ").takeIf(String::isNotBlank)
                },
                barcode = isbns?.let { values ->
                    (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
                        .firstOrNull { it.length == 13 }
                        ?: (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
                            .firstOrNull()
                },
                releaseDate = document.optInt("first_publish_year")
                    .takeIf { it > 0 }?.toString(),
                source = "Open Library"
            )
        }
    }

    private fun execute(url: HttpUrl): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "HomeBooks Android")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Catalog request failed with HTTP ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IOException("Catalog returned an empty response")
            return JSONObject(body)
        }
    }

    private companion object {
        val defaultHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}
