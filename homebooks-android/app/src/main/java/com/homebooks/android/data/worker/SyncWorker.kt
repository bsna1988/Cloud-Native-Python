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
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val baseUrl = prefs.getString("api_base_url", com.homebooks.android.data.api.ApiConstants.BASE_URL) ?: com.homebooks.android.data.api.ApiConstants.BASE_URL
    private val apiService = RetrofitClient.createService(baseUrl)
    private val gson = Gson()

    override suspend fun doWork(): Result {
        android.util.Log.d("SyncWorker", "Starting sync work with base URL: $baseUrl")
        return try {
            val mutations = pendingMutationDao.getAllMutations()
            android.util.Log.d("SyncWorker", "Found ${mutations.size} pending mutations to sync.")
            for (mutation in mutations) {
                val book = bookDao.getBookByUuid(mutation.bookUuid)
                if (book == null && mutation.operationType != "DELETE") {
                    android.util.Log.d("SyncWorker", "Book not found for mutation ${mutation.id}, deleting mutation.")
                    pendingMutationDao.deleteMutation(mutation)
                    continue
                }

                android.util.Log.d("SyncWorker", "Processing mutation type ${mutation.operationType} for book UUID ${mutation.bookUuid}")
                val success = when (mutation.operationType) {
                    "CREATE" -> syncCreate(mutation, book)
                    "UPDATE" -> syncUpdate(mutation, book)
                    "DELETE" -> syncDelete(mutation, book)
                    else -> true
                }

                if (!success) {
                    android.util.Log.w("SyncWorker", "Mutation ${mutation.operationType} failed, scheduling retry.")
                    return Result.retry()
                }
            }

            android.util.Log.d("SyncWorker", "Mutations synced successfully. Reconciling server library...")
            syncAndReconcileServerLibrary()
            android.util.Log.d("SyncWorker", "Sync work completed successfully.")
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            android.util.Log.w("SyncWorker", "Sync work cancelled", e)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Sync work encountered exception, retrying", e)
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
                    android.util.Log.d("SyncWorker", "CREATE successful for book UUID ${book.uuid}, serverId=${serverBook.id}")
                    return true
                } else {
                    android.util.Log.w("SyncWorker", "CREATE response successful but serverBook or serverId is null. Body: ${response.body()}")
                }
            } else if (response.code() == 409 || response.code() == 201) {
                android.util.Log.d("SyncWorker", "CREATE handled status code ${response.code()}, removing mutation.")
                pendingMutationDao.deleteMutation(mutation)
                return true
            } else {
                val errorBodyStr = response.errorBody()?.string() ?: "No error body"
                android.util.Log.e("SyncWorker", "CREATE failed with HTTP code ${response.code()}, error: $errorBodyStr")
            }
            return false
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("SyncWorker", "CREATE threw exception", e)
            e.printStackTrace()
            return false
        }
    }

    private suspend fun syncUpdate(mutation: com.homebooks.android.data.local.entity.PendingMutationEntity, book: BookEntity?): Boolean {
        if (book == null || book.serverId == null) {
            android.util.Log.w("SyncWorker", "UPDATE skipped: book or serverId is null")
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
                android.util.Log.d("SyncWorker", "UPDATE successful for book serverId=${book.serverId}")
                return true
            } else {
                val errorBodyStr = response.errorBody()?.string() ?: "No error body"
                android.util.Log.e("SyncWorker", "UPDATE failed with HTTP code ${response.code()}, error: $errorBodyStr")
            }
            return false
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("SyncWorker", "UPDATE threw exception", e)
            e.printStackTrace()
            return false
        }
    }

    private suspend fun syncDelete(mutation: com.homebooks.android.data.local.entity.PendingMutationEntity, book: BookEntity?): Boolean {
        try {
            if (book != null && book.serverId != null) {
                val response = apiService.deleteBook(book.serverId)
                if (!response.isSuccessful && response.code() != 404) {
                    val errorBodyStr = response.errorBody()?.string() ?: "No error body"
                    android.util.Log.e("SyncWorker", "DELETE failed with HTTP code ${response.code()}, error: $errorBodyStr")
                    return false
                }
            }
            if (book != null) {
                bookDao.deleteBookByUuid(book.uuid)
            }
            pendingMutationDao.deleteMutation(mutation)
            android.util.Log.d("SyncWorker", "DELETE successful for mutation ${mutation.id}")
            return true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("SyncWorker", "DELETE threw exception", e)
            e.printStackTrace()
            return false
        }
    }

    private suspend fun syncAndReconcileServerLibrary() {
        try {
            val response = apiService.getBooks()
            if (response.isSuccessful) {
                val serverBooks = response.body()?.books ?: emptyList()
                android.util.Log.d("SyncWorker", "Reconciliation fetched ${serverBooks.size} books from server.")
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
            } else {
                val errorBodyStr = response.errorBody()?.string() ?: "No error body"
                android.util.Log.e("SyncWorker", "Reconciliation getBooks failed with HTTP code ${response.code()}, error: $errorBodyStr")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("SyncWorker", "Reconciliation threw exception", e)
            e.printStackTrace()
        }
    }
}
