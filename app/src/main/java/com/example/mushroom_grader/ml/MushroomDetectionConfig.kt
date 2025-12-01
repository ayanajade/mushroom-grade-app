package com.example.mushroom_grader.ml

import android.util.Log

/**
 * Configuration for mushroom detection
 * Automatically adapts based on whether NOT_MUSHROOM class exists
 * ✅ ENHANCED: More lenient thresholds for better real-world detection
 */
object MushroomDetectionConfig {

    private const val TAG = "MushroomDetectionConfig"

    // NOT_MUSHROOM class names to look for
    private const val NOT_MUSHROOM_CLASS_NAME = "NOT_MUSHROOM"
    private const val NOT_MUSHROOM_ALT_NAME = "Non-Mushroom"
    private const val NOT_MUSHROOM_ALT_NAME_2 = "Non Mushroom"

    // Detection mode
    var hasNonMushroomClass: Boolean = false
        private set

    var nonMushroomClassIndex: Int = -1
        private set

    // ✅ IMPROVED: More lenient thresholds for better detection
    val confidenceThreshold: Float
        get() = if (hasNonMushroomClass) 0.60f else 0.55f  // Lowered from 0.75f/0.60f

    val confidenceGapThreshold: Float
        get() = if (hasNonMushroomClass) 0.10f else 0.15f  // Lowered from 0.15f/0.20f

    val calibratedConfidenceThreshold: Float
        get() = if (hasNonMushroomClass) 0.65f else 0.60f  // Lowered from 0.80f/0.70f

    val notMushroomRejectionThreshold: Float
        get() = 0.35f  // Increased from 0.30f

    // ✅ NEW: Thresholds for visual feature validation
    val minMushroomColorRatio: Float
        get() = 0.25f  // Lowered from 0.30f - allow darker mushrooms

    val minMushroomTextureRatio: Float
        get() = 0.30f  // Lowered from 0.40f - allow smooth caps

    val maxEntropyThreshold: Float
        get() = 1.0f   // Relaxed from 0.8f - allow more uncertainty

    /**
     * Initialize detection configuration
     * Call this when loading model
     */
    fun initialize(numClasses: Int, getClassName: (Int) -> String) {
        // Check if NOT_MUSHROOM class exists
        nonMushroomClassIndex = -1
        for (i in 0 until numClasses) {
            val className = getClassName(i)
            if (className.equals(NOT_MUSHROOM_CLASS_NAME, ignoreCase = true) ||
                className.equals(NOT_MUSHROOM_ALT_NAME, ignoreCase = true) ||
                className.equals(NOT_MUSHROOM_ALT_NAME_2, ignoreCase = true)) {
                nonMushroomClassIndex = i
                break
            }
        }

        hasNonMushroomClass = nonMushroomClassIndex >= 0
        if (hasNonMushroomClass) {
            Log.i(TAG, "✅ NOT_MUSHROOM class detected at index $nonMushroomClassIndex")
            Log.i(TAG, "🔥 Enhanced mode: Using model-based rejection")
        } else {
            Log.i(TAG, "⚠️ NOT_MUSHROOM class not found")
            Log.i(TAG, "🛡️ Fallback mode: Using heuristic-based validation")
        }

        Log.i(TAG, "Thresholds - Confidence: $confidenceThreshold, Gap: $confidenceGapThreshold, Calibrated: $calibratedConfidenceThreshold")
    }

    /**
     * Check if a class ID is the NOT_MUSHROOM class
     */
    fun isNotMushroomClass(classId: Int): Boolean {
        return hasNonMushroomClass && classId == nonMushroomClassIndex
    }

    /**
     * Get detection mode description
     */
    fun getDetectionModeDescription(): String {
        return if (hasNonMushroomClass) {
            "Enhanced Detection (Model-Based)"
        } else {
            "Standard Detection (Heuristic)"
        }
    }
}
