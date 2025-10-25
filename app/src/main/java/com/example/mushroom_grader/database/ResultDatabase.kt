package com.example.mushroom_grader.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ClassificationResult::class], version = 1)
abstract class ResultDatabase : RoomDatabase() {
    abstract fun resultDao(): ResultDao
}