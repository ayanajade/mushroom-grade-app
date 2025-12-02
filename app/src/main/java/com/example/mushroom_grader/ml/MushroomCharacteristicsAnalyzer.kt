package com.example.mushroom_grader.ml

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.get
import androidx.core.graphics.scale

/**
 * MushroomCharacteristicsAnalyzer - Analyzes actual visual characteristics from image
 *
 * Generates accurate descriptions based on what's actually visible in the photo:
 * - Size estimation (small, medium, large)
 * - Color description
 * - Surface condition
 * - Visual defects
 * - Physical appearance
 */
class MushroomCharacteristicsAnalyzer {

    companion object {
        private const val TAG = "CharacteristicsAnalyzer"
    }

    /**
     * Analyze mushroom visual characteristics from image
     */
    fun analyzeCharacteristics(
        bitmap: Bitmap,
        className: String,
        grade: String?
    ): MushroomCharacteristics {
        Log.d(TAG, "🔍 Analyzing characteristics for: $className")

        try {
            val size = estimateSize(bitmap)
            val colors = analyzeColors(bitmap)
            val surfaceCondition = analyzeSurfaceCondition(bitmap)
            val defects = detectDefects(bitmap, grade)
            val description = generateDescription(className, grade, size, colors, surfaceCondition, defects)

            return MushroomCharacteristics(
                estimatedSize = size,
                dominantColors = colors,
                surfaceCondition = surfaceCondition,
                visualDefects = defects,
                description = description
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error analyzing characteristics", e)
            return getDefaultCharacteristics(className, grade)
        }
    }

    /**
     * ✅ FIXED: Estimate mushroom size from image (removed unused width/height variables)
     */
    private fun estimateSize(bitmap: Bitmap): String {
        val scaled = bitmap.scale(200, 200, filter = true)

        // Count non-background pixels (rough mushroom area estimation)
        var mushroomPixels = 0
        var totalPixels = 0

        for (x in 0 until 200) {
            for (y in 0 until 200) {
                val pixel = scaled[x, y]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val brightness = (r + g + b) / 3

                // Not pure white/black background
                if (brightness in 30..230) {
                    mushroomPixels++
                }
                totalPixels++
            }
        }

        scaled.recycle()

        val coverage = mushroomPixels.toFloat() / totalPixels

        return when {
            coverage > 0.6 -> "Large" // Takes up most of frame
            coverage > 0.35 -> "Medium" // Moderate coverage
            else -> "Small" // Small coverage
        }
    }

    /**
     * Analyze dominant colors in mushroom
     */
    private fun analyzeColors(bitmap: Bitmap): List<String> {
        val scaled = bitmap.scale(100, 100, filter = true)

        var whitePixels = 0
        var creamPixels = 0
        var brownPixels = 0
        var darkPixels = 0
        var yellowPixels = 0
        var goldPixels = 0

        for (x in 0 until 100) {
            for (y in 0 until 100) {
                val pixel = scaled[x, y]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                when {
                    // White/Cream
                    r > 200 && g > 200 && b > 180 -> whitePixels++
                    r > 180 && g > 160 && b > 120 -> creamPixels++

                    // Brown/Dark
                    r in 80..150 && g in 60..120 && b in 40..100 -> brownPixels++
                    r < 80 && g < 80 && b < 80 -> darkPixels++

                    // Yellow/Gold (oyster mushrooms)
                    r > 180 && g > 150 && b < 120 && r > g -> goldPixels++
                    r > 200 && g > 180 && b < 150 -> yellowPixels++
                }
            }
        }

        scaled.recycle()

        val colors = mutableListOf<String>()
        val total = 10000

        if (whitePixels > total * 0.15) colors.add("White")
        if (creamPixels > total * 0.15) colors.add("Cream")
        if (brownPixels > total * 0.15) colors.add("Brown")
        if (darkPixels > total * 0.10) colors.add("Dark")
        if (goldPixels > total * 0.10) colors.add("Golden")
        if (yellowPixels > total * 0.10) colors.add("Yellow")

        return if (colors.isEmpty()) listOf("Light colored") else colors
    }

    /**
     * Analyze surface condition
     */
    private fun analyzeSurfaceCondition(bitmap: Bitmap): String {
        val scaled = bitmap.scale(100, 100, filter = true)

        var smoothPixels = 0
        var roughPixels = 0
        var spotPixels = 0

        for (x in 1 until 99) {
            for (y in 1 until 99) {
                val center = scaled[x, y]
                val centerBrightness = ((center shr 16) and 0xFF) +
                        ((center shr 8) and 0xFF) +
                        (center and 0xFF)

                // Check neighbors for texture
                val right = scaled[x + 1, y]
                val rightBrightness = ((right shr 16) and 0xFF) +
                        ((right shr 8) and 0xFF) +
                        (right and 0xFF)

                val variance = kotlin.math.abs(centerBrightness - rightBrightness)

                when {
                    variance < 30 -> smoothPixels++
                    variance > 80 -> roughPixels++
                }

                // Check for spots
                if (centerBrightness < 100) spotPixels++
            }
        }

        scaled.recycle()

        val total = 98 * 98
        return when {
            smoothPixels > total * 0.6 -> "Smooth"
            roughPixels > total * 0.3 -> "Textured"
            spotPixels > total * 0.2 -> "Spotted"
            else -> "Firm"
        }
    }

    /**
     * Detect visual defects
     */
    private fun detectDefects(bitmap: Bitmap, grade: String?): List<String> {
        val defects = mutableListOf<String>()
        val scaled = bitmap.scale(100, 100, filter = true)

        var darkSpots = 0
        var brownAreas = 0

        for (x in 0 until 100) {
            for (y in 0 until 100) {
                val pixel = scaled[x, y]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val brightness = (r + g + b) / 3

                if (brightness < 60) darkSpots++
                if (r in 60..120 && g in 40..90 && b in 30..80) brownAreas++
            }
        }

        scaled.recycle()

        val total = 10000

        // Defect detection based on grade and visual analysis
        when (grade) {
            "Class C", "Defective" -> {
                if (darkSpots > total * 0.15) defects.add("visible dark spots")
                if (brownAreas > total * 0.20) defects.add("browning")
                defects.add("minor imperfections")
            }
            "Class B" -> {
                if (darkSpots > total * 0.10) defects.add("slight discoloration")
            }
        }

        if (defects.isEmpty() && grade == "Class A") {
            defects.add("minimal defects")
        }

        return defects
    }

    /**
     * ✅ FIXED: Generate dynamic description (removed redundant curly braces)
     */
    private fun generateDescription(
        className: String,
        grade: String?,
        size: String,
        colors: List<String>,
        surface: String,
        defects: List<String>
    ): String {
        val mushroomType = when {
            className.contains("Oyster") -> "oyster mushroom"
            className.contains("Shiitake") -> "shiitake mushroom"
            className.contains("Button") -> "button mushroom"
            else -> "mushroom"
        }

        val colorDesc = when (colors.size) {
            1 -> colors[0].lowercase()
            2 -> "${colors[0].lowercase()} and ${colors[1].lowercase()}"
            else -> colors.take(2).joinToString(" and ") { it.lowercase() }
        }

        return buildString {
            // Size and type
            append("This is a ${size.lowercase()} $mushroomType")

            // Color
            append(" with $colorDesc coloring")

            // Surface condition
            append(" and a ${surface.lowercase()} surface")

            // Grade-specific details
            when (grade) {
                "Class A" -> {
                    append(". Premium quality specimen showing excellent appearance and structure")
                }
                "Class B" -> {
                    append(". Good quality specimen with minor cosmetic variations")
                }
                "Class C" -> {
                    append(". Standard quality with ${defects.joinToString(", ")}")
                }
                "Cluster" -> {
                    append(". Multiple mushrooms growing together in a cluster formation")
                }
                "Defective" -> {
                    append(". Shows signs of ${defects.joinToString(", ")}")
                }
                else -> {
                    append(". Suitable for culinary use")
                }
            }

            // Add practical note
            append(". ")
            append(getPracticalNote(size, grade))
        }
    }

    /**
     * Get practical cooking/usage note
     */
    private fun getPracticalNote(size: String, grade: String?): String {
        return when {
            size == "Large" && grade == "Class A" -> "Ideal for stuffing, grilling, or showcase dishes"
            size == "Large" -> "Great for slicing and sautéing"
            size == "Medium" && grade == "Class A" -> "Perfect for stir-fries and soups"
            size == "Medium" -> "Versatile for most cooking methods"
            size == "Small" -> "Best used whole in soups or quick sautés"
            else -> "Suitable for various culinary applications"
        }
    }

    /**
     * Get default characteristics if analysis fails
     */
    private fun getDefaultCharacteristics(className: String, grade: String?): MushroomCharacteristics {
        return MushroomCharacteristics(
            estimatedSize = "Medium",
            dominantColors = listOf("Natural"),
            surfaceCondition = "Firm",
            visualDefects = emptyList(),
            description = "Fresh $className mushroom. $grade grade."
        )
    }
}

/**
 * Data class for mushroom characteristics
 */
data class MushroomCharacteristics(
    val estimatedSize: String,          // "Small", "Medium", "Large"
    val dominantColors: List<String>,   // ["White", "Cream", "Brown"]
    val surfaceCondition: String,       // "Smooth", "Textured", "Firm"
    val visualDefects: List<String>,    // ["dark spots", "browning"]
    val description: String              // Full dynamic description
)
