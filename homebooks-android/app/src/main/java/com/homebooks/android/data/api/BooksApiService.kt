package com.homebooks.android.data.api

import com.homebooks.android.data.model.ApiResponse
import com.homebooks.android.data.model.RecognitionResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface BooksApiService {

    @GET("api/v1/books")
    suspend fun getBooks(): Response<ApiResponse>

    @GET("api/v1/books/{id}")
    suspend fun getBook(@Path("id") bookId: Int): Response<ApiResponse>

    @Multipart
    @POST("api/v1/books")
    suspend fun addBook(
        @Part("book") bookJson: RequestBody,
        @Part images: List<MultipartBody.Part>
    ): Response<ApiResponse>

    @Multipart
    @POST("api/v1/books/recognize")
    suspend fun recognizeImage(
        @Part images: List<MultipartBody.Part>
    ): Response<RecognitionResponse>

    @GET("uploads/{filename}")
    suspend fun getUploadedImage(@Path("filename") filename: String): Response<ByteArray>

    @DELETE("api/v1/books/{id}")
    suspend fun deleteBook(@Path("id") bookId: Int): Response<ApiResponse>

    @Multipart
    @PUT("api/v1/books/{id}")
    suspend fun updateBook(
        @Path("id") bookId: Int,
        @Part("book") bookJson: RequestBody,
        @Part images: List<MultipartBody.Part>,
        @Part("removed_images") removedImagesJson: RequestBody
    ): Response<ApiResponse>

    @GET("api/v1/books/barcode/{isbn}")
    suspend fun searchByBarcode(@Path("isbn") isbn: String): Response<RecognitionResponse>
}
