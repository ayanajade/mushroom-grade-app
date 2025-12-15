package com.example.mushroom_grader.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.get
import androidx.core.graphics.scale
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ✅ FINAL VERSION - All compiler warnings fixed
 * - NUM_CLASSES = 22 (matches TFLite model)
 * - All 22 classes mapped correctly (0-21)
 * - Enhanced poisonous detection (0.75f threshold)
 * - No unused parameters or properties
 */
class MLModelHelper(private val context: Context) {

    companion object {
        private const val TAG = "MLModelHelper"
        private const val MODEL_PATH = "best.tflite"
        private const val INPUT_SIZE = 320
        private const val NUM_CLASSES = 22  // ✅ Updated to 22 classes
        private const val TOP_PREDICTIONS_COUNT = 5  // ✅ Made constant
    }

    private var interpreter: Interpreter? = null
    private var isInitialized = false

    init {
        setupInterpreter()
    }

    private fun setupInterpreter() {
        try {
            val model = FileUtil.loadMappedFile(context, MODEL_PATH)
            interpreter = Interpreter(model)
            isInitialized = true

            MushroomDetectionConfig.initialize(NUM_CLASSES) { classId ->
                getClassNameByIndex(classId)
            }

            Log.d(TAG, "✅ Model initialized: $NUM_CLASSES classes")
            Log.d(TAG, "Detection Mode: ${MushroomDetectionConfig.getDetectionModeDescription()}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading model", e)
            isInitialized = false
        }
    }

    fun classifyImage(bitmap: Bitmap): ClassificationResult? {
        if (!isInitialized || interpreter == null) {
            Log.e(TAG, "❌ Model not initialized")
            return null
        }

        return if (MushroomDetectionConfig.hasNonMushroomClass) {
            classifyWithNonMushroomClass(bitmap)
        } else {
            classifyWithHeuristicValidation(bitmap)
        }
    }

    private fun classifyWithNonMushroomClass(bitmap: Bitmap): ClassificationResult? {
        try {
            val topPredictions = getTopPredictions(bitmap)
            if (topPredictions.isEmpty()) {
                Log.d(TAG, "No predictions returned")
                return null
            }

            // Check NOT_MUSHROOM rejection
            val notMushroomPrediction = topPredictions.find {
                MushroomDetectionConfig.isNotMushroomClass(it.classId)
            }

            if (notMushroomPrediction != null &&
                notMushroomPrediction.confidence > MushroomDetectionConfig.notMushroomRejectionThreshold) {
                Log.d(TAG, "🚫 MODEL REJECTED: NOT_MUSHROOM confidence ${notMushroomPrediction.confidence}")
                return null
            }

            val topMushroomPrediction = topPredictions.firstOrNull {
                !MushroomDetectionConfig.isNotMushroomClass(it.classId)
            }

            if (topMushroomPrediction == null) {
                Log.d(TAG, "No valid mushroom predictions")
                return null
            }

            // ✅ Higher threshold for poisonous species
            val requiredConfidence = if (isPoisonousClass(topMushroomPrediction.classId)) {
                0.75f
            } else {
                MushroomDetectionConfig.confidenceThreshold
            }

            if (topMushroomPrediction.confidence < requiredConfidence) {
                Log.d(TAG, "REJECTED: Confidence too low (${topMushroomPrediction.confidence} < $requiredConfidence)")
                return null
            }

            // Check confidence gap
            val secondMushroomPrediction = topPredictions
                .filter { !MushroomDetectionConfig.isNotMushroomClass(it.classId) }
                .drop(1)
                .firstOrNull()

            if (secondMushroomPrediction != null) {
                val gap = topMushroomPrediction.confidence - secondMushroomPrediction.confidence
                val requiredGap = if (isPoisonousClass(topMushroomPrediction.classId)) {
                    0.15f
                } else {
                    MushroomDetectionConfig.confidenceGapThreshold
                }

                if (gap < requiredGap) {
                    Log.d(TAG, "REJECTED: Small confidence gap ($gap < $requiredGap)")
                    return null
                }
            }

            Log.d(TAG, "✅ ACCEPTED (Model-Based): ${topMushroomPrediction.className} (${topMushroomPrediction.confidence})")
            return topMushroomPrediction

        } catch (e: Exception) {
            Log.e(TAG, "❌ Classification failed", e)
            return null
        }
    }

