package com.homebooks.android.data.api

import android.util.Log
import com.homebooks.android.BuildConfig
import com.homebooks.android.data.model.Book
import com.homebooks.android.data.model.BookMetadataCandidate
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class CatalogMetadataClient(
    private val httpClient: OkHttpClient = defaultHttpClient,
    private val googleBooksApiKey: String = BuildConfig.GOOGLE_BOOKS_API_KEY
) {

    fun search(book: Book): Result<List<BookMetadataCandidate>> = try {
        if (book.title.isBlank() && book.author.isBlank() && book.barcode.isNullOrBlank()) {
            Result.success(emptyList())
        } else {
            val googleBooksResult = runCatching { requestGoogleBooks(book) }
            val openLibraryResult = runCatching { requestOpenLibrary(book) }
            if (googleBooksResult.isFailure && openLibraryResult.isFailure) {
                val exception = googleBooksResult.exceptionOrNull()
                    ?: IOException("Google Books lookup failed")
                openLibraryResult.exceptionOrNull()?.let(exception::addSuppressed)
                throw exception
            }
            googleBooksResult.exceptionOrNull()?.let {
                Log.w(TAG, "Google Books lookup failed; returning Open Library results", it)
            }
            openLibraryResult.exceptionOrNull()?.let {
                Log.w(TAG, "Open Library lookup failed; returning Google Books results", it)
            }
            val candidates = googleBooksResult.getOrDefault(emptyList()) +
                openLibraryResult.getOrDefault(emptyList())
            val distinctCandidates = candidates
                .distinctBy { candidate ->
                    listOf(
                        candidate.title.trim().lowercase(),
                        candidate.author.orEmpty().trim().lowercase(),
                        candidate.releaseDate.orEmpty().trim().lowercase()
                    ).joinToString("|")
                }
            Result.success(distinctCandidates)
        }
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    private fun isValidAuthor(author: String): Boolean {
        if (author.isBlank()) return false
        val lower = author.trim().lowercase()
        return lower != "unknown" && lower != "unknown author" && lower != "unknown_author" &&
               lower != "n/a" && lower != "none" && lower != "null"
    }

    private fun requestGoogleBooks(book: Book): List<BookMetadataCandidate> {
        val query = book.barcode?.takeIf(String::isNotBlank)?.let { "isbn:$it" }
            ?: book.title.takeIf(String::isNotBlank)
            ?: book.author.takeIf(::isValidAuthor)
            ?: ""
        if (query.isBlank()) return emptyList()

        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host("www.googleapis.com")
            .addPathSegments("books/v1/volumes")
            .addQueryParameter("q", query)
            .addQueryParameter("maxResults", "8")
            .addQueryParameter(
                "fields",
                "items(volumeInfo(title,authors,publishedDate,description,industryIdentifiers,imageLinks))"
            )
        val hasApiKey = googleBooksApiKey.isNotBlank()
        if (hasApiKey) {
            urlBuilder.addQueryParameter("key", googleBooksApiKey)
        }
        val requestUrl = urlBuilder.build()
        val maskedUrl = if (hasApiKey) {
            requestUrl.toString().replace(googleBooksApiKey, "***")
        } else {
            requestUrl.toString()
        }
        Log.d(TAG, "Google Books API Request: query='$query', url=$maskedUrl")

        val json = try {
            execute(requestUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Google Books API Request Failed for query '$query': ${e.message}", e)
            throw e
        }

        val items = json.optJSONArray("items")
        val itemCount = items?.length() ?: 0
        Log.d(TAG, "Google Books API Success: query='$query' returned $itemCount candidates")

        if (items == null) return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val info = items.optJSONObject(index)?.optJSONObject("volumeInfo")
                ?: return@mapNotNull null
            val title = info.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            val authors = info.optJSONArray("authors")
            val identifiers = info.optJSONArray("industryIdentifiers")
            val imageLinks = info.optJSONObject("imageLinks")
            val rawThumbnail = imageLinks?.optString("thumbnail")
                ?: imageLinks?.optString("smallThumbnail")
            val cleanThumbnail = rawThumbnail?.replace("http://", "https://")

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
                thumbnail = cleanThumbnail,
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
                "title,author_name,first_publish_year,isbn,cover_i"
            )
            .addQueryParameter("limit", "8")
        val isbn = book.barcode?.takeIf(String::isNotBlank)
        if (isbn != null) {
            urlBuilder.addQueryParameter("isbn", isbn)
        } else {
            if (book.title.isNotBlank()) urlBuilder.addQueryParameter("title", book.title)
            if (isValidAuthor(book.author)) urlBuilder.addQueryParameter("author", book.author)
        }

        val json = execute(urlBuilder.build())
        val docs = json.optJSONArray("docs") ?: return emptyList()
        return (0 until docs.length()).mapNotNull { index ->
            val document = docs.optJSONObject(index) ?: return@mapNotNull null
            val title = document.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            val authors = document.optJSONArray("author_name")
            val isbns = document.optJSONArray("isbn")
            val coverId = document.optInt("cover_i").takeIf { it > 0 }
            val isbnForCover = isbns?.let { values ->
                (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }.firstOrNull()
            }
            val openLibraryCover = when {
                coverId != null -> "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
                isbnForCover != null -> "https://covers.openlibrary.org/b/isbn/$isbnForCover-L.jpg"
                else -> null
            }

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
                thumbnail = openLibraryCover,
                source = "Open Library"
            )
        }
    }

    private fun execute(url: HttpUrl, attemptsLeft: Int = 2): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "HomeBooks Android")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if ((response.code == 503 || response.code == 502 || response.code == 504) && attemptsLeft > 1) {
                        Log.w(TAG, "Transient HTTP ${response.code} from $url. Retrying...")
                        Thread.sleep(500)
                        return execute(url, attemptsLeft - 1)
                    }
                    throw IOException("Catalog request failed with HTTP ${response.code}")
                }
                val body = response.body?.string()
                    ?: throw IOException("Catalog returned an empty response")
                return JSONObject(body)
            }
        } catch (e: IOException) {
            if (attemptsLeft > 1 && e !is java.net.UnknownHostException) {
                Log.w(TAG, "Transient error from $url. Retrying...", e)
                Thread.sleep(500)
                return execute(url, attemptsLeft - 1)
            }
            throw e
        }
    }

    private companion object {
        const val TAG = "CatalogMetadataClient"

        val defaultHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}
