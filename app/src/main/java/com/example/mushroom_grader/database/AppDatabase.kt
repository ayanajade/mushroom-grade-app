package com.example.mushroom_grader.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.mushroom_grader.ml.ClassificationResult

@Database(entities = [ClassificationResult::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun resultDao(): ResultDao

    companion object {
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mushroom_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}