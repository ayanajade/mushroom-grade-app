package com.example.mushroom_grader.ml

import java.util.Calendar

class ShelfLifePredictor {

    companion object {
        // Base shelf life in days for refrigerated open storage
        private val BASE_SHELF_LIFE = mapOf(
            "Button Mushroom" to 7,
            "Oyster - Class A" to 5,
            "Oyster - Class B" to 4,
            "Oyster - Class C" to 3,
            "Oyster - Cluster" to 4,
            "Shiitake Mushroom" to 10
        )

        fun predictShelfLife(
            mushroomName: String,
            storageMethod: StorageMethod,
            classificationDate: Long = System.currentTimeMillis()
        ): ShelfLifeData {
            val baseDays = BASE_SHELF_LIFE[mushroomName] ?: 3
            val multiplier = storageMethod.getShelfLifeMultiplier()
            val calculatedDays = (baseDays * multiplier).toInt()

            val expirationDate = Calendar.getInstance().apply {
                timeInMillis = classificationDate
                add(Calendar.DAY_OF_YEAR, calculatedDays)
            }.time

            val temperature = getRecommendedTemperature(storageMethod)
            val tips = getStorageTips(mushroomName, storageMethod)
            val warnings = getWarnings(mushroomName, storageMethod)

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

        fun getAllStoragePredictions(
            mushroomName: String,
            classificationDate: Long = System.currentTimeMillis()
        ): List<ShelfLifeData> {
            return StorageMethod.values().map { method ->
                predictShelfLife(mushroomName, method, classificationDate)
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

        private fun getStorageTips(mushroomName: String, method: StorageMethod): List<String> {
            val commonTips = mutableListOf<String>()

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
                    commonTips.add("Best consumed fresh within 2-3 days")
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

        private fun getWarnings(mushroomName: String, method: StorageMethod): List<String> {
            val warnings = mutableListOf<String>()

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
                    warnings.add("✓ This is a recommended storage method")
                }
            }

            // Grade-specific warnings
            if (mushroomName.contains("Class C") || mushroomName.contains("Cluster")) {
                warnings.add("⚠️ Lower grade - consume within shorter timeframe")
            }

            return warnings
        }
    }
}
