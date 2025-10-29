package com.example.mushroom_grader.ml

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.mushroom_grader.database.Converters
import com.example.mushroom_grader.ml.MushroomCategory

@Entity(tableName = "classification_results")
@TypeConverters(Converters::class)
data class ClassificationResult(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // Mushroom identification
    val className: String,              // e.g., "Oyster Class A", "Amanita virosa"
    val classId: Int,                   // Index from YOLOv8 output
    val confidence: Float,              // 0.0 - 1.0

    // Classification details
    val category: MushroomCategory,     // EDIBLE, POISONOUS, INEDIBLE
    val grade: String? = null,          // For edible: "Class A", "Class B", "Class C", "Defective", null for others
    val isPoisonous: Boolean,           // Safety flag

    // Image data
    val imagePath: String? = null,      // Path to saved image

    // Metadata
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String? = null           // Optional user notes
) {
    // Helper functions
    fun getConfidencePercentage(): String = String.format("%.2f%%", confidence * 100)

    fun getFormattedTimestamp(): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    fun getSafetyLevel(): String {
        return when {
            isPoisonous -> "⚠️ POISONOUS - DO NOT CONSUME"
            category == MushroomCategory.INEDIBLE -> "⚠️ INEDIBLE - NOT RECOMMENDED"
            category == MushroomCategory.EDIBLE && grade != null -> "✓ Safe to consume - Grade: $grade"
            else -> "✓ Safe to consume"
        }
    }

    fun getQualityDescription(): String {
        return when (grade) {
            "Class A", "Extra" -> "Premium quality mushroom"
            "Class B", "Class I" -> "Good quality mushroom"
            "Class C", "Class II" -> "Fair quality mushroom"
            "Defective" -> "Defective - not recommended for sale"
            else -> if (isPoisonous) "Toxic mushroom" else "Identified mushroom"
        }
    }
}

