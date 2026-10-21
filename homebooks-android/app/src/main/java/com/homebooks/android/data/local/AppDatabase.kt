package com.homebooks.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.homebooks.android.data.local.dao.BookDao
import com.homebooks.android.data.local.dao.PendingMutationDao
import com.homebooks.android.data.local.entity.BookEntity
import com.homebooks.android.data.local.entity.PendingMutationEntity

@Database(entities = [BookEntity::class, PendingMutationEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun pendingMutationDao(): PendingMutationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "homebooks_local.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
