package com.example.mushroom_grader.ml


import java.text.SimpleDateFormat // Import for date formatting
import java.util.Calendar
import java.util.Date
import java.util.Locale // Import for date formatting locale

data class ShelfLifeData(
    val mushroomName: String,
    val storageMethod: StorageMethod,
    val baseDays: Int,
    val calculatedDays: Int,
    val expirationDate: Date,
    val storageTemperature: String,
    val tips: List<String>,
    val warnings: List<String>
) {
    /**
     * Calculates the number of days remaining until the expiration date.
     * This version uses the older Calendar API as it was in your original file.
     */
    fun getDaysRemaining(): Int {
        val now = Calendar.getInstance()
        val expiry = Calendar.getInstance().apply { time = expirationDate }

        // To avoid issues with time of day, we clear the time fields
        now.set(Calendar.HOUR_OF_DAY, 0)
        now.set(Calendar.MINUTE, 0)
        now.set(Calendar.SECOND, 0)
        now.set(Calendar.MILLISECOND, 0)

        expiry.set(Calendar.HOUR_OF_DAY, 0)
        expiry.set(Calendar.MINUTE, 0)
        expiry.set(Calendar.SECOND, 0)
        expiry.set(Calendar.MILLISECOND, 0)

        val diffMillis = expiry.timeInMillis - now.timeInMillis
        // Calculate the number of full days
        return (diffMillis / (1000 * 60 * 60 * 24)).toInt()
    }

    /**
     * Formats the expiration date into a readable string like "November 28, 2025".
     */
    fun getFormattedExpirationDate(): String {
        val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        return sdf.format(expirationDate)
    }

    /**
     * Determines the freshness status based on the number of days remaining.
     */
    fun getFreshnessStatus(): FreshnessStatus {
        val daysRemaining = getDaysRemaining()
        return when {
            daysRemaining < 0 -> FreshnessStatus.EXPIRED
            daysRemaining <= 2 -> FreshnessStatus.CRITICAL
            daysRemaining <= 5 -> FreshnessStatus.WARNING
            else -> FreshnessStatus.FRESH
        }
    }
}
// The 'FreshnessStatus' enum has been removed from this file to prevent redeclaration errors.
// It should exist only in 'FreshnessStatus.kt'.
