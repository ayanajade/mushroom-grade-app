package com.example.mushroom_grader.ml

import androidx.annotation.ColorRes
import com.example.mushroom_grader.R

/**
 * Defines the freshness level of a mushroom and provides associated UI data.
 * Each status has a color and a user-facing message.
 */
enum class FreshnessStatus(
    @ColorRes val color: Int,
    val message: String
) {
    /**
     * The item is fresh and has a significant amount of shelf life left.
     */
    FRESH(R.color.green_600, "Fresh - Safe to consume"),

    /**
     * The item is still good but should be consumed soon.
     */
    WARNING(R.color.orange_600, "Consume soon"),

    /**
     * The item is very close to expiring and should be used immediately.
     */
    CRITICAL(R.color.red_600, "Use immediately or discard"),

    /**
     * The item has passed its expiration date and should not be consumed.
     */
    EXPIRED(R.color.gray_700, "Expired - Do not consume")
}
