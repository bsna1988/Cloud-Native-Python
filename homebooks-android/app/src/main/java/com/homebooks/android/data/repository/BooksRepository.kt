package com.homebooks.android.data.repository

import android.content.Context
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.homebooks.android.data.api.CatalogMetadataClient
import com.homebooks.android.data.api.ApiConstants
import com.homebooks.android.data.api.BooksApiService
import com.homebooks.android.data.local.AppDatabase
import com.homebooks.android.data.local.entity.BookEntity
import com.homebooks.android.data.local.entity.PendingMutationEntity
import com.homebooks.android.data.model.Book
import com.homebooks.android.data.model.BookMetadataCandidate
import com.homebooks.android.data.model.RecognizedBook
import com.homebooks.android.data.model.withMetadataCandidate
import com.homebooks.android.data.worker.SyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val catalogMetadataClient = CatalogMetadataClient()

    init {
        scheduleSync()
    }

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
            images = rawImages.mapNotNull { addApiBaseToUrl(it) },
            needsReview = entity.needsReview
        )
    }

    fun observeBooks(): Flow<List<Book>> {
        return bookDao.observeBooks().map { entities ->
            entities.map { entityToModel(it) }
        }
    }

    suspend fun getBooks(): Result<List<Book>> {
        return try {
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

    suspend fun markBookReviewed(id: Int): Result<Unit> {
        return try {
            val entity = bookDao.getBookByServerId(id)
                ?: bookDao.getBooksList().find {
                    it.uuid.hashCode() == id || it.uuid == id.toString()
                }
                ?: throw Exception("Book not found")

            if (entity.needsReview) {
                bookDao.updateBook(entity.copy(needsReview = false))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findMetadataCandidates(book: Book): Result<List<BookMetadataCandidate>> =
        withContext(Dispatchers.IO) {
            catalogMetadataClient.search(book)
        }

    suspend fun acceptMetadataCandidate(
        book: Book,
        candidate: BookMetadataCandidate
    ): Result<Book> {
        val id = book.id
            ?: return Result.failure(IllegalArgumentException("Book has no library ID"))
        val updatedBook = book.withMetadataCandidate(candidate)
        val remoteUrls = listOfNotNull(candidate.thumbnail).filter(String::isNotBlank)
        return updateBook(
            id = id,
            title = updatedBook.title,
            author = updatedBook.author,
            barcode = updatedBook.barcode,
            releaseDate = updatedBook.release_date,
            description = updatedBook.description,
            imageFiles = emptyList(),
            removedImages = emptyList(),
            remoteImageUrls = remoteUrls
        )
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
                syncState = "PENDING_CREATE",
                needsReview = true
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
        removedImages: List<String>,
        remoteImageUrls: List<String> = emptyList()
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
            val filteredCurrent = currentImages.filter { !removedImages.contains(it) }
            val newRemoteUrls = remoteImageUrls.filter { !filteredCurrent.contains(it) }
            val updatedImages = filteredCurrent + newImagePaths + newRemoteUrls

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
                val remoteUrls = updatedImages.filter { it.startsWith("http://") || it.startsWith("https://") }
                if (remoteUrls.isNotEmpty()) {
                    val jsonArray = org.json.JSONArray()
                    remoteUrls.forEach { jsonArray.put(it) }
                    put("image_urls", jsonArray)
                }
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
                    localImagePathsJson = gson.toJson(updatedImages),
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
        return withContext(Dispatchers.IO) {
            com.homebooks.android.util.OnDeviceRecognitionEngine.recognizeImagesLocally(context, imageFiles)
        }
    }

    suspend fun recognizeShelf(imageFiles: List<File>): Result<List<RecognizedBook>> {
        return withContext(Dispatchers.IO) {
            com.homebooks.android.util.OnDeviceRecognitionEngine.recognizeShelfLocally(context, imageFiles)
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
        android.util.Log.d("BooksRepository", "Sync Now clicked! Forcing immediate sync enqueue.")
        scheduleSync()
    }

    private fun scheduleSync() {
        android.util.Log.d("BooksRepository", "Scheduling sync work request with REPLACE policy.")
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
