package com.homebooks.android.data.repository

import android.content.Context
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.homebooks.android.data.api.ApiConstants
import com.homebooks.android.data.api.BooksApiService
import com.homebooks.android.data.local.AppDatabase
import com.homebooks.android.data.local.entity.BookEntity
import com.homebooks.android.data.local.entity.PendingMutationEntity
import com.homebooks.android.data.model.Book
import com.homebooks.android.data.model.RecognizedBook
import com.homebooks.android.data.worker.SyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID

class BooksRepository(
    private val apiService: BooksApiService,
    private val context: Context
) {

    private val db = AppDatabase.getInstance(context)
    private val bookDao = db.bookDao()
    private val pendingMutationDao = db.pendingMutationDao()
    private val gson = Gson()

    private val baseUrl: String = run {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.getString("api_base_url", ApiConstants.BASE_URL) ?: ApiConstants.BASE_URL
    }

    private fun addApiBaseToUrl(thumbnailPath: String?): String? {
        if (thumbnailPath.isNullOrBlank()) return null
        return if (thumbnailPath.startsWith("http")) thumbnailPath else "$baseUrl$thumbnailPath"
    }

    private fun entityToModel(entity: BookEntity): Book {
        val imageListType = object : TypeToken<List<String>>() {}.type
        val rawImages: List<String> = try {
            gson.fromJson(entity.imagesJson, imageListType)
        } catch (_: Exception) {
            emptyList()
        }
        return Book(
            id = entity.serverId ?: entity.uuid.hashCode(),
            title = entity.title,
            author = entity.author,
            barcode = entity.barcode,
            release_date = entity.releaseDate,
            description = entity.description,
            thumbnail = addApiBaseToUrl(entity.thumbnail),
            images = rawImages.mapNotNull { addApiBaseToUrl(it) }
        )
    }

    fun observeBooks(): Flow<List<Book>> {
        return bookDao.observeBooks().map { entities ->
            entities.map { entityToModel(it) }
        }
    }

    suspend fun getBooks(): Result<List<Book>> {
        return try {
            scheduleSync()
            val entities = bookDao.getBooksList()
            Result.success(entities.map { entityToModel(it) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBook(id: Int): Result<Book> {
        return try {
            val entity = bookDao.getBookByServerId(id) ?: bookDao.getBooksList().find { it.uuid.hashCode() == id || it.uuid == id.toString() }
            if (entity != null) {
                Result.success(entityToModel(entity))
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
            val uuid = UUID.randomUUID().toString()
            val imagesList = imageFiles.map { it.absolutePath }

            val entity = BookEntity(
                uuid = uuid,
                title = title,
                author = author,
                barcode = barcode,
                releaseDate = releaseDate,
                description = description,
                thumbnail = imagesList.firstOrNull(),
                imagesJson = gson.toJson(imagesList),
                syncState = "PENDING_CREATE"
            )
            bookDao.insertBook(entity)

            val payloadJson = JSONObject().apply {
                put("title", title)
                put("author", author)
                if (barcode != null && barcode.isNotBlank()) put("barcode", barcode)
                put("release_date", releaseDate)
                put("description", description)
            }.toString()

            val mutation = PendingMutationEntity(
                bookUuid = uuid,
                operationType = "CREATE",
                payloadJson = payloadJson,
                localImagePathsJson = gson.toJson(imagesList)
            )
            pendingMutationDao.insertMutation(mutation)

            scheduleSync()

            Result.success(entityToModel(entity))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateBook(
        id: Int,
        title: String,
        author: String,
        barcode: String?,
        releaseDate: String,
        description: String,
        imageFiles: List<File>,
        removedImages: List<String>
    ): Result<Book> {
        return try {
            val entity = bookDao.getBookByServerId(id) ?: bookDao.getBooksList().find { it.uuid.hashCode() == id || it.uuid == id.toString() }
            ?: throw Exception("Book not found")

            val currentImagesType = object : TypeToken<List<String>>() {}.type
            val currentImages: List<String> = try {
                gson.fromJson(entity.imagesJson, currentImagesType)
            } catch (_: Exception) {
                emptyList()
            }

            val newImagePaths = imageFiles.map { it.absolutePath }
            val updatedImages = (currentImages.filter { !removedImages.contains(it) }) + newImagePaths

            val updatedEntity = entity.copy(
                title = title,
                author = author,
                barcode = barcode,
                releaseDate = releaseDate,
                description = description,
                thumbnail = updatedImages.firstOrNull(),
                imagesJson = gson.toJson(updatedImages),
                syncState = if (entity.syncState == "PENDING_CREATE") "PENDING_CREATE" else "PENDING_UPDATE"
            )
            bookDao.updateBook(updatedEntity)

            val payloadJson = JSONObject().apply {
                put("title", title)
                put("author", author)
                if (barcode != null && barcode.isNotBlank()) put("barcode", barcode)
                put("release_date", releaseDate)
                put("description", description)
            }.toString()

            val existingMutations = pendingMutationDao.getMutationsForBook(entity.uuid)
            val createMutation = existingMutations.find { it.operationType == "CREATE" }
            if (createMutation != null) {
                val updatedMutation = createMutation.copy(
                    payloadJson = payloadJson,
                    localImagePathsJson = gson.toJson(updatedImages)
                )
                pendingMutationDao.insertMutation(updatedMutation)
            } else {
                val mutation = PendingMutationEntity(
                    bookUuid = entity.uuid,
                    operationType = "UPDATE",
                    payloadJson = payloadJson,
                    localImagePathsJson = gson.toJson(newImagePaths),
                    removedImagesJson = gson.toJson(removedImages)
                )
                pendingMutationDao.insertMutation(mutation)
            }

            scheduleSync()

            Result.success(entityToModel(updatedEntity))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBook(id: Int): Result<Unit> {
        return try {
            val entity = bookDao.getBookByServerId(id) ?: bookDao.getBooksList().find { it.uuid.hashCode() == id || it.uuid == id.toString() }
            ?: return Result.success(Unit)

            val mutations = pendingMutationDao.getMutationsForBook(entity.uuid)
            if (entity.syncState == "PENDING_CREATE" || mutations.any { it.operationType == "CREATE" }) {
                pendingMutationDao.deleteMutationsForBook(entity.uuid)
                bookDao.deleteBookByUuid(entity.uuid)
            } else {
                val updatedEntity = entity.copy(
                    isDeleted = true,
                    syncState = "PENDING_DELETE"
                )
                bookDao.updateBook(updatedEntity)

                pendingMutationDao.deleteMutationsForBook(entity.uuid)
                val mutation = PendingMutationEntity(
                    bookUuid = entity.uuid,
                    operationType = "DELETE",
                    payloadJson = "{}"
                )
                pendingMutationDao.insertMutation(mutation)
            }

            scheduleSync()

            Result.success(Unit)
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
                Result.failure(Exception(response.errorBody()?.string() ?: "Recognition failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recognizeShelf(imageFiles: List<File>): Result<List<RecognizedBook>> {
        return try {
            val imageParts = imageFiles.map { file ->
                val requestBody = file.asRequestBody("image/*".toMediaType())
                MultipartBody.Part.createFormData("images", file.name, requestBody)
            }
            val response = apiService.recognizeShelf(imageParts)
            if (response.isSuccessful) {
                Result.success(response.body()?.books ?: emptyList())
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Bookshelf recognition failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchByBarcode(isbn: String): Result<List<RecognizedBook>> {
        return try {
            val response = apiService.searchByBarcode(isbn)
            if (response.isSuccessful) {
                val books = response.body()?.books ?: emptyList()
                if (books.isEmpty()) {
                    Result.failure(Exception("Book not recognized"))
                } else {
                    Result.success(books)
                }
            } else {
                Result.failure(Exception("Book not recognized"))
            }
        } catch (_: Exception) {
            Result.failure(Exception("Book not recognized"))
        }
    }

    fun observePendingMutationsCount(): Flow<Int> {
        return pendingMutationDao.observeAllMutations().map { it.size }
    }

    fun triggerImmediateSync() {
        scheduleSync()
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "homebooks_sync_work",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
