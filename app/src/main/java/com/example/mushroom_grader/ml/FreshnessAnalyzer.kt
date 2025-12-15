package com.example.mushroom_grader.ml

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.get
import androidx.core.graphics.scale
import kotlin.math.abs

/**
 * FreshnessAnalyzer - Analyzes mushroom visual condition from bitmap
 *
 * Analyzes actual mushroom appearance to determine current freshness level:
 * - Color vibrancy (fading indicates age)
 * - Browning and discoloration
 * - Dark spots and blemishes
 * - Texture degradation
 * - Structural integrity
 */
class FreshnessAnalyzer {

    companion object {
        private const val TAG = "FreshnessAnalyzer"

        // Freshness score thresholds
        private const val EXCELLENT_THRESHOLD = 85  // 85-100%
        private const val GOOD_THRESHOLD = 70       // 70-84%
        private const val FAIR_THRESHOLD = 50       // 50-69%
        private const val DECLINING_THRESHOLD = 30  // 30-49%
        // Below 30% = Spoiled
    }

    /**
     * Analyze mushroom freshness from image
     * Returns FreshnessAnalysisResult with score and status
     */
    fun analyzeFreshness(bitmap: Bitmap, mushroomType: String): FreshnessAnalysisResult {
        Log.d(TAG, "🔍 Analyzing freshness for: $mushroomType")

        try {
            // Analyze different visual aspects
            val colorScore = analyzeColorVibrancy(bitmap)
            val browningScore = analyzeBrowningLevel(bitmap)
            val spotScore = analyzeSpotting(bitmap)
            val textureScore = analyzeTexture(bitmap)


            // Calculate weighted freshness score
            val overallScore = calculateOverallScore(
                colorScore,
                browningScore,
                spotScore,
                textureScore
            )

            val status = determineFreshnessStatus(overallScore)
            val shelfLifeMultiplier = calculateShelfLifeMultiplier(overallScore)

            Log.d(TAG, "✅ Analysis complete - Score: $overallScore%, Status: $status")

            return FreshnessAnalysisResult(
                freshnessScore = overallScore,
                status = status,
                shelfLifeMultiplier = shelfLifeMultiplier,
                colorScore = colorScore,
                browningScore = browningScore,
                spotScore = spotScore,
                textureScore = textureScore,
                details = buildAnalysisDetails(
                    overallScore,
                    colorScore,
                    browningScore,
                    spotScore,
                    textureScore,
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error analyzing freshness", e)
            // Return default "Good" result on error
            return FreshnessAnalysisResult(
                freshnessScore = 75,
                status = "Good",
                shelfLifeMultiplier = 0.9f,
                colorScore = 75,
                browningScore = 75,
                spotScore = 75,
                textureScore = 75,
                details = "Analysis error - using default freshness estimate"
            )
        }
    }

    /**
     * Analyze color vibrancy (faded colors indicate age)
     */
    private fun analyzeColorVibrancy(bitmap: Bitmap): Int {
        val width = bitmap.width.coerceAtMost(100)
        val height = bitmap.height.coerceAtMost(100)
        val scaled = bitmap.scale(width, height, filter = true)

        var totalSaturation = 0.0
        var pixelCount = 0
        var brightPixels = 0

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = scaled[x, y]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Calculate saturation
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val saturation = if (max != 0) {
                    ((max - min).toDouble() / max) * 100
                } else {
                    0.0
                }

                totalSaturation += saturation

                // Count bright/vibrant pixels (fresh mushrooms)
                if (max > 120 && saturation > 8) {  // ✅ More lenient
                    brightPixels++
                }

                pixelCount++
            }
        }

        scaled.recycle()

        val avgSaturation = totalSaturation / pixelCount
        val brightnessRatio = brightPixels.toFloat() / pixelCount

        // Fresh mushrooms have higher saturation and brightness
        val score = ((avgSaturation * 0.6) + (brightnessRatio * 100 * 0.4)).coerceIn(0.0, 100.0)

        Log.d(TAG, "Color vibrancy: ${score.toInt()}% (sat: $avgSaturation, bright: $brightnessRatio)")
        return score.toInt()
    }

