package com.homebooks.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val uuid: String,
    val serverId: Int? = null,
    val title: String,
    val author: String,
    val barcode: String? = null,
    val releaseDate: String = "",
    val description: String = "",
    val thumbnail: String? = null,
    val imagesJson: String = "[]",
    val syncState: String = "SYNCED",
    val isDeleted: Boolean = false,
    val needsReview: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
