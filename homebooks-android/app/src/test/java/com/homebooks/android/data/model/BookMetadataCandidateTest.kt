package com.homebooks.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BookMetadataCandidateTest {

    @Test
    fun incompleteBookIncludesAnyMissingSupportedMetadata() {
        val complete = Book(
            title = "Title",
            author = "Author",
            barcode = "9781234567890",
            release_date = "2020",
            description = "Description"
        )
        assertEquals(false, complete.isMetadataIncomplete())
        assertEquals(true, complete.copy(author = "").isMetadataIncomplete())
        assertEquals(true, complete.copy(author = "Unknown Author").isMetadataIncomplete())
        assertEquals(true, complete.copy(barcode = null).isMetadataIncomplete())
        assertEquals(true, complete.copy(description = "").isMetadataIncomplete())
        assertEquals(
            true,
            complete.copy(description = "Text recognized on-device. Verify title and author.")
                .isMetadataIncomplete()
        )
    }

    @Test
    fun candidateReplacesSuppliedFieldsAndPreservesMissingFieldsAndLibraryIdentity() {
        val original = Book(
            id = 42,
            title = "Old title",
            author = "Existing author",
            barcode = "old-isbn",
            release_date = "1990",
            description = "Existing description",
            thumbnail = "cover.jpg",
            images = listOf("cover.jpg", "back.jpg")
        )
        val updated = original.withMetadataCandidate(
            BookMetadataCandidate(
                title = "Catalog title",
                author = "Catalog author",
                releaseDate = "2024",
                source = "Google Books"
            )
        )

        assertEquals("Catalog title", updated.title)
        assertEquals("Catalog author", updated.author)
        assertEquals("old-isbn", updated.barcode)
        assertEquals("2024", updated.release_date)
        assertEquals("Existing description", updated.description)
        assertEquals(original.id, updated.id)
        assertEquals(original.thumbnail, updated.thumbnail)
        assertEquals(original.images, updated.images)
    }
}
