package com.example.mushroom_grader.ml

/**
 * Enum representing different mushroom safety categories
 */
enum class MushroomCategory {
    /** Safe to eat - edible mushrooms */
    EDIBLE,

    /** Toxic - poisonous mushrooms */
    POISONOUS,

    /** Not suitable for consumption - neither poisonous nor edible */
    INEDIBLE,

    /** Unknown or unidentified mushroom */
    UNKNOWN
}
