package com.homebooks.android.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.homebooks.android.data.api.RetrofitClient
import com.homebooks.android.data.local.AppDatabase
import com.homebooks.android.data.local.entity.BookEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val db = AppDatabase.getInstance(context)
    private val bookDao = db.bookDao()
    private val pendingMutationDao = db.pendingMutationDao()
    private val apiService = RetrofitClient.createService()
    private val gson = Gson()

    override suspend fun doWork(): Result {
        return try {
            val mutations = pendingMutationDao.getAllMutations()
            for (mutation in mutations) {
                val book = bookDao.getBookByUuid(mutation.bookUuid)
                if (book == null && mutation.operationType != "DELETE") {
                    pendingMutationDao.deleteMutation(mutation)
                    continue
                }

                val success = when (mutation.operationType) {
                    "CREATE" -> syncCreate(mutation, book)
                    "UPDATE" -> syncUpdate(mutation, book)
                    "DELETE" -> syncDelete(mutation, book)
                    else -> true
                }

                if (!success) {
                    return Result.retry()
                }
            }

            syncAndReconcileServerLibrary()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private suspend fun syncCreate(mutation: com.homebooks.android.data.local.entity.PendingMutationEntity, book: BookEntity?): Boolean {
        if (book == null) {
            pendingMutationDao.deleteMutation(mutation)
            return true
        }

        try {
            val jsonObject = JSONObject(mutation.payloadJson).apply {
                put("idempotency_key", book.uuid)
            }
            val jsonBody = jsonObject.toString().toRequestBody("application/json".toMediaType())

            val localPathsType = object : TypeToken<List<String>>() {}.type
            val localPaths: List<String> = try {
                gson.fromJson(mutation.localImagePathsJson, localPathsType)
            } catch (_: Exception) {
                emptyList()
            }

            val imageParts = localPaths.mapNotNull { path ->
                val file = File(path)
                if (file.exists()) {
                    val requestBody = file.asRequestBody("image/*".toMediaType())
                    MultipartBody.Part.createFormData("images", file.name, requestBody)
                } else null
            }

            val response = apiService.addBook(jsonBody, imageParts)
            if (response.isSuccessful) {
                val serverBook = response.body()?.book
                if (serverBook != null && serverBook.id != null) {
                    val updatedEntity = book.copy(
                        serverId = serverBook.id,
                        syncState = "SYNCED",
                        thumbnail = serverBook.thumbnail,
                        imagesJson = gson.toJson(serverBook.images)
                    )
                    bookDao.updateBook(updatedEntity)
                    pendingMutationDao.deleteMutation(mutation)
                    return true
                }
            } else if (response.code() == 409 || response.code() == 201) {
                pendingMutationDao.deleteMutation(mutation)
                return true
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private suspend fun syncUpdate(mutation: com.homebooks.android.data.local.entity.PendingMutationEntity, book: BookEntity?): Boolean {
        if (book == null || book.serverId == null) {
            return false
        }

        try {
            val jsonObject = JSONObject(mutation.payloadJson)
            val jsonBody = jsonObject.toString().toRequestBody("application/json".toMediaType())

            val removedImagesType = object : TypeToken<List<String>>() {}.type
            val removedImages: List<String> = try {
                gson.fromJson(mutation.removedImagesJson, removedImagesType)
            } catch (_: Exception) {
                emptyList()
            }
            val removedImagesJsonBody = gson.toJson(removedImages).toRequestBody("application/json".toMediaType())

            val localPathsType = object : TypeToken<List<String>>() {}.type
            val localPaths: List<String> = try {
                gson.fromJson(mutation.localImagePathsJson, localPathsType)
            } catch (_: Exception) {
                emptyList()
            }

            val imageParts = localPaths.mapNotNull { path ->
                val file = File(path)
                if (file.exists()) {
                    val requestBody = file.asRequestBody("image/*".toMediaType())
                    MultipartBody.Part.createFormData("images", file.name, requestBody)
                } else null
            }

            val response = apiService.updateBook(book.serverId, jsonBody, imageParts, removedImagesJsonBody)
            if (response.isSuccessful) {
                val serverBook = response.body()?.book
                if (serverBook != null) {
                    val updatedEntity = book.copy(
                        syncState = "SYNCED",
                        thumbnail = serverBook.thumbnail,
                        imagesJson = gson.toJson(serverBook.images)
                    )
                    bookDao.updateBook(updatedEntity)
                } else {
                    bookDao.updateBook(book.copy(syncState = "SYNCED"))
                }
                pendingMutationDao.deleteMutation(mutation)
                return true
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private suspend fun syncDelete(mutation: com.homebooks.android.data.local.entity.PendingMutationEntity, book: BookEntity?): Boolean {
        try {
            if (book != null && book.serverId != null) {
                apiService.deleteBook(book.serverId)
            }
            if (book != null) {
                bookDao.deleteBookByUuid(book.uuid)
            }
            pendingMutationDao.deleteMutation(mutation)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private suspend fun syncAndReconcileServerLibrary() {
        try {
            val response = apiService.getBooks()
            if (response.isSuccessful) {
                val serverBooks = response.body()?.books ?: emptyList()
                for (serverBook in serverBooks) {
                    if (serverBook.id == null) continue
                    val existingByServerId = bookDao.getBookByServerId(serverBook.id)
                    if (existingByServerId != null) {
                        if (existingByServerId.syncState == "SYNCED") {
                            val updated = existingByServerId.copy(
                                title = serverBook.title,
                                author = serverBook.author,
                                barcode = serverBook.barcode,
                                releaseDate = serverBook.release_date,
                                description = serverBook.description,
                                thumbnail = serverBook.thumbnail,
                                imagesJson = gson.toJson(serverBook.images)
                            )
                            bookDao.updateBook(updated)
                        }
                    } else {
                        val newEntity = BookEntity(
                            uuid = java.util.UUID.randomUUID().toString(),
                            serverId = serverBook.id,
                            title = serverBook.title,
                            author = serverBook.author,
                            barcode = serverBook.barcode,
                            releaseDate = serverBook.release_date,
                            description = serverBook.description,
                            thumbnail = serverBook.thumbnail,
                            imagesJson = gson.toJson(serverBook.images),
                            syncState = "SYNCED"
                        )
                        bookDao.insertBook(newEntity)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
