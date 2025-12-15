package com.example.mushroom_grader.ml

enum class StorageMethod(val displayName: String, val description: String) {
    VACUUM_SEALED(
        "Vacuum Sealed",
        "Removes air, prevents oxidation and bacterial growth"
    ),
    REFRIGERATED_SEALED(
        "Refrigerated (Sealed Container)",
        "Stored in airtight container at 2-4°C"
    ),
    REFRIGERATED_OPEN(
        "Refrigerated (Paper Bag/Open)",
        "Allows breathing, moderate preservation"
    ),
    ROOM_TEMPERATURE(
        "Room Temperature",
        "Not recommended for long storage"
    );

    // ✅ CALIBRATED: Research-based multipliers
    fun getShelfLifeMultiplier(): Float {
        return when (this) {
            VACUUM_SEALED -> 3.6f        // ✅ 5→18 days
            REFRIGERATED_SEALED -> 1.6f  // ✅ 5→8 days
            REFRIGERATED_OPEN -> 1.0f    // ✓ Baseline
            ROOM_TEMPERATURE -> 0.4f     // ✅ 5→2 days

        }
    }

}
