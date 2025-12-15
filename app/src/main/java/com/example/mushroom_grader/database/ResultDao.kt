package com.example.mushroom_grader.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.mushroom_grader.ml.ClassificationResult

@Dao
interface ResultDao {

    // ✅ Insert a single result
    @Insert
    suspend fun insertResult(result: ClassificationResult): Long

    // ✅ Insert multiple results
    @Insert
    suspend fun insertResults(results: List<ClassificationResult>): List<Long>

    // ✅ Get all results
    @Query("SELECT * FROM classification_results ORDER BY timestamp DESC")
    suspend fun getAllResults(): List<ClassificationResult>

    // ✅ Get result by ID
    @Query("SELECT * FROM classification_results WHERE id = :id")
    suspend fun getResultById(id: Int): ClassificationResult?

    // ✅ Delete a result
    @Delete
    suspend fun deleteResult(result: ClassificationResult)

    // ✅ Delete all results
    @Query("DELETE FROM classification_results")
    suspend fun deleteAllResults()

    // ✅ Get recent results (last N)
    @Query("SELECT * FROM classification_results ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentResults(limit: Int): List<ClassificationResult>

    // ✅ Search by class name
    @Query("SELECT * FROM classification_results WHERE className LIKE :query ORDER BY timestamp DESC")
    suspend fun searchByClassName(query: String): List<ClassificationResult>

    // ✅ Get count
    @Query("SELECT COUNT(*) FROM classification_results")
    suspend fun getCount(): Int

    // ✅ Get only poisonous
    @Query("SELECT * FROM classification_results WHERE isPoisonous = 1 ORDER BY timestamp DESC")
    suspend fun getPoisonousResults(): List<ClassificationResult>

    // ✅ Get only edible
    @Query("SELECT * FROM classification_results WHERE isPoisonous = 0 AND category != 'INEDIBLE' ORDER BY timestamp DESC")
    suspend fun getEdibleResults(): List<ClassificationResult>
}