    private fun classifyWithHeuristicValidation(bitmap: Bitmap): ClassificationResult? {
        try {
            if (!validateEnhancedImage(bitmap)) {
                Log.d(TAG, "REJECTED: Image quality check failed")
                return null
            }

            val topPredictions = getTopPredictions(bitmap)
            if (topPredictions.isEmpty()) {
                Log.d(TAG, "No predictions returned")
                return null
            }

            val topPrediction = topPredictions[0]

            val requiredConfidence = if (isPoisonousClass(topPrediction.classId)) {
                0.75f
            } else {
                MushroomDetectionConfig.confidenceThreshold
            }

            if (topPrediction.confidence < requiredConfidence) {
                Log.d(TAG, "REJECTED: Confidence too low (${topPrediction.confidence})")
                return null
            }

            val entropyScore = calculatePredictionEntropy(topPredictions)
            if (entropyScore > MushroomDetectionConfig.maxEntropyThreshold) {
                Log.d(TAG, "REJECTED: High entropy (uncertain predictions)")
                return null
            }

            if (topPredictions.size >= 2) {
                val confidenceGap = topPrediction.confidence - topPredictions[1].confidence
                if (confidenceGap < MushroomDetectionConfig.confidenceGapThreshold) {
                    Log.d(TAG, "REJECTED: Small confidence gap ($confidenceGap)")
                    return null
                }
            }

            if (!validateVisualFeatures(bitmap)) {
                Log.d(TAG, "REJECTED: Failed visual feature check")
                return null
            }

            val calibratedConfidence = calibrateConfidence(topPrediction.confidence, entropyScore)
            if (calibratedConfidence < MushroomDetectionConfig.calibratedConfidenceThreshold) {
                Log.d(TAG, "REJECTED: Calibrated confidence too low ($calibratedConfidence)")
                return null
            }

            Log.d(TAG, "✅ ACCEPTED (Heuristic): ${topPrediction.className} (calibrated: $calibratedConfidence)")
            return topPrediction

        } catch (e: Exception) {
            Log.e(TAG, "❌ Classification validation failed", e)
            return null
        }
    }

    private fun getTopPredictions(bitmap: Bitmap): List<ClassificationResult> {
        val inputBuffer = preprocessImage(bitmap)
        val output = Array(1) { FloatArray(NUM_CLASSES) }

        interpreter?.run(inputBuffer, output)

        val predictions = output[0]
        val sortedIndices = predictions.indices.sortedByDescending { predictions[it] }

        return sortedIndices.take(TOP_PREDICTIONS_COUNT).mapNotNull { index ->
            val confidence = predictions[index]
            createClassificationResult(index, confidence)
        }
    }

    private fun calculatePredictionEntropy(predictions: List<ClassificationResult>): Float {
        val total = predictions.sumOf { it.confidence.toDouble() }.toFloat()
        if (total == 0f) return 1f

        var entropy = 0f
        predictions.forEach { pred ->
            val p = pred.confidence / total
            if (p > 0) {
                entropy -= p * kotlin.math.ln(p)
            }
        }

        val maxEntropy = kotlin.math.ln(predictions.size.toFloat())
        return if (maxEntropy > 0) entropy / maxEntropy else 0f
    }

