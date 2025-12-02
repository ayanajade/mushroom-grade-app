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

class MLModelHelper(private val context: Context) {

    companion object {
        private const val TAG = "MLModelHelper"
        private const val MODEL_PATH = "mushroom_classifier.tflite"
        private const val INPUT_SIZE = 256
        private const val NUM_CLASSES = 13

        private val THRESHOLDS = listOf(
            0.90f, // High confidence
            0.85f, // Caution
            0.60f  // Minimum threshold
        )
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
            val topPredictions = getTopPredictions(bitmap, 5)
            if (topPredictions.isEmpty()) {
                Log.d(TAG, "No predictions returned")
                return null
            }

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

            if (topMushroomPrediction.confidence < MushroomDetectionConfig.confidenceThreshold) {
                Log.d(TAG, "REJECTED: Confidence too low (${topMushroomPrediction.confidence})")
                return null
            }

            val secondMushroomPrediction = topPredictions
                .filter { !MushroomDetectionConfig.isNotMushroomClass(it.classId) }
                .drop(1)
                .firstOrNull()

            if (secondMushroomPrediction != null) {
                val gap = topMushroomPrediction.confidence - secondMushroomPrediction.confidence
                if (gap < MushroomDetectionConfig.confidenceGapThreshold) {
                    Log.d(TAG, "REJECTED: Small confidence gap ($gap)")
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

            val topPredictions = getTopPredictions(bitmap, 3)
            if (topPredictions.isEmpty()) {
                Log.d(TAG, "No predictions returned")
                return null
            }

            val topPrediction = topPredictions[0]
            if (topPrediction.confidence < MushroomDetectionConfig.confidenceThreshold) {
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

            if (topPredictions.size >= 2 && !validateCategoryConsistency(topPredictions)) {
                Log.d(TAG, "REJECTED: Category inconsistency")
                return null
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

            Log.d(TAG, "✅ ACCEPTED (Heuristic): ${topPrediction.className} (raw: ${topPrediction.confidence}, calibrated: $calibratedConfidence)")
            return topPrediction.copy(confidence = calibratedConfidence)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Classification validation failed", e)
            return null
        }
    }

    private fun getTopPredictions(bitmap: Bitmap, topK: Int): List<ClassificationResult> {
        val inputBuffer = preprocessImage(bitmap)
        val output = Array(1) { FloatArray(NUM_CLASSES) }
        interpreter?.run(inputBuffer, output)

        val predictions = output[0]
        val sortedIndices = predictions.indices.sortedByDescending { predictions[it] }

        return sortedIndices.take(topK).mapNotNull { index ->
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

    private fun validateCategoryConsistency(predictions: List<ClassificationResult>): Boolean {
        if (predictions.isEmpty()) return false
        val topCategory = predictions[0].category
        val agreementCount = predictions.count { it.category == topCategory }
        return agreementCount >= 2
    }

    private fun validateVisualFeatures(bitmap: Bitmap): Boolean {
        val width = bitmap.width.coerceAtMost(100)
        val height = bitmap.height.coerceAtMost(100)
        val scaled = bitmap.scale(width, height, filter = true)

        var mushroomColorScore = 0f
        var organicTextureScore = 0f
        var pixelCount = 0

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = scaled[x, y]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val isWhitish = (r > 140 && g > 140 && b > 140)
                val isBrownish = (r in 80..220 && g in 60..180 && b in 30..150)
                val isTannish = (r in 140..230 && g in 120..210 && b in 70..160)
                val isGrayish = (r in 100..200 && g in 100..200 && b in 100..200)
                val isOrangeBrown = (r in 150..230 && g in 80..180 && b in 30..100)
                val isRedBrown = (r in 120..230 && g in 40..150 && b in 30..100)
                val isYellowish = (r in 180..240 && g in 180..240 && b in 100..180)

                if (isWhitish || isBrownish || isTannish || isGrayish ||
                    isOrangeBrown || isRedBrown || isYellowish) {
                    mushroomColorScore += 1f
                }

                val neighbors = getNeighborPixels(scaled, x, y)
                val variance = calculateLocalVariance(pixel, neighbors)
                if (variance in 10f..80f) {
                    organicTextureScore += 1f
                }

                pixelCount++
            }
        }

        scaled.recycle()

        val colorRatio = mushroomColorScore / pixelCount
        val textureRatio = organicTextureScore / pixelCount

        Log.d(TAG, "Visual validation - Color: $colorRatio, Texture: $textureRatio")

        return colorRatio > MushroomDetectionConfig.minMushroomColorRatio &&
                textureRatio > MushroomDetectionConfig.minMushroomTextureRatio
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

                val isWhitish = (r > 140 && g > 140 && b > 140)
                val isBrownish = (r in 80..220 && g in 60..180 && b in 30..150)
                val isTannish = (r in 140..230 && g in 120..210 && b in 70..160)
                val isGrayish = (r in 100..200 && g in 100..200 && b in 100..200)
                val isOrangeBrown = (r in 150..230 && g in 80..180 && b in 30..100)
                val isRedBrown = (r in 120..230 && g in 40..150 && b in 30..100)
                val isYellowish = (r in 180..240 && g in 180..240 && b in 100..180)
                val isCreamish = (r in 200..255 && g in 190..245 && b in 150..210)

                if (isWhitish || isBrownish || isTannish || isGrayish ||
                    isOrangeBrown || isRedBrown || isYellowish || isCreamish) {
                    colorScore += 1f
                }

                pixelCount++
            }
        }

        scaled.recycle()

        val colorRatio = if (pixelCount > 0) colorScore / pixelCount else 0f
        Log.d(TAG, "Color ratio: $colorRatio (threshold: ${MushroomDetectionConfig.minMushroomColorRatio})")

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
                    scaled[x - 1, y],
                    scaled[x + 1, y],
                    scaled[x, y - 1],
                    scaled[x, y + 1],
                    scaled[x - 1, y - 1],
                    scaled[x + 1, y - 1],
                    scaled[x - 1, y + 1],
                    scaled[x + 1, y + 1]
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

    private fun getNeighborPixels(bitmap: Bitmap, x: Int, y: Int): List<Int> {
        val neighbors = mutableListOf<Int>()
        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = (x + dx).coerceIn(0, bitmap.width - 1)
                val ny = (y + dy).coerceIn(0, bitmap.height - 1)
                neighbors.add(bitmap[nx, ny])
            }
        }
        return neighbors
    }

