package com.example.mushroom_grader.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.mushroom_grader.ml.ClassificationResult

@Dao
interface ResultDao {
    @Insert
    suspend fun insertResult(result: ClassificationResult)

    @Query("SELECT * FROM classification_results ORDER BY timestamp DESC")
    suspend fun getAllResults(): List<ClassificationResult>

    @Query("DELETE FROM classification_results")
    suspend fun deleteAll()
}