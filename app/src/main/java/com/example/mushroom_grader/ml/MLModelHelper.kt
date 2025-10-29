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
        private const val INPUT_SIZE = 256  // YOLOv8-cls trained at 256x256
        private const val NUM_CLASSES = 12
        private const val CONFIDENCE_THRESHOLD = 0.20f
    }

    private var interpreter: Interpreter? = null
    private var isInitialized = false

    init {
        setupInterpreter()
    }

    private fun setupInterpreter() {
        try {
            val model = FileUtil.loadMappedFile(context, MODEL_PATH)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }

            interpreter = Interpreter(model, options)
            isInitialized = true

            Log.d(TAG, "✅ YOLOv8 Classification Model loaded!")

            try {
                val inputShape = interpreter?.getInputTensor(0)?.shape()
                val outputShape = interpreter?.getOutputTensor(0)?.shape()
                Log.d(TAG, "Input shape: ${inputShape?.contentToString()}")
                Log.d(TAG, "Output shape: ${outputShape?.contentToString()}")
            } catch (e: Exception) {
                Log.e(TAG, "Error reading shapes", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading model", e)
            e.printStackTrace()
            isInitialized = false
        }
    }

    fun classifyImage(bitmap: Bitmap): ClassificationResult? {
        if (!isInitialized || interpreter == null) {
            Log.e(TAG, "❌ Model not initialized")
            return null
        }

        try {
            Log.d(TAG, "Starting YOLOv8 classification...")

            val inputBuffer = preprocessImage(bitmap)
            val output = Array(1) { FloatArray(NUM_CLASSES) }

            interpreter?.run(inputBuffer, output)

            Log.d(TAG, "✅ Classification complete!")
            Log.d(TAG, "Raw output: ${output[0].contentToString()}")

            return processOutput(output[0])

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during classification", e)
            e.printStackTrace()
            return null
        }
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        Log.d(TAG, "Preprocessing... Original: ${bitmap.width}x${bitmap.height}")

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resizedBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // YOLOv8 expects RGB normalized [0, 1]
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        Log.d(TAG, "✅ Preprocessed to ${INPUT_SIZE}x${INPUT_SIZE}")
        return inputBuffer
    }

    private fun processOutput(output: FloatArray): ClassificationResult? {
        var maxIndex = -1
        var maxProb = 0f

        // Log all probabilities for debugging
        Log.d(TAG, "=== ALL CLASS PROBABILITIES ===")
        for (i in output.indices) {
            Log.d(TAG, "Class $i: ${String.format("%.4f", output[i])}")
            if (output[i] > maxProb) {
                maxProb = output[i]
                maxIndex = i
            }
        }
        Log.d(TAG, "================================")

        Log.d(TAG, "✅ Best: Class $maxIndex (${String.format("%.4f", maxProb)})")

        if (maxProb < CONFIDENCE_THRESHOLD) {
            Log.w(TAG, "⚠️ Low confidence: $maxProb")
            return null
        }

        return createClassificationResult(maxIndex, maxProb)
    }

    private fun createClassificationResult(classId: Int, confidence: Float): ClassificationResult {
        // ✅ CORRECT ORDER - Matches YOLOv8 alphabetical training order!
        // Based on your training log class distribution (alphabetical)
        val classes = arrayOf(
            // Class 0: Amanita Pantherina_Amanita Pantherina
            Triple("Amanita Pantherina", true, MushroomCategory.POISONOUS),
            // Class 1: Amanita phalloides_Amanita phalloides
            Triple("Amanita phalloides (Death Cap)", true, MushroomCategory.POISONOUS),
            // Class 2: Amanita_virosa_Amanita_virosa
            Triple("Amanita virosa (Destroying Angel)", true, MushroomCategory.POISONOUS),
            // Class 3: Button Mushroom_Button Mushroom
            Triple("Button Mushroom", false, MushroomCategory.EDIBLE),
            // Class 4: Cinnabar Polypores_Cinnabar Polypores
            Triple("Cinnabar Polypores", true, MushroomCategory.POISONOUS),
            // Class 5: Daedaleopsis confragosa_Daedaleopsis confragosa
            Triple("Daedaleopsis confragosa", true, MushroomCategory.POISONOUS),
            // Class 6: Ganoderma applanatum_Ganoderma applanatum
            Triple("Ganoderma applanatum", true, MushroomCategory.POISONOUS),
            // Class 7: Oyster_Class A_Class A
            Triple("Oyster - Class A", false, MushroomCategory.EDIBLE),
            // Class 8: Oyster_Class B_Class B
            Triple("Oyster - Class B", false, MushroomCategory.EDIBLE),
            // Class 9: Oyster_Class C_Class C
            Triple("Oyster - Class C", false, MushroomCategory.EDIBLE),
            // Class 10: Oyster_Defective_Defective
            Triple("Oyster - Defective", false, MushroomCategory.INEDIBLE),
            // Class 11: Shiitake Mushroom_Shiitake Mushroom
            Triple("Shiitake Mushroom", false, MushroomCategory.EDIBLE)
        )

        val (name, isPoisonous, category) = if (classId < classes.size) {
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
                Amanita Pantherina (Panther Cap)
                
                ⚠️ HIGHLY POISONOUS ⚠️
                
                • Contains ibotenic acid and muscimol
                • Causes severe neurological symptoms
                • Confusion, hallucinations, and convulsions
                • Can be fatal in large doses
                
                Symptoms: Nausea, vomiting, dizziness, loss of coordination
                
                Safety: ⚠️ TOXIC - DO NOT CONSUME
            """.trimIndent()

            1 -> """
                Amanita phalloides (Death Cap)
                
                ⚠️ EXTREMELY DEADLY ⚠️
                
                • Most poisonous mushroom in the world
                • Contains deadly amatoxins
                • Fatal in 50% of cases even with treatment
                • NO EFFECTIVE ANTIDOTE
                • Symptoms appear 6-24 hours after eating
                
                Just ONE mushroom can kill an adult!
                
                Safety: ⚠️ DEADLY - DO NOT CONSUME
            """.trimIndent()

            2 -> """
                Amanita virosa (Destroying Angel)
                
                ⚠️ EXTREMELY DEADLY ⚠️
                
                • Pure white, deadly poisonous mushroom
                • Contains deadly amatoxins
                • NO ANTIDOTE exists
                • Nearly 100% fatal if untreated
                • Causes liver and kidney failure
                
                Often mistaken for edible white mushrooms!
                
                Safety: ⚠️ DEADLY - DO NOT CONSUME
            """.trimIndent()

            3 -> """
                Button Mushroom (Agaricus bisporus)
                
                • Most widely cultivated mushroom worldwide
                • Rich in vitamins B2, B3, B5, and D
                • Good source of protein and fiber
                • Mild, earthy flavor
                • Can be eaten raw or cooked
                
                Nutrition (per 100g):
                • Calories: 22
                • Protein: 3.1g
                • Vitamin D: 0.2μg
                
                Safety: ✓ Safe to eat
            """.trimIndent()

            4 -> """
                Cinnabar Polypores (Pycnoporus cinnabarinus)
                
                ⚠️ NOT EDIBLE ⚠️
                
                • Bright red/orange bracket fungus
                • Grows on dead hardwood
                • Too tough and woody to eat
                • Not poisonous but completely inedible
                • Used in traditional medicine
                
                Identification: Bright cinnabar-red color
                
                Safety: ⚠️ INEDIBLE - DO NOT CONSUME
            """.trimIndent()

            5 -> """
                Daedaleopsis confragosa (Blushing Bracket)
                
                ⚠️ NOT EDIBLE ⚠️
                
                • Bracket fungus found on dead wood
                • Bruises reddish when touched
                • Too tough and corky to eat
                • Not poisonous but inedible
                • Common on willow and birch trees
                
                Identification: Bruises pink/red when scratched
                
                Safety: ⚠️ INEDIBLE - DO NOT CONSUME
            """.trimIndent()

            6 -> """
                Ganoderma applanatum (Artist's Conk)
                
                ⚠️ NOT EDIBLE (Raw) ⚠️
                
                • Large, hard, woody bracket fungus
                • Used in traditional Chinese medicine
                • NOT for raw consumption
                • Too tough to eat directly
                • Can be made into tea or extract
                
                Note: Only medicinal preparations, NOT food
                
                Safety: ⚠️ INEDIBLE - DO NOT CONSUME RAW
            """.trimIndent()

            7 -> """
                Oyster Mushroom - Class A (Premium)
                Grade: Class A
                
                • Popular culinary mushroom
                • Delicate, silky texture
                • Mild, sweet, anise-like flavor
                • High in protein and fiber
                • Rich in B vitamins and minerals
                
                Premium quality characteristics:
                • Large, intact caps
                • Clean appearance
                • No discoloration
                
                Best for: Fresh cooking, grilling, stir-frying
                
                Safety: ✓ Safe to eat
            """.trimIndent()

            8 -> """
                Oyster Mushroom - Class B (Good)
                Grade: Class B
                
                • Popular culinary mushroom
                • Delicate texture
                • Mild, sweet flavor
                • High in protein and fiber
                • Good source of vitamins
                
                Good quality characteristics:
                • Slightly smaller caps
                • Minor imperfections
                • Still fresh
                
                Best for: Soups, stews, pasta dishes
                
                Safety: ✓ Safe to eat
            """.trimIndent()

            9 -> """
                Oyster Mushroom - Class C (Fair)
                Grade: Class C
                
                • Popular culinary mushroom
                • Decent texture
                • Mild flavor
                • Still nutritious
                
                Fair quality characteristics:
                • Smaller size
                • Some discoloration
                • Minor blemishes
                
                Best for: Cooking in sauces, blended dishes
                
                Safety: ✓ Safe to eat
            """.trimIndent()

            10 -> """
                Oyster Mushroom - Defective
                Grade: Defective
                
                • Oyster mushroom with defects
                • May have spots or discoloration
                • Structural damage
                • Still safe but lower quality
                
                Defects may include:
                • Brown spots
                • Torn caps
                • Wilting edges
                
                Not recommended for sale
                
                Safety: ✓ Safe but low quality
            """.trimIndent()

            11 -> """
                Shiitake Mushroom (Lentinula edodes)
                
                • Second most cultivated mushroom worldwide
                • Prized for medicinal properties
                • Rich, savory (umami) flavor
                • Contains lentinan (immune booster)
                • High in B vitamins and vitamin D
                
                Health benefits:
                • Boosts immune system
                • Lowers cholesterol
                • Anti-cancer properties
                
                Best cooked, not raw!
                
                Safety: ✓ Safe to eat (cooked)
            """.trimIndent()

            else -> """
                Unknown Mushroom Species
                
                ⚠️ WARNING ⚠️
                
                • Cannot identify this mushroom
                • DO NOT CONSUME unidentified mushrooms
                • Many poisonous species look like edible ones
                • Consult a mycologist for identification
                
                "When in doubt, throw it out!"
                
                Safety: ⚠️ UNKNOWN - DO NOT EAT
            """.trimIndent()
        }
    }

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
            isInitialized = false
            Log.d(TAG, "Interpreter closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing interpreter", e)
        }
    }
}
