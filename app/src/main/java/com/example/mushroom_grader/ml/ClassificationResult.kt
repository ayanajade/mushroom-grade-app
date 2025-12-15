package com.example.mushroom_grader.ml

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.mushroom_grader.database.Converters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "classification_results")
@TypeConverters(Converters::class)
data class ClassificationResult(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val className: String,
    val classId: Int,
    val confidence: Float,
    val category: MushroomCategory,
    val grade: String? = null,
    val isPoisonous: Boolean,
    val imagePath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String? = null
) {

    fun getConfidencePercentage(): String {
        return String.format(Locale.getDefault(), "%.2f%%", confidence * 100)
    }

    fun getFormattedTimestamp(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    // ✅ Helper to get display-friendly grade
    fun getDisplayGrade(): String {
        return grade ?: "N/A"
    }

    // ✅ Check if this is an Oyster mushroom
    fun isOysterMushroom(): Boolean {
        return className.contains("Oyster", ignoreCase = true)
    }

    // ✅ Get Oyster grade (A/B/C) or null
    fun getOysterGrade(): String? {
        return when {
            className.contains("Class A") -> "A"
            className.contains("Class B") -> "B"
            className.contains("Class C") -> "C"
            className.contains("Cluster") -> "Cluster"
            className.contains("Defective") -> "Defective"
            else -> null
        }
    }
}