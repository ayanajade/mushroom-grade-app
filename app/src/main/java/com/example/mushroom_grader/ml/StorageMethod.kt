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
    ),
    FROZEN(
        "Frozen (-18°C)",
        "Best for long-term storage, may affect texture"
    );

    fun getShelfLifeMultiplier(): Float {
        return when (this) {
            VACUUM_SEALED -> 2.5f
            REFRIGERATED_SEALED -> 1.8f
            REFRIGERATED_OPEN -> 1.0f
            ROOM_TEMPERATURE -> 0.3f
            FROZEN -> 10.0f
        }
    }
}
