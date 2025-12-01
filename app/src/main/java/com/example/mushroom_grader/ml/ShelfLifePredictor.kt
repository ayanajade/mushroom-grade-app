package com.example.mushroom_grader.ml

import android.util.Log
import java.util.Calendar

class ShelfLifePredictor {

    companion object {
        private const val TAG = "ShelfLifePredictor"

        // Base shelf life in days for refrigerated open storage
        // These are for FRESH mushrooms at optimal condition (100% freshness)
        private val BASE_SHELF_LIFE = mapOf(
            "Button Mushroom" to 7,
            "Oyster - Class A" to 5,
            "Oyster - Class B" to 4,
            "Oyster - Class C" to 3,
            "Oyster - Cluster" to 4,
            "Oyster - Defective" to 1,
            "Shiitake Mushroom" to 10
        )

        /**
         * ✅ NEW: Predict shelf life based on actual freshness from image analysis
         * Now uses freshness multiplier from visual analysis
         */
        fun predictShelfLife(
            mushroomName: String,
            storageMethod: StorageMethod,
            freshnessMultiplier: Float = 1.0f,  // ← NEW parameter from image analysis
            classificationDate: Long = System.currentTimeMillis()
        ): ShelfLifeData {

            // Get base days for this mushroom type
            val baseDays = BASE_SHELF_LIFE[mushroomName] ?: 3

            // Get storage method multiplier
            val storageMultiplier = storageMethod.getShelfLifeMultiplier()

            // ✅ NEW: Apply freshness multiplier from image analysis
            // Example: Fresh mushroom (1.0x) × Refrigerated (1.0x) = 5 days
            //          Declining (0.5x) × Refrigerated (1.0x) = 2.5 days
            val calculatedDays = (baseDays * storageMultiplier * freshnessMultiplier).toInt().coerceAtLeast(0)

            Log.d(TAG, "📊 Shelf life calculation:")
            Log.d(TAG, "  Base: $baseDays days")
            Log.d(TAG, "  Storage multiplier: ${storageMultiplier}x")
            Log.d(TAG, "  Freshness multiplier: ${freshnessMultiplier}x")
            Log.d(TAG, "  Final: $calculatedDays days")

            // Calculate expiration date
            val expirationDate = Calendar.getInstance().apply {
                timeInMillis = classificationDate
                add(Calendar.DAY_OF_YEAR, calculatedDays)
            }.time

            val temperature = getRecommendedTemperature(storageMethod)
            val tips = getStorageTips(mushroomName, storageMethod, freshnessMultiplier)
            val warnings = getWarnings(mushroomName, storageMethod, freshnessMultiplier)

            return ShelfLifeData(
                mushroomName = mushroomName,
                storageMethod = storageMethod,
                baseDays = baseDays,
                calculatedDays = calculatedDays,
                expirationDate = expirationDate,
                storageTemperature = temperature,
                tips = tips,
                warnings = warnings
            )
        }

        /**
         * ✅ NEW: Get all storage predictions with freshness adjustment
         */
        fun getAllStoragePredictions(
            mushroomName: String,
            freshnessMultiplier: Float = 1.0f,  // ← NEW parameter
            classificationDate: Long = System.currentTimeMillis()
        ): List<ShelfLifeData> {
            return StorageMethod.values().map { method ->
                predictShelfLife(mushroomName, method, freshnessMultiplier, classificationDate)
            }
        }

        private fun getRecommendedTemperature(method: StorageMethod): String {
            return when (method) {
                StorageMethod.VACUUM_SEALED -> "2-4°C (35-39°F)"
                StorageMethod.REFRIGERATED_SEALED -> "2-4°C (35-39°F)"
                StorageMethod.REFRIGERATED_OPEN -> "2-4°C (35-39°F)"
                StorageMethod.ROOM_TEMPERATURE -> "18-22°C (64-72°F)"
                StorageMethod.FROZEN -> "-18°C (0°F) or below"
            }
        }

        /**
         * ✅ UPDATED: Storage tips now consider freshness level
         */
        private fun getStorageTips(
            mushroomName: String,
            method: StorageMethod,
            freshnessMultiplier: Float
        ): List<String> {
            val commonTips = mutableListOf<String>()

            // ✅ NEW: Add freshness-specific tip first
            when {
                freshnessMultiplier >= 0.9f -> {
                    commonTips.add("✨ Mushrooms are in excellent condition")
                }
                freshnessMultiplier >= 0.7f -> {
                    commonTips.add("👍 Mushrooms are fresh, use within recommended time")
                }
                freshnessMultiplier >= 0.5f -> {
                    commonTips.add("⏰ Mushrooms showing age, consume within shorter timeframe")
                }
                freshnessMultiplier >= 0.3f -> {
                    commonTips.add("⚠️ Mushrooms are declining, use immediately")
                }
                else -> {
                    commonTips.add("🚨 CRITICAL: Mushrooms may be past optimal freshness")
                }
            }

            when (method) {
                StorageMethod.VACUUM_SEALED -> {
                    commonTips.add("Ensure complete air removal before sealing")
                    commonTips.add("Store in coldest part of refrigerator")
                    commonTips.add("Check seal integrity regularly")
                    commonTips.add("Do not refreeze after thawing")
                }

                StorageMethod.REFRIGERATED_SEALED -> {
                    commonTips.add("Use airtight container with tight-fitting lid")
                    commonTips.add("Place paper towel at bottom to absorb moisture")
                    commonTips.add("Check daily for signs of spoilage")
                    commonTips.add("Keep away from strong-smelling foods")
                }

                StorageMethod.REFRIGERATED_OPEN -> {
                    commonTips.add("Store in paper bag (not plastic)")
                    commonTips.add("Paper allows mushrooms to breathe")
                    commonTips.add("Place in crisper drawer")
                    commonTips.add("Inspect daily and remove any slimy pieces")
                }

                StorageMethod.ROOM_TEMPERATURE -> {
                    commonTips.add("Only for same-day use")
                    commonTips.add("Keep in cool, dark place")
                    commonTips.add("Avoid direct sunlight")
                    commonTips.add("Use within 6-8 hours")
                }

                StorageMethod.FROZEN -> {
                    commonTips.add("Clean and slice before freezing")
                    commonTips.add("Blanch for 1-2 minutes (optional but recommended)")
                    commonTips.add("Use freezer-safe bags or containers")
                    commonTips.add("Label with date and mushroom type")
                    commonTips.add("Thaw in refrigerator, not at room temperature")
                }
            }

            // Mushroom-specific tips
            when (mushroomName) {
                "Oyster - Class A", "Oyster - Class B", "Oyster - Class C", "Oyster - Cluster" -> {
                    commonTips.add("Oyster mushrooms are delicate - handle gently")
                    if (freshnessMultiplier >= 0.8f) {
                        commonTips.add("Best consumed fresh within 2-3 days")
                    }
                }

                "Shiitake Mushroom" -> {
                    commonTips.add("Shiitake have longer shelf life than oyster")
                    commonTips.add("Can be dried for extended storage")
                }

                "Button Mushroom" -> {
                    commonTips.add("One of the most stable mushroom varieties")
                    commonTips.add("Can handle slightly longer storage")
                }
            }

            return commonTips
        }

        /**
         * ✅ UPDATED: Warnings now consider freshness level
         */
        private fun getWarnings(
            mushroomName: String,
            method: StorageMethod,
            freshnessMultiplier: Float
        ): List<String> {
            val warnings = mutableListOf<String>()

            // ✅ NEW: Freshness-based warnings
            when {
                freshnessMultiplier < 0.3f -> {
                    warnings.add("🚨 CRITICAL: Mushrooms appear significantly aged")
                    warnings.add("🚨 Consume immediately or discard if any off-smell")
                    warnings.add("🚨 Shortened shelf life due to current condition")
                }
                freshnessMultiplier < 0.5f -> {
                    warnings.add("⚠️ WARNING: Mushrooms showing signs of age")
                    warnings.add("⚠️ Use within 1-2 days regardless of storage method")
                }
                freshnessMultiplier < 0.7f -> {
                    warnings.add("⚠️ Mushrooms are not farm-fresh")
                    warnings.add("⚠️ Shelf life may be shorter than indicated")
                }
            }

            // Standard warnings
            warnings.add("⚠️ Discard if mushrooms are slimy or have dark spots")
            warnings.add("⚠️ Strong ammonia smell indicates spoilage")
            warnings.add("⚠️ Wrinkled or dried out mushrooms lose quality")

            when (method) {
                StorageMethod.ROOM_TEMPERATURE -> {
                    warnings.add("🚨 HIGH RISK: Room temperature storage not recommended")
                    warnings.add("🚨 Rapid bacterial growth at 20°C+")
                    warnings.add("🚨 Use immediately or refrigerate")
                }

                StorageMethod.FROZEN -> {
                    warnings.add("⚠️ Texture may become softer after thawing")
                    warnings.add("⚠️ Best used in cooked dishes after freezing")
                }

                else -> {
                    if (freshnessMultiplier >= 0.7f) {
                        warnings.add("✓ This is a recommended storage method")
                    }
                }
            }

            // Grade-specific warnings
            if (mushroomName.contains("Class C") || mushroomName.contains("Cluster") || mushroomName.contains("Defective")) {
                warnings.add("⚠️ Lower grade - consume within shorter timeframe")
            }

            return warnings
        }
    }
}
