package com.homebooks.android.data.local.dao

import androidx.room.*
import com.homebooks.android.data.local.entity.PendingMutationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingMutationDao {
    @Query("SELECT * FROM pending_mutations ORDER BY timestamp ASC")
    suspend fun getAllMutations(): List<PendingMutationEntity>

    @Query("SELECT * FROM pending_mutations ORDER BY timestamp ASC")
    fun observeAllMutations(): Flow<List<PendingMutationEntity>>

    @Query("SELECT * FROM pending_mutations WHERE bookUuid = :bookUuid")
    suspend fun getMutationsForBook(bookUuid: String): List<PendingMutationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMutation(mutation: PendingMutationEntity): Long

    @Delete
    suspend fun deleteMutation(mutation: PendingMutationEntity)

    @Query("DELETE FROM pending_mutations WHERE id = :mutationId")
    suspend fun deleteMutationById(mutationId: Long)

    @Query("DELETE FROM pending_mutations WHERE bookUuid = :bookUuid")
    suspend fun deleteMutationsForBook(bookUuid: String)
}
