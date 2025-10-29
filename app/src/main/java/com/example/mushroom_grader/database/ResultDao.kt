package com.example.mushroom_grader.database

import androidx.room.*
import com.example.mushroom_grader.ml.ClassificationResult
import com.example.mushroom_grader.ml.MushroomCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ResultDao {
    // Insert operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ClassificationResult): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<ClassificationResult>)

    // Query all results (as Flow for real-time updates)
    @Query("SELECT * FROM classification_results ORDER BY timestamp DESC")
    fun getAllResultsFlow(): Flow<List<ClassificationResult>>

    // Query all results (one-time fetch)
    @Query("SELECT * FROM classification_results ORDER BY timestamp DESC")
    suspend fun getAllResults(): List<ClassificationResult>

    // Query by category
    @Query("SELECT * FROM classification_results WHERE category = :category ORDER BY timestamp DESC")
    suspend fun getResultsByCategory(category: MushroomCategory): List<ClassificationResult>

    // Query poisonous mushrooms only
    @Query("SELECT * FROM classification_results WHERE isPoisonous = 1 ORDER BY timestamp DESC")
    suspend fun getPoisonousResults(): List<ClassificationResult>

    // Query edible mushrooms only
    @Query("SELECT * FROM classification_results WHERE isPoisonous = 0 ORDER BY timestamp DESC")
    suspend fun getEdibleResults(): List<ClassificationResult>

    // Query by specific mushroom name
    @Query("SELECT * FROM classification_results WHERE className LIKE '%' || :name || '%' ORDER BY timestamp DESC")
    suspend fun searchByName(name: String): List<ClassificationResult>

    // Get result by ID
    @Query("SELECT * FROM classification_results WHERE id = :id")
    suspend fun getResultById(id: Int): ClassificationResult?

    // Get recent results (last N entries)
    @Query("SELECT * FROM classification_results ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentResults(limit: Int): List<ClassificationResult>

    // Get statistics
    @Query("SELECT COUNT(*) FROM classification_results")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM classification_results WHERE isPoisonous = 1")
    suspend fun getPoisonousCount(): Int

    @Query("SELECT COUNT(*) FROM classification_results WHERE isPoisonous = 0")
    suspend fun getEdibleCount(): Int

    // Update operations
    @Update
    suspend fun updateResult(result: ClassificationResult)

    // Delete operations
    @Delete
    suspend fun deleteResult(result: ClassificationResult)

    @Query("DELETE FROM classification_results WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM classification_results")
    suspend fun deleteAll()

    @Query("DELETE FROM classification_results WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
