package com.example.mushroom_grader.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mushroom_grader.ml.ClassificationResult

@Dao
interface ResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ClassificationResult): Long

    @Query("SELECT * FROM classification_results ORDER BY timestamp DESC")
    suspend fun getAllResults(): List<ClassificationResult>

    @Delete
    suspend fun deleteResult(result: ClassificationResult)

    @Query("DELETE FROM classification_results")
    suspend fun deleteAllResults()
}
