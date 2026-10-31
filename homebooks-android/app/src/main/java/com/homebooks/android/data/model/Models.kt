package com.homebooks.android.data.model

data class Book(
    val id: Int? = null,
    val title: String = "",
    val author: String = "",
    val barcode: String? = null,
    val release_date: String = "",
    val description: String = "",
    val thumbnail: String? = null,
    val images: List<String> = emptyList(),
    @Transient val needsReview: Boolean = false
)

data class ApiResponse(
    val books: List<Book> = emptyList(),
    val book: Book? = null,
    val status: String? = null,
    val error: String? = null
)

data class RecognizedBook(
    val title: String? = null,
    val raw_title: String? = null,
    val author: String? = null,
    val barcode: String? = null,
    val release_date: String? = null,
    val description: String? = null,
    val thumbnail: String? = null
)

data class RecognitionResponse(
    val status: String? = null,
    val books: List<RecognizedBook> = emptyList(),
    val error: String? = null
)

data class BookMetadataCandidate(
    val title: String,
    val author: String? = null,
    val barcode: String? = null,
    val releaseDate: String? = null,
    val description: String? = null,
    val thumbnail: String? = null,
    val source: String
)

fun Book.isMetadataIncomplete(): Boolean =
    title.isMissingMetadata() ||
        author.isMissingMetadata() ||
        barcode.isNullOrMissingMetadata() ||
        release_date.isMissingMetadata() ||
        description.isMissingMetadata()

private fun String.isMissingMetadata(): Boolean {
    val normalized = trim()
    return normalized.isBlank() ||
        normalized.equals("unknown", ignoreCase = true) ||
        normalized.equals("unknown author", ignoreCase = true) ||
        normalized.equals("n/a", ignoreCase = true) ||
        normalized.equals("not available", ignoreCase = true) ||
        normalized.equals(
            "Text recognized on-device. Verify title and author.",
            ignoreCase = true
        ) ||
        normalized.equals("Barcode recognized on-device.", ignoreCase = true)
}

private fun String?.isNullOrMissingMetadata(): Boolean =
    this == null || isMissingMetadata()

fun Book.withMetadataCandidate(candidate: BookMetadataCandidate): Book = copy(
    title = candidate.title.ifBlank { title },
    author = candidate.author?.takeIf(String::isNotBlank) ?: author,
    barcode = candidate.barcode?.takeIf(String::isNotBlank) ?: barcode,
    release_date = candidate.releaseDate?.takeIf(String::isNotBlank) ?: release_date,
    description = candidate.description?.takeIf(String::isNotBlank) ?: description,
    thumbnail = candidate.thumbnail?.takeIf(String::isNotBlank) ?: thumbnail,
    images = if (!candidate.thumbnail.isNullOrBlank() && !images.contains(candidate.thumbnail)) images + candidate.thumbnail else images
)