    private fun calculateLocalVariance(centerPixel: Int, neighbors: List<Int>): Float {
        val centerR = (centerPixel shr 16) and 0xFF
        val centerG = (centerPixel shr 8) and 0xFF
        val centerB = centerPixel and 0xFF

        var variance = 0f
        neighbors.forEach { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            val diff = kotlin.math.abs(r - centerR) +
                    kotlin.math.abs(g - centerG) +
                    kotlin.math.abs(b - centerB)
            variance += diff
        }

        return if (neighbors.isNotEmpty()) variance / neighbors.size else 0f
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

    private fun createClassificationResult(classId: Int, confidence: Float): ClassificationResult? {
        val classes = arrayOf(
            Triple("Amanita Pantherina", true, MushroomCategory.POISONOUS),      // 0
            Triple("Amanita phalloides", true, MushroomCategory.POISONOUS),       // 1
            Triple("Amanita virosa", true, MushroomCategory.POISONOUS),           // 2
            Triple("Button Mushroom", false, MushroomCategory.EDIBLE),            // 3
            Triple("Cinnabar Polypores", true, MushroomCategory.POISONOUS),       // 4
            Triple("Daedaleopsis confragosa", true, MushroomCategory.POISONOUS),  // 5
            Triple("Ganoderma applanatum", true, MushroomCategory.POISONOUS),     // 6
            Triple("Oyster - Class A", false, MushroomCategory.EDIBLE),           // 7
            Triple("Oyster - Class B", false, MushroomCategory.EDIBLE),           // 8
            Triple("Oyster - Class C", false, MushroomCategory.EDIBLE),           // 9
            Triple("Oyster - Cluster", false, MushroomCategory.EDIBLE),           // 10
            Triple("Oyster - Defective", false, MushroomCategory.INEDIBLE),       // 11
            Triple("Shiitake Mushroom", false, MushroomCategory.EDIBLE)           // 12
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

    private fun getClassNameByIndex(classId: Int): String {
        val classes = arrayOf(
            "Amanita Pantherina",
            "Amanita phalloides",
            "Amanita virosa",
            "Button Mushroom",
            "Cinnabar Polypores",
            "Daedaleopsis confragosa",
            "Ganoderma applanatum",
            "Oyster - Class A",
            "Oyster - Class B",
            "Oyster - Class C",
            "Oyster - Cluster",
            "Oyster - Defective",
            "Shiitake Mushroom"
        )

        return if (classId in classes.indices) classes[classId] else "Unknown"
    }

    /**
     * ✅ UPDATED: Get mushroom info with DYNAMIC features based on actual image
     * Now accepts optional characteristics to show accurate physical description
     */
    fun getMushroomInfo(
        classId: Int,
        characteristics: MushroomCharacteristics? = null
    ): String {
        return when (classId) {
            0 -> buildMushroomInfo(
                name = "AMANITA PANTHERINA (Panther Cap)",
                safety = "⚠️ HIGHLY POISONOUS - DO NOT CONSUME",
                characteristics = characteristics,
                staticFeatures = "Brown cap w/ white warts, white gills & stem",
                habitat = "Deciduous/coniferous forests in North America, Europe, Asia",
                toxins = "Ibotenic acid, muscimol",
                symptoms = "Confusion, hallucinations, vomiting",
                firstAid = "Induce vomiting, seek emergency care immediately"
            )

            1 -> buildMushroomInfo(
                name = "AMANITA PHALLOIDES (Death Cap)",
                safety = "⚠️ EXTREMELY DEADLY - DO NOT CONSUME",
                characteristics = characteristics,
                staticFeatures = "Pale yellow/green cap, white gills, volva at base",
                habitat = "Oak, beech forests, introduced worldwide",
                toxins = "Amatoxins",
                symptoms = "Severe vomiting, abdominal pain, liver failure",
                firstAid = "Hospitalization, activated charcoal, liver support"
            )

            2 -> buildMushroomInfo(
                name = "AMANITA VIROSA (Destroying Angel)",
                safety = "⚠️ EXTREMELY DEADLY - DO NOT CONSUME",
                characteristics = characteristics,
                staticFeatures = "Pure white, smooth cap, ring, bulbous base",
                habitat = "Deciduous/evergreen woods, widespread in Europe, Asia",
                toxins = "Amatoxins",
                symptoms = "Delayed onset, vomiting, kidney/liver failure",
                firstAid = "Immediate medical attention, treat as medical emergency"
            )

            3 -> buildMushroomInfo(
                name = "BUTTON MUSHROOM (Agaricus bisporus)",
                safety = "✅ EDIBLE - SAFE TO EAT",
                characteristics = characteristics,
                staticFeatures = "White or brown cap, pink to brown gills, pleasant smell",
                habitat = "Commercially grown, global cultivation",
                bestUses = "Salads, soups, pizzas, sauteed, versatile in cuisine",
                nutrition = "High in B vitamins, selenium, low-calorie protein source",
                safetyNote = "Safe when fresh and properly cooked"
            )

            4 -> buildMushroomInfo(
                name = "CINNABAR POLYPORES (Pycnoporus cinnabarinus)",
                safety = "⚠️ INEDIBLE — NOT TOXIC BUT TOO TOUGH",
                characteristics = characteristics,
                staticFeatures = "Bright red, flat shelf-like fruiting body, hard texture",
                habitat = "On dead hardwood worldwide, tropics to temperate",
                culinaryUse = "Not edible, used for dyes/pigments",
                medicinalUse = "Limited use in traditional medicine"
            )

            5 -> buildMushroomInfo(
                name = "DAEDALEOPSIS CONFRAGOSA (Blushing Bracket)",
                safety = "⚠️ INEDIBLE",
                characteristics = characteristics,
                staticFeatures = "Tan/grey top, reddish bruising, maze-like pores beneath",
                habitat = "Fallen willow, birch, hardwood logs throughout Eurasia",
                culinaryUse = "Not eaten; used for decoration/woodcraft"
            )

            6 -> buildMushroomInfo(
                name = "GANODERMA APPLANATUM (Artist's Conk)",
                safety = "⚠️ NOT FOR CULINARY USE - MEDICINAL REMEDY ONLY",
                characteristics = characteristics,
                staticFeatures = "Brown crust, very woody, hard perennial bracket",
                habitat = "On dead/dying hardwood globally",
                use = "Traditional medicine (immune boosting, anti-inflammatory)",
                culinaryNote = "Not edible; boiled for extracts/teas (very bitter)"
            )

            7 -> buildEdibleOysterInfo(
                grade = "CLASS A (Premium)",
                quality = "✅ EDIBLE — PREMIUM QUALITY",
                characteristics = characteristics,
                staticFeatures = "Firm, white, unblemished, large fan-shaped cap",
                bestUse = "Fresh, grilling, roasting, best flavor and texture"
            )

            8 -> buildEdibleOysterInfo(
                grade = "CLASS B (Good)",
                quality = "✅ EDIBLE — GOOD QUALITY",
                characteristics = characteristics,
                staticFeatures = "Minor defects, still firm and fresh, slightly smaller",
                bestUse = "Suitable for cooked dishes, stir-fries, soups, stews"
            )

            9 -> buildEdibleOysterInfo(
                grade = "CLASS C (Fair)",
                quality = "✅ EDIBLE — FAIR/COOK ONLY",
                characteristics = characteristics,
                staticFeatures = "Small, thin, slight discoloration",
                bestUse = "Must be cooked, best in stews, sauces"
            )

            10 -> buildEdibleOysterInfo(
                grade = "CLUSTER",
                quality = "✅ EDIBLE — MULTIPLE MUSHROOMS GROUPED",
                characteristics = characteristics,
                staticFeatures = "Multiple oyster mushrooms growing together",
                bestUse = "Separate and sort by individual grades, or cook together",
                note = "May contain mixed quality mushrooms in one cluster"
            )

            11 -> buildEdibleOysterInfo(
                grade = "DEFECTIVE",
                quality = "⚠️ NOT FOR CONSUMPTION",
                characteristics = characteristics,
                staticFeatures = "Soft, watery, dark spots or minor spoilage",
                bestUse = "Rejected for human food, possible animal feed use",
                safetyNote = "Do not use for eating!"
            )

            12 -> buildMushroomInfo(
                name = "SHIITAKE MUSHROOM (Lentinula edodes)",
                safety = "✅ EDIBLE — MUST BE COOKED",
                characteristics = characteristics,
                staticFeatures = "Brown cap, white gills, white stem",
                culinaryUse = "Grilled, sautéed, soups, tempura",
                habitat = "Cultivated worldwide, especially Asia",
                nutrition = "Immunity boost, dietary fiber, vitamin D",
                safetyNote = "Some people sensitive to raw shiitake; always cook well"
            )

            else -> """
                ❌ UNKNOWN MUSHROOM
                - This mushroom could not be identified
                - Never consume unidentified wild mushrooms!
            """.trimIndent()
        }
    }

    /**
     * ✅ NEW: Build mushroom info with dynamic features
     */
    private fun buildMushroomInfo(
        name: String,
        safety: String,
        characteristics: MushroomCharacteristics?,
        staticFeatures: String,
        habitat: String? = null,
        toxins: String? = null,
        symptoms: String? = null,
        firstAid: String? = null,
        bestUses: String? = null,
        nutrition: String? = null,
        safetyNote: String? = null,
        culinaryUse: String? = null,
        medicinalUse: String? = null,
        use: String? = null,
        culinaryNote: String? = null
    ): String {
        return buildString {
            append("🍄 $name\n")
            append("$safety\n\n")

            // ✅ DYNAMIC Features based on actual photo
            if (characteristics != null) {
                append("📸 Features (from your photo):\n")
                append("- Size: ${characteristics.estimatedSize}\n")
                append("- Colors: ${characteristics.dominantColors.joinToString(", ")}\n")
                append("- Surface: ${characteristics.surfaceCondition}\n")
                if (characteristics.visualDefects.isNotEmpty()) {
                    append("- Notes: ${characteristics.visualDefects.joinToString(", ")}\n")
                }
                append("\n")
                append("📚 Typical Features:\n")
                append("- $staticFeatures\n\n")
            } else {
                append("- Features: $staticFeatures\n\n")
            }

            habitat?.let { append("- Habitat: $it\n") }
            toxins?.let { append("- Toxins: $it\n") }
            symptoms?.let { append("- Symptoms: $it\n") }
            firstAid?.let { append("- First Aid: $it\n") }
            bestUses?.let { append("- Best Uses: $it\n") }
            nutrition?.let { append("- Nutritional Value: $it\n") }
            safetyNote?.let { append("- Safety: $it\n") }
            culinaryUse?.let { append("- Culinary Use: $it\n") }
            medicinalUse?.let { append("- Ethnomedicine: $it\n") }
            use?.let { append("- Use: $it\n") }
            culinaryNote?.let { append("- Culinary: $it\n") }
        }
    }

    /**
     * ✅ NEW: Build oyster mushroom info with dynamic features
     */
    private fun buildEdibleOysterInfo(
        grade: String,
        quality: String,
        characteristics: MushroomCharacteristics?,
        staticFeatures: String,
        bestUse: String,
        note: String? = null,
        safetyNote: String? = null
    ): String {
        return buildString {
            append("🍄 OYSTER MUSHROOM - $grade\n")
            append("$quality\n\n")

            // ✅ DYNAMIC Features based on actual photo
            if (characteristics != null) {
                append("📸 Features (from your photo):\n")
                append("- Size: ${characteristics.estimatedSize}\n")
                append("- Colors: ${characteristics.dominantColors.joinToString(", ")}\n")
                append("- Surface: ${characteristics.surfaceCondition}\n")
                if (characteristics.visualDefects.isNotEmpty()) {
                    append("- Condition: ${characteristics.visualDefects.joinToString(", ")}\n")
                }
                append("\n")
                append("📚 Typical $grade Features:\n")
                append("- $staticFeatures\n\n")
            } else {
                append("- Features: $staticFeatures\n\n")
            }

            append("- Use: $bestUse\n")
            append("- Habitat: Commercial oyster farms, community mushroom houses\n")
            append("- Nutrition: Rich in protein, B vitamins, antioxidants\n")

            note?.let { append("- Note: $it\n") }
            safetyNote?.let { append("- Safety: $it\n") }
        }
    }

    fun getConfidenceLevel(confidence: Float): String {
        return when {
            confidence >= THRESHOLDS[0] -> "HIGH"
            confidence >= THRESHOLDS[1] -> "CAUTION"
            confidence >= THRESHOLDS[2] -> "RETAKE"
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
