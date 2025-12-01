package com.example.mushroom_grader

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.mushroom_grader.database.AppDatabase
import com.example.mushroom_grader.databinding.ActivityResultBinding
import com.example.mushroom_grader.ml.ClassificationResult
import com.example.mushroom_grader.ml.MLModelHelper
import com.example.mushroom_grader.ml.MushroomCategory
import com.example.mushroom_grader.ml.ShelfLifePredictor
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * ResultActivity - Displays mushroom classification results
 * Features:
 * - Shows mushroom image and classification details
 * - Displays safety status (Edible/Poisonous/Inedible)
 * - Shows grading information
 * - Storage & shelf life predictions with freshness status for edible mushrooms
 * - Share and save functionality
 */
class ResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultBinding
    private lateinit var mlModelHelper: MLModelHelper

    // Classification data
    private var className: String = ""
    private var classId: Int = 0
    private var confidence: Float = 0f
    private var isPoisonous: Boolean = false
    private var category: String = ""
    private var grade: String? = null
    private var imagePath: String? = null

    companion object {
        private const val TAG = "ResultActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "🚀 onCreate started")
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()
        initializeMLModel()
        loadDataAndDisplay()
    }

    /**
     * Setup toolbar with back navigation
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.classification_result)
        }
    }

    /**
     * Initialize ML Model Helper
     */
    private fun initializeMLModel() {
        mlModelHelper = MLModelHelper(this)
    }

    /**
     * Load intent data and display all results
     * ✅ FIXED: Only saves to database if NOT from history
     */
    private fun loadDataAndDisplay() {
        Log.d(TAG, "📥 Loading data from intent...")
        getIntentData()
        displayResults()
        displayStoragePredictions()
        setupClickListeners()

        // ✅ ONLY SAVE IF NOT FROM HISTORY (prevents duplicates!)
        val isFromHistory = intent.getBooleanExtra("fromHistory", false)
        if (!isFromHistory) {
            Log.d(TAG, "💾 New result - saving to database")
            saveToDatabase()
        } else {
            Log.d(TAG, "👁️ Viewing from history - skipping database save")
        }
    }

    /**
     * Get classification data from intent
     */
    private fun getIntentData() {
        className = intent.getStringExtra("className") ?: "Unknown"
        classId = intent.getIntExtra("classId", -1)
        confidence = intent.getFloatExtra("confidence", 0f)
        isPoisonous = intent.getBooleanExtra("isPoisonous", false)
        category = intent.getStringExtra("category") ?: "INEDIBLE"
        grade = intent.getStringExtra("grade")
        imagePath = intent.getStringExtra("imagePath")
        Log.d(TAG, "📦 Data loaded: $className (ID: $classId, Confidence: $confidence)")
    }

    /**
     * Display all classification results
     */
    private fun displayResults() {
        displayImage()
        displayBasicInfo()
        displaySafetyStatus()
        displayGrade()
        displayDetailedInfo()
    }

    /**
     * Display mushroom image from file path
     */
    private fun displayImage() {
        imagePath?.let { path ->
            try {
                val bitmap = BitmapFactory.decodeFile(path)
                binding.ivMushroom.setImageBitmap(bitmap)
                Log.d(TAG, "✅ Image loaded from: $path")
            } catch (ex: Exception) {
                Log.e(TAG, "❌ Failed to load image", ex)
                // Fallback to default icon if image loading fails
                binding.ivMushroom.setImageResource(android.R.mipmap.sym_def_app_icon)
            }
        }
    }

    /**
     * Display basic classification information
     */
    private fun displayBasicInfo() {
        binding.tvMushroomName.text = className
        val confidencePercent = String.format(Locale.getDefault(), "%.2f%%", confidence * 100)
        binding.tvConfidence.text = getString(R.string.confidence_format, confidencePercent)
    }

    /**
     * Display safety status with color coding
     */
    private fun displaySafetyStatus() {
        when {
            isPoisonous -> {
                setSafetyCard(
                    android.R.color.holo_red_dark,
                    R.string.poisonous_title,
                    R.string.poisonous_message
                )
            }
            category == MushroomCategory.INEDIBLE.name -> {
                setSafetyCard(
                    android.R.color.holo_orange_dark,
                    R.string.inedible_title,
                    R.string.inedible_message
                )
            }
            else -> {
                setSafetyCard(
                    android.R.color.holo_green_dark,
                    R.string.safe_to_eat_title,
                    R.string.safe_to_eat_message
                )
            }
        }
    }

    /**
     * Helper method to set safety card appearance
     */
    private fun setSafetyCard(colorRes: Int, titleRes: Int, messageRes: Int) {
        binding.cardSafety.setCardBackgroundColor(
            ContextCompat.getColor(this, colorRes)
        )
        binding.tvSafetyTitle.setText(titleRes)
        binding.tvSafetyMessage.setText(messageRes)
        val whiteColor = ContextCompat.getColor(this, android.R.color.white)
        binding.tvSafetyTitle.setTextColor(whiteColor)
        binding.tvSafetyMessage.setTextColor(whiteColor)
    }

    /**
     * Display grade information if available
     */
    private fun displayGrade() {
        if (grade != null) {
            binding.cardGrade.visibility = View.VISIBLE
            binding.tvGradeValue.text = grade
        } else {
            binding.cardGrade.visibility = View.GONE
        }
    }

    /**
     * Display detailed mushroom information
     */
    private fun displayDetailedInfo() {
        val detailedInfo = mlModelHelper.getMushroomInfo(classId)
        binding.tvDetailedInfo.text = detailedInfo
        binding.tvCategory.text = getString(R.string.category_format, category)
    }

    /**
     * Display storage predictions for edible mushrooms
     * Shows a table with different storage methods, shelf life, and freshness indicators
     */
    private fun displayStoragePredictions() {
        // Only show for edible mushrooms
        if (category != MushroomCategory.EDIBLE.name) {
            binding.cardStoragePredictions.visibility = View.GONE
            return
        }

        try {
            binding.cardStoragePredictions.visibility = View.VISIBLE
            binding.layoutStorageRows.removeAllViews()

            // Get all storage predictions
            val predictions = ShelfLifePredictor.getAllStoragePredictions(className)

            // Create table rows for each storage method
            predictions.forEach { prediction ->
                addStorageRow(prediction)
            }
        } catch (ex: Exception) {
            Log.e(TAG, "❌ Failed to display storage predictions", ex)
            // Hide card if prediction fails
            binding.cardStoragePredictions.visibility = View.GONE
        }
    }

    /**
     * Add a single storage method row to the table with freshness indicator
     */
    private fun addStorageRow(prediction: com.example.mushroom_grader.ml.ShelfLifeData) {
        val rowView = layoutInflater.inflate(
            R.layout.item_storage_row,
            binding.layoutStorageRows,
            false
        )

        // Get icon for storage method
        val icon = getStorageMethodIcon(prediction.storageMethod.name)

        // Set method name with icon
        val methodNameView = rowView.findViewById<TextView>(R.id.textStorageMethodName)
        methodNameView.text = getString(
            R.string.storage_method_format,
            icon,
            prediction.storageMethod.displayName
        )

        // Set shelf life days with color coding
        val daysView = rowView.findViewById<TextView>(R.id.textStorageDays)
        daysView.text = getString(R.string.days_format, prediction.calculatedDays)

        // Color code based on duration
        val color = getShelfLifeColor(prediction.calculatedDays)
        daysView.setTextColor(ContextCompat.getColor(this, color))

        // NEW: Set freshness status indicator bar
        val freshnessIndicator = rowView.findViewById<View>(R.id.viewFreshnessIndicator)
        val freshnessStatus = prediction.getFreshnessStatus()
        freshnessIndicator.setBackgroundColor(
            ContextCompat.getColor(this, freshnessStatus.color)
        )

        // Click to show detailed info
        rowView.setOnClickListener {
            showDetailedStorageInfo(prediction)
        }

        binding.layoutStorageRows.addView(rowView)
    }

    /**
     * Get emoji icon for storage method
     */
    private fun getStorageMethodIcon(methodName: String): String {
        return when (methodName) {
            "VACUUM_SEALED" -> "🔒"
            "REFRIGERATED_SEALED" -> "📦"
            "REFRIGERATED_OPEN" -> "🧊"
            "ROOM_TEMPERATURE" -> "🌡️"
            "FROZEN" -> "❄️"
            else -> "📋"
        }
    }

    /**
     * Get color for shelf life based on duration
     */
    private fun getShelfLifeColor(days: Int): Int {
        return when {
            days >= 10 -> android.R.color.holo_green_dark
            days >= 5 -> android.R.color.holo_orange_dark
            else -> android.R.color.holo_red_dark
        }
    }

    /**
     * Show detailed storage information dialog with freshness status
     */
    private fun showDetailedStorageInfo(prediction: com.example.mushroom_grader.ml.ShelfLifeData) {
        val message = buildStorageInfoMessage(prediction)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.storage_details)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    /**
     * Build storage information message with freshness status
     */
    private fun buildStorageInfoMessage(prediction: com.example.mushroom_grader.ml.ShelfLifeData): String {
        val freshnessStatus = prediction.getFreshnessStatus()
        val daysRemaining = prediction.getDaysRemaining()

        return buildString {
            append("📦 ${prediction.storageMethod.displayName}\n\n")

            // NEW: Show freshness status
            append("🔔 Freshness: ${freshnessStatus.message}\n")
            append("📊 Days Remaining: $daysRemaining days\n\n")

            append(getString(R.string.shelf_life_format, prediction.calculatedDays))
            append("\n")
            append(getString(R.string.expires_format, prediction.getFormattedExpirationDate()))
            append("\n")
            append(getString(R.string.temperature_format, prediction.storageTemperature))
            append("\n\n")

            append(getString(R.string.storage_tips))
            append(":\n")
            prediction.tips.forEachIndexed { index, tip ->
                append("${index + 1}. $tip\n")
            }

            append("\n")
            append(getString(R.string.warnings))
            append(":\n")
            prediction.warnings.forEach { warning ->
                append("⚠️ $warning\n")
            }
        }
    }

    /**
     * Setup all button click listeners
     */
    private fun setupClickListeners() {
        binding.btnShare.setOnClickListener {
            shareResult()
        }

        binding.btnTakeAnother.setOnClickListener {
            navigateToCameraActivity()
        }

        binding.btnViewHistory.setOnClickListener {
            navigateToHistoryActivity()
        }

        binding.btnBackHome.setOnClickListener {
            navigateToMainActivity()
        }

        binding.btnMoreInfo.setOnClickListener {
            showMoreInfoDialog()
        }
    }

    /**
     * Share classification result via share intent
     */
    private fun shareResult() {
        val shareText = buildShareMessage()
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject, className))
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_result)))
    }

    /**
     * Build share message text
     */
    private fun buildShareMessage(): String {
        return buildString {
            append(getString(R.string.share_result_header))
            append("\n\n")
            append(getString(R.string.share_species, className))
            append("\n")

            val confidencePercent = String.format(Locale.getDefault(), "%.2f%%", confidence * 100)
            append(getString(R.string.share_confidence, confidencePercent))
            append("\n")

            val safetyText = if (isPoisonous) {
                getString(R.string.poisonous)
            } else {
                getString(R.string.safe_to_eat)
            }
            append(getString(R.string.share_safety, safetyText))
            append("\n")

            grade?.let {
                append(getString(R.string.share_grade, it))
                append("\n")
            }

            append("\n")
            append(getString(R.string.app_name))
        }
    }

    /**
     * Navigate to CameraActivity for new classification
     */
    private fun navigateToCameraActivity() {
        startActivity(Intent(this, CameraActivity::class.java))
        finish()
    }

    /**
     * Navigate to HistoryActivity
     */
    private fun navigateToHistoryActivity() {
        startActivity(Intent(this, HistoryActivity::class.java))
        finish()
    }

    /**
     * Navigate to MainActivity (Home)
     */
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }

    /**
     * Show detailed mushroom information dialog
     */
    private fun showMoreInfoDialog() {
        val message = mlModelHelper.getMushroomInfo(classId)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.detailed_information)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    /**
     * Save classification result to database
     * ✅ NOW WITH FULL LOGGING AND ERROR HANDLING
     */
    private fun saveToDatabase() {
        Log.d(TAG, "💾 saveToDatabase() called")
        lifecycleScope.launch {
            try {
                Log.d(TAG, "📦 Creating ClassificationResult object...")
                withContext(Dispatchers.IO) {
                    val result = ClassificationResult(
                        className = className,
                        classId = classId,
                        confidence = confidence,
                        isPoisonous = isPoisonous,
                        category = MushroomCategory.valueOf(category),
                        grade = grade,
                        imagePath = imagePath
                    )

                    Log.d(TAG, "📦 Result object created: $className")
                    val database = AppDatabase.getDatabase(applicationContext)
                    Log.d(TAG, "✅ Database instance obtained")

                    val rowId = database.resultDao().insertResult(result)
                    Log.d(TAG, "✅ SAVED to database! Row ID: $rowId")
                }

                // Show success toast
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ResultActivity,
                        "✓ Saved to history",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "❌ FAILED to save to database!", ex)
                Log.e(TAG, "❌ Error: ${ex.message}")
                Log.e(TAG, "❌ Stack trace: ${ex.stackTraceToString()}")

                // Show error toast
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ResultActivity,
                        "Failed to save: ${ex.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Handle toolbar back navigation
     */
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /**
     * Clean up ML model resources
     */
    override fun onDestroy() {
        super.onDestroy()
        mlModelHelper.close()
        Log.d(TAG, "🔚 Activity destroyed")
    }
}