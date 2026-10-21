package com.homebooks.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_mutations")
data class PendingMutationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookUuid: String,
    val operationType: String, // CREATE, UPDATE, DELETE
    val payloadJson: String,
    val localImagePathsJson: String = "[]",
    val removedImagesJson: String = "[]",
    val timestamp: Long = System.currentTimeMillis()
)