    private fun validateVisualFeatures(bitmap: Bitmap): Boolean {
        val width = bitmap.width.coerceAtMost(100)
        val height = bitmap.height.coerceAtMost(100)
        val scaled = bitmap.scale(width, height, filter = true)

        var mushroomColorScore = 0f
        var pixelCount = 0

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = scaled[x, y]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                if (isMushroomColor(r, g, b)) {
                    mushroomColorScore += 1f
                }
                pixelCount++
            }
        }

        scaled.recycle()

        val colorRatio = mushroomColorScore / pixelCount
        Log.d(TAG, "Visual validation - Color: $colorRatio")
        return colorRatio > MushroomDetectionConfig.minMushroomColorRatio
    }

    private fun isMushroomColor(r: Int, g: Int, b: Int): Boolean {
        val isWhitish = (r > 140 && g > 140 && b > 140)
        val isBrownish = (r in 80..220 && g in 60..180 && b in 30..150)
        val isTannish = (r in 140..230 && g in 120..210 && b in 70..160)
        val isGrayish = (r in 100..200 && g in 100..200 && b in 100..200)
        val isOrangeBrown = (r in 150..230 && g in 80..180 && b in 30..100)
        val isRedBrown = (r in 120..230 && g in 40..150 && b in 30..100)
        val isYellowish = (r in 180..240 && g in 180..240 && b in 100..180)
        val isCreamish = (r in 200..255 && g in 190..245 && b in 150..210)

        return isWhitish || isBrownish || isTannish || isGrayish ||
                isOrangeBrown || isRedBrown || isYellowish || isCreamish
    }

    private fun validateEnhancedImage(bitmap: Bitmap): Boolean {
        return expandedMushroomColorCheck(bitmap) && checkImageQuality(bitmap)
    }

    private fun expandedMushroomColorCheck(bitmap: Bitmap): Boolean {
        val width = bitmap.width.coerceAtMost(100)
        val height = bitmap.height.coerceAtMost(100)
        val scaled = bitmap.scale(width, height, filter = true)

        var colorScore = 0f
        var pixelCount = 0

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = scaled[x, y]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                if (isMushroomColor(r, g, b)) {
                    colorScore += 1f
                }
                pixelCount++
            }
        }
        scaled.recycle()

        val colorRatio = if (pixelCount > 0) colorScore / pixelCount else 0f
        Log.d(TAG, "Color ratio: $colorRatio")
        return colorRatio > MushroomDetectionConfig.minMushroomColorRatio
    }

    private fun checkImageQuality(bitmap: Bitmap): Boolean {
        val width = bitmap.width.coerceAtMost(50)
        val height = bitmap.height.coerceAtMost(50)
        val scaled = bitmap.scale(width, height, filter = true)

        var laplacianScore = 0.0
        for (x in 1 until (width - 1)) {
            for (y in 1 until (height - 1)) {
                val center = scaled[x, y]
                val neighbors = intArrayOf(
                    scaled[x - 1, y], scaled[x + 1, y],
                    scaled[x, y - 1], scaled[x, y + 1],
                    scaled[x - 1, y - 1], scaled[x + 1, y - 1],
                    scaled[x - 1, y + 1], scaled[x + 1, y + 1]
                )

                val centerGray = ((center shr 16) and 0xFF) + ((center shr 8) and 0xFF) + (center and 0xFF)
                val neighborAvg = neighbors.sumOf {
                    (((it shr 16) and 0xFF) + ((it shr 8) and 0xFF) + (it and 0xFF)) / 3
                } / 8

                laplacianScore += kotlin.math.abs(centerGray - neighborAvg)
            }
        }

        scaled.recycle()
        val sharpness = laplacianScore / (width * height)
        Log.d(TAG, "Image sharpness: $sharpness")
        return sharpness > 50.0
    }

    private fun calibrateConfidence(rawConfidence: Float, entropy: Float): Float {
        val penaltyFactor = 1.0f - (entropy * 0.15f)
        return rawConfidence * penaltyFactor
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = bitmap.scale(INPUT_SIZE, INPUT_SIZE, filter = true)
        val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resizedBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        if (resizedBitmap != bitmap) {
            resizedBitmap.recycle()
        }

        return inputBuffer
    }

    // ✅ 22 classes matching TFLite model (alphabetical order)
    private fun createClassificationResult(classId: Int, confidence: Float): ClassificationResult? {
        val classes = arrayOf(
            Triple("Amanita Pantherina", true, MushroomCategory.POISONOUS),           // 0
            Triple("Amanita phalloides", true, MushroomCategory.POISONOUS),           // 1
            Triple("Amanita virosa", true, MushroomCategory.POISONOUS),               // 2
            Triple("Button Mushroom", false, MushroomCategory.EDIBLE),                // 3
            Triple("Cinnabar Polypores", true, MushroomCategory.INEDIBLE),            // 4
            Triple("Daedaleopsis confragosa", true, MushroomCategory.INEDIBLE),       // 5
            Triple("Ganoderma applanatum", true, MushroomCategory.INEDIBLE),          // 6
            Triple("Non-Mushroom", false, MushroomCategory.UNKNOWN),                  // 7
            Triple("Oyster Class A - Cap I", false, MushroomCategory.EDIBLE),         // 8
            Triple("Oyster Class A - Cap II", false, MushroomCategory.EDIBLE),        // 9
            Triple("Oyster Class A - Cap III", false, MushroomCategory.EDIBLE),       // 10
            Triple("Oyster Class A - Cap IV", false, MushroomCategory.EDIBLE),        // 11
            Triple("Oyster Class B - Cap I", false, MushroomCategory.EDIBLE),         // 12
            Triple("Oyster Class B - Cap II", false, MushroomCategory.EDIBLE),        // 13
            Triple("Oyster Class B - Cap III", false, MushroomCategory.EDIBLE),       // 14
            Triple("Oyster Class B - Cap IV", false, MushroomCategory.EDIBLE),        // 15
            Triple("Oyster Class C - Cap I", false, MushroomCategory.EDIBLE),         // 16
            Triple("Oyster Class C - Cap II", false, MushroomCategory.EDIBLE),        // 17
            Triple("Oyster Class C - Cap III", false, MushroomCategory.EDIBLE),       // 18
            Triple("Oyster Cluster", false, MushroomCategory.EDIBLE),                 // 19
            Triple("Oyster Defective", false, MushroomCategory.INEDIBLE),             // 20
            Triple("Shiitake Mushroom", false, MushroomCategory.EDIBLE)               // 21
        )

        val (name, isPoisonous, category) = if (classId in classes.indices) {
            classes[classId]
        } else {
            Triple("Unknown", false, MushroomCategory.UNKNOWN)
        }

        val grade = when {
            name.contains("Class A") -> "Class A"
            name.contains("Class B") -> "Class B"
            name.contains("Class C") -> "Class C"
            name.contains("Cluster") -> "Cluster"
            name.contains("Defective") -> "Defective"
            name == "Button Mushroom" -> "Mixed Grade"
            name == "Shiitake Mushroom" -> "Mixed Grade"
            else -> null
        }

        return ClassificationResult(
            className = name,
            classId = classId,
            confidence = confidence,
            isPoisonous = isPoisonous,
            category = category,
            grade = grade
        )
    }

    // ✅ 22 class names in alphabetical order
    private fun getClassNameByIndex(classId: Int): String {
        val classes = arrayOf(
            "Amanita Pantherina",      // 0
            "Amanita phalloides",      // 1
            "Amanita virosa",          // 2
            "Button Mushroom",         // 3
            "Cinnabar Polypores",      // 4
            "Daedaleopsis confragosa", // 5
            "Ganoderma applanatum",    // 6
            "Non-Mushroom",            // 7
            "Oyster Class A - Cap I",  // 8
            "Oyster Class A - Cap II", // 9
            "Oyster Class A - Cap III",// 10
            "Oyster Class A - Cap IV", // 11
            "Oyster Class B - Cap I",  // 12
            "Oyster Class B - Cap II", // 13
            "Oyster Class B - Cap III",// 14
            "Oyster Class B - Cap IV", // 15
            "Oyster Class C - Cap I",  // 16
            "Oyster Class C - Cap II", // 17
            "Oyster Class C - Cap III",// 18
            "Oyster Cluster",          // 19
            "Oyster Defective",        // 20
            "Shiitake Mushroom"        // 21
        )

        return if (classId in classes.indices) classes[classId] else "Unknown"
    }

    // ✅ Helper to check if a class is poisonous
    private fun isPoisonousClass(classId: Int): Boolean {
        return classId in setOf(0, 1, 2) // Amanita species
    }

    /**
     * ✅ Get detailed mushroom information
     * @param classId The class ID (0-21)
     * @param characteristics Optional - image characteristics if available
     */
    fun getMushroomInfo(classId: Int, characteristics: MushroomCharacteristics? = null): String {
        val charInfo = if (characteristics != null) {
            buildString {
                append("📸 Features (from your photo):\n")
                append("- Size: ${characteristics.estimatedSize}\n")
                append("- Colors: ${characteristics.dominantColors.joinToString(", ")}\n")
                append("- Surface: ${characteristics.surfaceCondition}\n")
                if (characteristics.visualDefects.isNotEmpty()) {
                    append("- Notes: ${characteristics.visualDefects.joinToString(", ")}\n")
                }
                append("\n")
            }
        } else {
            ""
        }

        return when (classId) {
            0 -> buildMushroomInfo(
                name = "AMANITA PANTHERINA (Panther Cap)",
                safety = "⚠️ HIGHLY POISONOUS - DO NOT CONSUME",
                charInfo = charInfo,
                staticFeatures = "Brown cap w/ white warts, white gills & stem",
                habitat = "Deciduous/coniferous forests in North America, Europe, Asia",
                toxins = "Ibotenic acid, muscimol",
                symptoms = "Confusion, hallucinations, vomiting",
                firstAid = "Seek emergency care immediately"
            )
            1 -> buildMushroomInfo(
                name = "AMANITA PHALLOIDES (Death Cap)",
                safety = "⚠️ EXTREMELY DEADLY - DO NOT CONSUME",
                charInfo = charInfo,
                staticFeatures = "Pale yellow/green cap, white gills, volva at base",
                habitat = "Oak, beech forests, introduced worldwide",
                toxins = "Amatoxins",
                symptoms = "Severe vomiting, abdominal pain, liver failure",
                firstAid = "Hospitalization required"
            )
            2 -> buildMushroomInfo(
                name = "AMANITA VIROSA (Destroying Angel)",
                safety = "⚠️ EXTREMELY DEADLY - DO NOT CONSUME",
                charInfo = charInfo,
                staticFeatures = "Pure white, smooth cap, ring, bulbous base",
                habitat = "Deciduous/evergreen woods, Europe, Asia",
                toxins = "Amatoxins",
                symptoms = "Delayed onset, kidney/liver failure",
                firstAid = "Immediate medical attention"
            )
            3 -> buildMushroomInfo(
                name = "BUTTON MUSHROOM (Agaricus bisporus)",
                safety = "✅ EDIBLE - SAFE TO EAT",
                charInfo = charInfo,
                staticFeatures = "White or brown cap, pink to brown gills",
                habitat = "Commercially grown worldwide",
                bestUses = "Salads, soups, pizzas, sautéed",
                nutrition = "High in B vitamins, selenium, low-calorie protein"
            )
            4 -> buildMushroomInfo(
                name = "CINNABAR POLYPORES",
                safety = "⚠️ INEDIBLE — NOT TOXIC BUT TOO TOUGH",
                charInfo = charInfo,
                staticFeatures = "Bright red, flat shelf-like fruiting body",
                habitat = "Dead hardwood worldwide",
                culinaryUse = "Not edible, used for dyes/pigments"
            )
            5 -> buildMushroomInfo(
                name = "DAEDALEOPSIS CONFRAGOSA (Blushing Bracket)",
                safety = "⚠️ INEDIBLE",
                charInfo = charInfo,
                staticFeatures = "Tan/grey top, reddish bruising, maze-like pores",
                habitat = "Fallen willow, birch, hardwood logs",
                culinaryUse = "Not eaten; used for decoration"
            )
            6 -> buildMushroomInfo(
                name = "GANODERMA APPLANATUM (Artist's Conk)",
                safety = "⚠️ NOT FOR CULINARY USE",
                charInfo = charInfo,
                staticFeatures = "Brown crust, very woody, hard perennial bracket",
                habitat = "Dead/dying hardwood globally",
                use = "Traditional medicine (immune boosting)"
            )
            7 -> "❌ NON-MUSHROOM DETECTED\n- This image does not appear to contain a mushroom\n- Please capture a clear mushroom image"

            in 8..11 -> buildOysterInfo("A", classId - 8, charInfo)
            in 12..15 -> buildOysterInfo("B", classId - 12, charInfo)
            in 16..18 -> buildOysterInfo("C", classId - 16, charInfo)

            19 -> buildMushroomInfo(
                name = "OYSTER CLUSTER",
                safety = "✅ EDIBLE — MULTIPLE MUSHROOMS",
                charInfo = charInfo,
                staticFeatures = "Multiple oyster mushrooms growing together",
                bestUses = "Separate and sort by individual grades"
            )
            20 -> buildMushroomInfo(
                name = "OYSTER DEFECTIVE",
                safety = "⚠️ NOT FOR CONSUMPTION",
                charInfo = charInfo,
                staticFeatures = "Soft, watery, dark spots or minor spoilage",
                bestUses = "Rejected for human food"
            )
            21 -> buildMushroomInfo(
                name = "SHIITAKE MUSHROOM",
                safety = "✅ EDIBLE — MUST BE COOKED",
                charInfo = charInfo,
                staticFeatures = "Brown cap, white gills, white stem",
                culinaryUse = "Grilled, sautéed, soups, tempura",
                nutrition = "Immunity boost, dietary fiber, vitamin D"
            )
            else -> "❌ UNKNOWN MUSHROOM\nNever consume unidentified wild mushrooms!"
        }
    }

    private fun buildOysterInfo(grade: String, capIndex: Int, charInfo: String): String {
        val capNames = arrayOf("Cap I (Small)", "Cap II (Medium)", "Cap III (Large)", "Cap IV (Extra Large)")
        val capName = if (capIndex in capNames.indices) capNames[capIndex] else "Unknown"

        val quality = when (grade) {
            "A" -> "✅ EDIBLE — PREMIUM QUALITY"
            "B" -> "✅ EDIBLE — GOOD QUALITY"
            "C" -> "✅ EDIBLE — FAIR/COOK ONLY"
            else -> "✅ EDIBLE"
        }

        return buildString {
            append("🍄 OYSTER MUSHROOM - CLASS $grade - $capName\n")
            append("$quality\n\n")
            append(charInfo)
            append("Nutrition: Rich in protein, B vitamins, antioxidants")
        }
    }

    private fun buildMushroomInfo(
        name: String,
        safety: String,
        charInfo: String,
        staticFeatures: String,
        habitat: String? = null,
        toxins: String? = null,
        symptoms: String? = null,
        firstAid: String? = null,
        bestUses: String? = null,
        nutrition: String? = null,
        culinaryUse: String? = null,
        use: String? = null
    ): String {
        return buildString {
            append("🍄 $name\n")
            append("$safety\n\n")
            append(charInfo)
            append("- Features: $staticFeatures\n")
            habitat?.let { append("- Habitat: $it\n") }
            toxins?.let { append("- Toxins: $it\n") }
            symptoms?.let { append("- Symptoms: $it\n") }
            firstAid?.let { append("- First Aid: $it\n") }
            bestUses?.let { append("- Best Uses: $it\n") }
            nutrition?.let { append("- Nutrition: $it\n") }
            culinaryUse?.let { append("- Culinary Use: $it\n") }
            use?.let { append("- Use: $it\n") }
        }
    }

    fun getConfidenceLevel(confidence: Float): String {
        return when {
            confidence >= 0.90f -> "HIGH"
            confidence >= 0.85f -> "CAUTION"
            confidence >= 0.60f -> "RETAKE"
            else -> "REJECTED"
        }
    }

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error closing interpreter", e)
        }
    }
}