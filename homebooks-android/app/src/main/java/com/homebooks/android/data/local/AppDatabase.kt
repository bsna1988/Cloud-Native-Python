package com.homebooks.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.homebooks.android.data.local.dao.BookDao
import com.homebooks.android.data.local.dao.PendingMutationDao
import com.homebooks.android.data.local.entity.BookEntity
import com.homebooks.android.data.local.entity.PendingMutationEntity

@Database(entities = [BookEntity::class, PendingMutationEntity::class], version = 2, exportSchema = false)
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
                ).addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE books ADD COLUMN needsReview INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
