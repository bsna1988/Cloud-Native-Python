package com.homebooks.android.data.repository

import android.content.Context
import com.homebooks.android.data.api.BooksApiService
import com.homebooks.android.data.model.Book
import com.homebooks.android.data.model.RecognizedBook
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

class BooksRepository(
    private val apiService: BooksApiService,
    private val context: Context
) {

    private val baseUrl: String = run {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.getString("api_base_url", "http://192.168.178.33:5005/") ?: "http://192.168.178.33:5005/"
    }

    private fun transformBook(book: Book): Book {
        return book.copy(
            thumbnail = addApiBaseToUrl(book.thumbnail),
            images = book.images.mapNotNull { addApiBaseToUrl(it) }
        )
    }

    suspend fun getBooks(): Result<List<Book>> {
        return try {
            val response = apiService.getBooks()
            if (response.isSuccessful) {
                val books = response.body()?.books?.map { transformBook(it) } ?: emptyList()
                Result.success(books)
            } else {
                Result.failure(Exception("Failed to load books: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBook(id: Int): Result<Book> {
        return try {
            val response = apiService.getBook(id)
            if (response.isSuccessful) {
                val book = response.body()?.book?.let { transformBook(it) } ?: throw Exception("Book not found")
                Result.success(book)
            } else {
                Result.failure(Exception("Book not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addBook(
        title: String,
        author: String,
        barcode: String?,
        releaseDate: String,
        description: String,
        imageFiles: List<File>
    ): Result<Book> {
        return try {
            val bookJson = JSONObject().apply {
                put("title", title)
                put("author", author)
                if (barcode != null && barcode.isNotBlank()) put("barcode", barcode)
                put("release_date", releaseDate)
                put("description", description)
            }.toString()

            val jsonBody = bookJson.toRequestBody("application/json".toMediaType())

            val imageParts = imageFiles.map { file ->
                val requestBody = file.asRequestBody("image/*".toMediaType())
                MultipartBody.Part.createFormData("images", file.name, requestBody)
            }

            val response = apiService.addBook(jsonBody, imageParts)
            if (response.isSuccessful) {
                val book = response.body()?.book?.let { transformBook(it) } ?: throw Exception("Failed to create book")
                Result.success(book)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to create book"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recognizeImages(imageFiles: List<File>): Result<List<RecognizedBook>> {
        return try {
            val imageParts = imageFiles.map { file ->
                val requestBody = file.asRequestBody("image/*".toMediaType())
                MultipartBody.Part.createFormData("images", file.name, requestBody)
            }

            val response = apiService.recognizeImage(imageParts)
            if (response.isSuccessful) {
                Result.success(response.body()?.books ?: emptyList())
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to recognize image: ${response.code()}"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun addApiBaseToUrl(thumbnailPath: String?): String? {
        if (thumbnailPath.isNullOrBlank()) return null
        return if (thumbnailPath.startsWith("http")) thumbnailPath else "$baseUrl$thumbnailPath"
    }
}
