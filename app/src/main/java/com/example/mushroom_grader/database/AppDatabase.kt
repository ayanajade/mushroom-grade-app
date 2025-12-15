package com.example.mushroom_grader.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.mushroom_grader.ml.ClassificationResult

@Database(
    entities = [ClassificationResult::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun resultDao(): ResultDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mushroom_grader_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)  // ✅ Added parameter
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
