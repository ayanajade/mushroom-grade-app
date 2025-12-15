package com.example.mushroom_grader.ml

import android.util.Log

object MushroomDetectionConfig {
    private const val TAG = "MushroomDetectionConfig"

    private const val NOT_MUSHROOM_CLASS_NAME = "NOT_MUSHROOM"
    private const val NOT_MUSHROOM_ALT_NAME = "Non-Mushroom"
    private const val NOT_MUSHROOM_ALT_NAME_2 = "Non Mushroom"

    var hasNonMushroomClass: Boolean = false
        private set
    var nonMushroomClassIndex: Int = -1
        private set

    // ✅ Tuned thresholds based on classification report
    val confidenceThreshold: Float
        get() = if (hasNonMushroomClass) 0.55f else 0.50f

    val confidenceGapThreshold: Float
        get() = if (hasNonMushroomClass) 0.10f else 0.12f

    val calibratedConfidenceThreshold: Float
        get() = if (hasNonMushroomClass) 0.60f else 0.55f

    // ✅ Increased to reduce false positives from Non-Mushroom class
    val notMushroomRejectionThreshold: Float
        get() = 0.40f  // Raised from 0.35f - stricter rejection

    val minMushroomColorRatio: Float
        get() = 0.25f

    val minMushroomTextureRatio: Float
        get() = 0.30f

    val maxEntropyThreshold: Float
        get() = 0.85f  // Tightened from 1.0f

    // ✅ NEW: Thresholds for poisonous species (must be higher)
    val poisonousConfidenceThreshold: Float
        get() = 0.75f

    val poisonousGapThreshold: Float
        get() = 0.20f

    fun initialize(numClasses: Int, getClassName: (Int) -> String) {
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

        Log.i(TAG, "Thresholds - Confidence: $confidenceThreshold, Gap: $confidenceGapThreshold")
        Log.i(TAG, "Poisonous Thresholds - Confidence: $poisonousConfidenceThreshold, Gap: $poisonousGapThreshold")
    }

    fun isNotMushroomClass(classId: Int): Boolean {
        return hasNonMushroomClass && classId == nonMushroomClassIndex
    }

    fun getDetectionModeDescription(): String {
        return if (hasNonMushroomClass) {
            "Enhanced Detection (Model-Based) - 22 Classes"
        } else {
            "Standard Detection (Heuristic)"
        }
    }
}
