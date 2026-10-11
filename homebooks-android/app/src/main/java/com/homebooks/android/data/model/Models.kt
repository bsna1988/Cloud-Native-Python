package com.homebooks.android.data.model

data class Book(
    val id: Int? = null,
    val title: String = "",
    val author: String = "",
    val barcode: String? = null,
    val release_date: String = "",
    val description: String = "",
    val thumbnail: String? = null,
    val images: List<String> = emptyList()
)

data class ApiResponse(
    val books: List<Book> = emptyList(),
    val book: Book? = null,
    val status: String? = null,
    val error: String? = null
)

data class RecognizedBook(
    val title: String? = null,
    val author: String? = null,
    val barcode: String? = null,
    val release_date: String? = null,
    val description: String? = null
)

data class RecognitionResponse(
    val status: String? = null,
    val books: List<RecognizedBook> = emptyList(),
    val error: String? = null
)
