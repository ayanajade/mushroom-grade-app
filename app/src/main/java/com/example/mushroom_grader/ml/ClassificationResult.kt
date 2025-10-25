package com.example.mushroom_grader.ml

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "classification_results")
data class ClassificationResult(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val classId: Int,
    val confidence: Float,
    val timestamp: Long
) {
    fun getConfidencePercentage() = String.format("%.2f", confidence * 100)
}

