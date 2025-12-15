package com.example.mushroom_grader.database

import androidx.room.TypeConverter
import com.example.mushroom_grader.ml.MushroomCategory

object Converters {
    @TypeConverter
    fun fromMushroomCategory(category: MushroomCategory): String {
        return category.name
    }

    @TypeConverter
    fun toMushroomCategory(categoryName: String): MushroomCategory {
        return try {
            MushroomCategory.valueOf(categoryName)
        } catch (e: IllegalArgumentException) {
            MushroomCategory.UNKNOWN
        }
    }
}
