package com.example.mushroom_grader.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MLModelHelper(private val context: Context) {

    companion object {
        private const val TAG = "MLModelHelper"
        private const val MODEL_PATH = "mushroom_classifier.tflite"
        private const val INPUT_SIZE = 256
        private const val NUM_CLASSES = 12
        private val THRESHOLDS = listOf(
            0.90f, // High confidence - Show immediately
            0.85f, // Caution
            0.60f  // Ask to retake
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
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            isInitialized = false
        }
    }

    fun classifyImage(bitmap: Bitmap): ClassificationResult? {
        if (!isInitialized || interpreter == null) return null

        return try {
            val inputBuffer = preprocessImage(bitmap)
            val output = Array(1) { FloatArray(NUM_CLASSES) }
            interpreter?.run(inputBuffer, output)
            processOutput(output[0])
        } catch (e: Exception) {
            null
        }
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
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
        return inputBuffer
    }

    private fun processOutput(output: FloatArray): ClassificationResult? {
        var maxIndex = -1
        var maxProb = 0f
        for (i in output.indices) {
            if (output[i] > maxProb) {
                maxProb = output[i]
                maxIndex = i
            }
        }
        if (maxProb < THRESHOLDS[2]) {
            return null // TOO LOW
        }
        return createClassificationResult(maxIndex, maxProb)
    }

    private fun createClassificationResult(classId: Int, confidence: Float): ClassificationResult {
        val classes = arrayOf(
            Triple("Amanita Pantherina", true, MushroomCategory.POISONOUS),
            Triple("Amanita phalloides", true, MushroomCategory.POISONOUS),
            Triple("Amanita virosa", true, MushroomCategory.POISONOUS),
            Triple("Button Mushroom", false, MushroomCategory.EDIBLE),
            Triple("Cinnabar Polypores", true, MushroomCategory.POISONOUS),
            Triple("Daedaleopsis confragosa", true, MushroomCategory.POISONOUS),
            Triple("Ganoderma applanatum", true, MushroomCategory.POISONOUS),
            Triple("Oyster - Class A", false, MushroomCategory.EDIBLE),
            Triple("Oyster - Class B", false, MushroomCategory.EDIBLE),
            Triple("Oyster - Class C", false, MushroomCategory.EDIBLE),
            Triple("Oyster - Defective", false, MushroomCategory.INEDIBLE),
            Triple("Shiitake Mushroom", false, MushroomCategory.EDIBLE)
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

    fun getMushroomInfo(classId: Int): String {
        return when (classId) {
            0 -> """
                🍄 AMANITA PANTHERINA (Panther Cap)
                ⚠️ HIGHLY POISONOUS - DO NOT CONSUME
                - Identification: Brown cap w/ white warts, white gills & stem
                - Habitat: Deciduous/coniferous forests in North America, Europe, Asia
                - Toxins: Ibotenic acid, muscimol
                - Symptoms: Confusion, hallucinations, vomiting
                - First Aid: Induce vomiting, seek emergency care immediately
            """.trimIndent()
            1 -> """
                🍄 AMANITA PHALLOIDES (Death Cap)
                ⚠️ EXTREMELY DEADLY - DO NOT CONSUME
                - Identification: Pale yellow/green cap, white gills, volva at base
                - Habitat: Oak, beech forests, introduced worldwide
                - Toxins: Amatoxins
                - Symptoms: Severe vomiting, abdominal pain, liver failure
                - First Aid: Hospitalization, activated charcoal, liver support
            """.trimIndent()
            2 -> """
                🍄 AMANITA VIROSA (Destroying Angel)
                ⚠️ EXTREMELY DEADLY - DO NOT CONSUME
                - Identification: Pure white, smooth cap, ring, bulbous base
                - Habitat: Deciduous/evergreen woods, widespread in Europe, Asia
                - Toxins: Amatoxins
                - Symptoms: Delayed onset, vomiting, kidney/liver failure
                - First Aid: Immediate medical attention, treat as medical emergency
            """.trimIndent()
            3 -> """
                🍄 BUTTON MUSHROOM (Agaricus bisporus)
                ✅ EDIBLE - SAFE TO EAT
                - Features: White or brown cap, pink to brown gills, pleasant smell
                - Habitat: Commercially grown, global cultivation
                - Best Uses: Salads, soups, pizzas, sauteed, versatile in cuisine
                - Nutritional Value: High in B vitamins, selenium, low-calorie protein source
                - Safety: Safe when fresh and properly cooked
            """.trimIndent()
            4 -> """
                🍄 CINNABAR POLYPORES (Pycnoporus cinnabarinus)
                ⚠️ INEDIBLE — NOT TOXIC BUT TOO TOUGH
                - Features: Bright red, flat shelf-like fruiting body, hard texture
                - Habitat: On dead hardwood worldwide, tropics to temperate
                - Culinary Use: Not edible, used for dyes/pigments
                - Ethnomedicine: Limited use in traditional medicine
            """.trimIndent()
            5 -> """
                🍄 DAEDALEOPSIS CONFRAGOSA (Blushing Bracket)
                ⚠️ INEDIBLE
                - Features: Tan/grey top, reddish bruising, maze-like pores beneath
                - Habitat: Fallen willow, birch, hardwood logs throughout Eurasia
                - Culinary: Not eaten; used for decoration/woodcraft
            """.trimIndent()
            6 -> """
                🍄 GANODERMA APPLANATUM (Artist’s Conk)
                ⚠️ NOT FOR CULINARY USE - MEDICINAL REMEDY ONLY
                - Features: Brown crust, very woody, hard perennial bracket
                - Habitat: On dead/dying hardwood globally
                - Use: Traditional medicine (immune boosting, anti-inflammatory)
                - Culinary: Not edible; boiled for extracts/teas (very bitter)
            """.trimIndent()
            7 -> """
                🍄 OYSTER MUSHROOM - CLASS A (Premium)
                ✅ EDIBLE — PREMIUM QUALITY
                - Features: Firm, white, unblemished, large fan-shaped cap
                - Best Use: Fresh, grilling, roasting, best flavor and texture
                - Habitat: Cultivated on logs or commercial substrate
                - Nutrition: Rich in protein, B vitamins, antioxidants
            """.trimIndent()
            8 -> """
                🍄 OYSTER MUSHROOM - CLASS B (Good)
                ✅ EDIBLE — GOOD QUALITY
                - Features: Minor defects, still firm and fresh, slightly smaller
                - Use: Suitable for cooked dishes, stir-fries, soups, stews
                - Habitat: Commercial oyster farms, community mushroom houses
            """.trimIndent()
            9 -> """
                🍄 OYSTER MUSHROOM - CLASS C (Fair)
                ✅ EDIBLE — FAIR/COOK ONLY
                - Features: Small, thin, slight discoloration
                - Use: Must be cooked, best in stews, sauces
                - Habitat: Older flushes, variable cultivation
            """.trimIndent()
            10 -> """
                🍄 OYSTER MUSHROOM - DEFECTIVE
                ⚠️ NOT FOR CONSUMPTION
                - Features: Soft, watery, dark spots or minor spoilage
                - Culinary: Rejected for human food, possible animal feed use
                - Safety: Do not use for eating!
            """.trimIndent()
            11 -> """
                🍄 SHIITAKE MUSHROOM (Lentinula edodes)
                ✅ EDIBLE — MUST BE COOKED
                - Features: Brown cap, white gills, white stem
                - Culinary: Grilled, sautéed, soups, tempura
                - Habitat: Cultivated worldwide, especially Asia
                - Nutrition: Immunity boost, dietary fiber, vitamin D
                - Note: Some people sensitive to raw shiitake; always cook well
            """.trimIndent()
            else -> """
                ❌ UNKNOWN MUSHROOM
                - This mushroom could not be identified
                - Never consume unidentified wild mushrooms!
            """.trimIndent()
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
        } catch (_: Exception) { }
    }
}