    /**
     * Analyze browning level (brown spots indicate decay)
     */
    private fun analyzeBrowningLevel(bitmap: Bitmap): Int {
        val width = bitmap.width.coerceAtMost(100)
        val height = bitmap.height.coerceAtMost(100)
        val scaled = bitmap.scale(width, height, filter = true)

        var brownPixels = 0
        var darkBrownPixels = 0
        var pixelCount = 0

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = scaled[x, y]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Detect browning (higher red than blue, moderate green)
                val isBrown = r > b && r > g && r in 80..180 && g in 40..120
                val isDarkBrown = r in 60..120 && g in 30..80 && b in 20..60

                if (isBrown) brownPixels++
                if (isDarkBrown) darkBrownPixels++

                pixelCount++
            }
        }

        scaled.recycle()

        val brownRatio = brownPixels.toFloat() / pixelCount
        val darkBrownRatio = darkBrownPixels.toFloat() / pixelCount

        // Less browning = higher score
        val score = (100 - (brownRatio * 80 + darkBrownRatio * 150)).coerceIn(0.0f, 100.0f)

        Log.d(TAG, "Browning level: ${score.toInt()}% (brown: $brownRatio, dark: $darkBrownRatio)")
        return score.toInt()
    }

    /**
     * Analyze dark spots and blemishes
     */
    private fun analyzeSpotting(bitmap: Bitmap): Int {
        val width = bitmap.width.coerceAtMost(100)
        val height = bitmap.height.coerceAtMost(100)
        val scaled = bitmap.scale(width, height, filter = true)

        var spotPixels = 0
        var darkPixels = 0
        var pixelCount = 0

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = scaled[x, y]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val brightness = (r + g + b) / 3

                // Detect dark spots (signs of decay)
                if (brightness < 60) {
                    darkPixels++
                } else if (brightness < 100) {
                    spotPixels++
                }

                pixelCount++
            }
        }

        scaled.recycle()

        val spotRatio = (spotPixels + darkPixels).toFloat() / pixelCount

        // Fewer spots = higher score
        val score = (100 - (spotRatio * 120)).coerceIn(0.0f, 100.0f)

        Log.d(TAG, "Spotting level: ${score.toInt()}% (spot ratio: $spotRatio)")
        return score.toInt()
    }

    /**
     * Analyze texture (wrinkles, sagging indicate age)
     */
    private fun analyzeTexture(bitmap: Bitmap): Int {
        val width = bitmap.width.coerceAtMost(80)
        val height = bitmap.height.coerceAtMost(80)
        val scaled = bitmap.scale(width, height, filter = true)

        var totalVariance = 0.0
        var smoothRegions = 0

        for (x in 1 until (width - 1)) {
            for (y in 1 until (height - 1)) {
                val center = scaled[x, y]
                val centerBrightness = ((center shr 16) and 0xFF) +
                        ((center shr 8) and 0xFF) +
                        (center and 0xFF)

                // Check 4 neighbors
                val neighbors = listOf(
                    scaled[x - 1, y],
                    scaled[x + 1, y],
                    scaled[x, y - 1],
                    scaled[x, y + 1]
                )

                var variance = 0.0
                neighbors.forEach { neighbor ->
                    val neighborBrightness = ((neighbor shr 16) and 0xFF) +
                            ((neighbor shr 8) and 0xFF) +
                            (neighbor and 0xFF)
                    variance += abs(centerBrightness - neighborBrightness)
                }
                variance /= neighbors.size

                totalVariance += variance

                // Fresh mushrooms have smooth, even texture
                if (variance < 30) {  // ✅ Accepts more natural texture
                    smoothRegions++
                }
            }
        }

        scaled.recycle()

        val avgVariance = totalVariance / ((width - 2) * (height - 2))
        val smoothRatio = smoothRegions.toFloat() / ((width - 2) * (height - 2))

        // Smooth texture = higher score (fresh)
        val score = ((smoothRatio * 100 * 0.7) + ((100 - avgVariance) * 0.3)).coerceIn(0.0, 100.0)

        Log.d(TAG, "Texture quality: ${score.toInt()}% (variance: $avgVariance, smooth: $smoothRatio)")
        return score.toInt()
    }


    /**
     * Calculate overall freshness score (weighted average)
     */
    private fun calculateOverallScore(
        color: Int,
        browning: Int,
        spots: Int,
        texture: Int
    ): Int {
        val weighted = (
                color * 0.27 +       // Color is very important
                        browning * 0.35 +    // Browning is critical
                        spots * 0.23 +       // Spots indicate decay
                        texture * 0.15    // Texture matters

                )

        return weighted.toInt().coerceIn(0, 100)
    }

    /**
     * Determine freshness status from score
     */
    private fun determineFreshnessStatus(score: Int): String {
        return when {
            score >= EXCELLENT_THRESHOLD -> "Excellent - Farm Fresh"
            score >= GOOD_THRESHOLD -> "Good - Fresh"
            score >= FAIR_THRESHOLD -> "Fair - Consume Soon"
            score >= DECLINING_THRESHOLD -> "Declining - Use Today"
            else -> "Poor - Do Not Consume"
        }
    }

    /**
     * Calculate shelf life multiplier based on freshness
     * Excellent (90%+) = 1.0x (full shelf life)
     * Good (70-89%) = 0.8x
     * Fair (50-69%) = 0.5x
     * Declining (30-49%) = 0.3x
     * Spoiled (<30%) = 0.1x
     */
    private fun calculateShelfLifeMultiplier(score: Int): Float {
        return when {
            score >= 90 -> 1.0f   // Excellent condition
            score >= 80 -> 0.9f   // Very good
            score >= 70 -> 0.8f   // Good
            score >= 60 -> 0.65f  // Fair
            score >= 50 -> 0.5f   // Declining
            score >= 40 -> 0.35f  // Poor
            score >= 30 -> 0.2f   // Very poor
            else -> 0.1f          // Spoiled
        }
    }

    /**
     * Build detailed analysis text
     */
    private fun buildAnalysisDetails(
        overall: Int,
        color: Int,
        browning: Int,
        spots: Int,
        texture: Int,
    ): String {
        return buildString {
            append("Visual Freshness Analysis:\n\n")
            append("Overall Freshness: $overall%\n")
            append("• Color Vibrancy: $color%\n")
            append("• Browning Level: $browning%\n")
            append("• Spotting: $spots%\n")
            append("• Texture Quality: $texture%\n")
        }
    }
}

/**
 * Result of freshness analysis
 */
data class FreshnessAnalysisResult(
    val freshnessScore: Int,          // 0-100%
    val status: String,                // "Excellent", "Good", "Fair", "Declining", "Spoiled"
    val shelfLifeMultiplier: Float,    // Multiplier for shelf life calculation
    val colorScore: Int,
    val browningScore: Int,
    val spotScore: Int,
    val textureScore: Int,
    val details: String
)
