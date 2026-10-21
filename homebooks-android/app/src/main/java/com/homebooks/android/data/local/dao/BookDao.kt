package com.homebooks.android.data.local.dao

import androidx.room.*
import com.homebooks.android.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE isDeleted = 0 ORDER BY title ASC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isDeleted = 0 ORDER BY title ASC")
    suspend fun getBooksList(): List<BookEntity>

    @Query("SELECT * FROM books WHERE uuid = :uuid")
    suspend fun getBookByUuid(uuid: String): BookEntity?

    @Query("SELECT * FROM books WHERE serverId = :serverId")
    suspend fun getBookByServerId(serverId: Int): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("DELETE FROM books WHERE uuid = :uuid")
    suspend fun deleteBookByUuid(uuid: String)
}
