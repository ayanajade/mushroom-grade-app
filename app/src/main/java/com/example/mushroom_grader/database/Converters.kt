package com.example.mushroom_grader.database

import androidx.room.TypeConverter
import com.example.mushroom_grader.ml.MushroomCategory

class Converters {
    @TypeConverter
    fun fromMushroomCategory(category: MushroomCategory): String {
        return category.name
    }

    @TypeConverter
    fun toMushroomCategory(value: String): MushroomCategory {
        return MushroomCategory.valueOf(value)
    }
}
