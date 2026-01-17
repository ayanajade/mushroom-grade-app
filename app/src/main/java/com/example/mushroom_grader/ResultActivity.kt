package com.example.mushroom_grader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.mushroom_grader.database.AppDatabase
import com.example.mushroom_grader.databinding.ActivityResultBinding
import com.example.mushroom_grader.ml.ClassificationResult
import com.example.mushroom_grader.ml.FreshnessAnalyzer
import com.example.mushroom_grader.ml.MLModelHelper
import com.example.mushroom_grader.ml.MushroomCategory
import com.example.mushroom_grader.ml.MushroomCharacteristicsAnalyzer
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
 * - ✅ Analyzes actual mushroom freshness from image
 * - ✅ Analyzes visual characteristics (size, color, surface)
 * - ✅ Storage & shelf life predictions based on visual freshness
 * - Share and save functionality
 */
class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private lateinit var mlModelHelper: MLModelHelper
    private lateinit var freshnessAnalyzer: FreshnessAnalyzer
    private lateinit var characteristicsAnalyzer: MushroomCharacteristicsAnalyzer

    // Classification data
    private var className: String = ""
    private var classId: Int = 0
    private var confidence: Float = 0f
    private var isPoisonous: Boolean = false
    private var category: String = ""
    private var grade: String? = null
    private var imagePath: String? = null
    private var imageBitmap: Bitmap? = null

    // ✅ NEW: Store characteristics for "More Info" dialog
    private var currentCharacteristics: com.example.mushroom_grader.ml.MushroomCharacteristics? = null

    companion object {
        private const val TAG = "ResultActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "🚀 onCreate started")

        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        initializeAnalyzers()
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
     * ✅ UPDATED: Initialize ML Model Helper and analyzers
     */
    private fun initializeAnalyzers() {
        mlModelHelper = MLModelHelper(this)
        freshnessAnalyzer = FreshnessAnalyzer()
        characteristicsAnalyzer = MushroomCharacteristicsAnalyzer()
        Log.d(TAG, "✅ ML Model and Analyzers initialized")
    }

    /**
     * Load intent data and display all results
     */
    private fun loadDataAndDisplay() {
        Log.d(TAG, "📥 Loading data from intent...")

        getIntentData()
        displayResults()
        displayStoragePredictions()
        setupClickListeners()

        // Only save if NOT from history (prevents duplicates)
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
                imageBitmap = BitmapFactory.decodeFile(path)
                binding.ivMushroom.setImageBitmap(imageBitmap)
                Log.d(TAG, "✅ Image loaded from: $path")
            } catch (ex: Exception) {
                Log.e(TAG, "❌ Failed to load image", ex)
                binding.ivMushroom.setImageResource(android.R.mipmap.sym_def_app_icon)
            }
        } ?: run {
            binding.ivMushroom.setImageResource(android.R.mipmap.sym_def_app_icon)
        }
    }

    /**
     * Display basic classification information
     */
    private fun displayBasicInfo() {
        binding.tvMushroomName.text = className

        val confidencePercent = String.format(Locale.getDefault(), "%.2f%%", confidence * 100f)
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
        binding.cardSafety.setCardBackgroundColor(ContextCompat.getColor(this, colorRes))
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
     * ✅ UPDATED: Display storage predictions with FRESHNESS & CHARACTERISTICS ANALYSIS
     */
    private fun displayStoragePredictions() {
        // Only show for edible mushrooms
        if (category != MushroomCategory.EDIBLE.name) {
            binding.cardStoragePredictions.visibility = View.GONE
            binding.cardFreshness.visibility = View.GONE
            return
        }

        try {
            binding.cardStoragePredictions.visibility = View.VISIBLE

            // ✅ Analyze actual mushroom freshness and characteristics from image
            lifecycleScope.launch {
                val analysisResults = withContext(Dispatchers.Default) {
                    imageBitmap?.let { bitmap ->
                        Log.d(TAG, "🔍 Starting freshness and characteristics analysis...")

                        val freshnessResult = freshnessAnalyzer.analyzeFreshness(bitmap, className)
                        val characteristics =
                            characteristicsAnalyzer.analyzeCharacteristics(bitmap, className, grade)

                        Pair(freshnessResult, characteristics)
                    }
                }

                if (analysisResults != null) {
                    val (freshnessResult, characteristics) = analysisResults

                    Log.d(TAG, "✅ Analysis complete:")
                    Log.d(TAG, " Freshness Score: ${freshnessResult.freshnessScore}%")
                    Log.d(TAG, " Size: ${characteristics.estimatedSize}")
                    Log.d(TAG, " Colors: ${characteristics.dominantColors}")

                    displayFreshnessCard(freshnessResult)
                    displayAccurateDescription(characteristics)

                    val predictions = ShelfLifePredictor.getAllStoragePredictions(
                        className,
                        freshnessResult.shelfLifeMultiplier
                    )

                    binding.layoutStorageRows.removeAllViews()
                    predictions.forEach { prediction ->
                        addStorageRow(prediction)
                    }
                } else {
                    Log.w(TAG, "⚠️ Could not analyze, using default")

                    binding.cardFreshness.visibility = View.GONE
                    val predictions = ShelfLifePredictor.getAllStoragePredictions(className)

                    binding.layoutStorageRows.removeAllViews()
                    predictions.forEach { prediction ->
                        addStorageRow(prediction)
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "❌ Failed to display storage predictions", ex)
            binding.cardStoragePredictions.visibility = View.GONE
            binding.cardFreshness.visibility = View.GONE
        }
    }

    /**
     * ✅ Display freshness analysis in dedicated card (Dynamic with proper formatting)
     *
     * IMPORTANT FIX:
     * Convert scores to Int before getString(...) formatting to avoid runtime format crashes.
     */
    private fun displayFreshnessCard(
        freshnessResult: com.example.mushroom_grader.ml.FreshnessAnalysisResult
    ) {
        binding.cardFreshness.visibility = View.VISIBLE

        val freshnessScoreInt = freshnessResult.freshnessScore.coerceIn(0, 100)

        binding.tvFreshnessScore.text =
            getString(R.string.percentage_format, freshnessScoreInt)

        binding.tvFreshnessStatus.text = freshnessResult.status

        binding.progressFreshness.progress = freshnessScoreInt

        val (progressColor, textColor) = when {
            freshnessScoreInt >= 85 -> Pair(
                android.R.color.holo_green_dark,
                android.R.color.holo_green_dark
            )

            freshnessScoreInt >= 70 -> Pair(
                android.R.color.holo_green_light,
                android.R.color.holo_green_light
            )

            freshnessScoreInt >= 50 -> Pair(
                android.R.color.holo_orange_light,
                android.R.color.holo_orange_dark
            )

            freshnessScoreInt >= 30 -> Pair(
                android.R.color.holo_orange_dark,
                android.R.color.holo_red_dark
            )

            else -> Pair(
                android.R.color.holo_red_dark,
                android.R.color.holo_red_dark
            )
        }

        binding.progressFreshness.progressTintList =
            ContextCompat.getColorStateList(this, progressColor)

        binding.tvFreshnessScore.setTextColor(ContextCompat.getColor(this, textColor))
        binding.tvFreshnessStatus.setTextColor(ContextCompat.getColor(this, textColor))

        binding.btnFreshnessDetails.setOnClickListener {
            showFreshnessDetailsDialog(freshnessResult)
        }

        val dynamicTitle = buildString {
            append(getString(R.string.shelf_life_by_storage))
            append("\n")
            // FIX: ensure score is Int for %d placeholder (if your string uses %d).
            append(getString(R.string.current_freshness_format, freshnessScoreInt, freshnessResult.status))
        }

        binding.tvStoragePredictionsTitle.text = dynamicTitle
        Log.d(TAG, "✅ Freshness card displayed: ${freshnessScoreInt}%")
    }

    /**
     * ✅ Show detailed freshness analysis dialog
     */
    private fun showFreshnessDetailsDialog(
        freshnessResult: com.example.mushroom_grader.ml.FreshnessAnalysisResult
    ) {
        // FIX: convert to Int in case your string resources use %d
        val colorScoreInt = freshnessResult.colorScore.coerceIn(0, 100)
        val browningScoreInt = freshnessResult.browningScore.coerceIn(0, 100)
        val spotScoreInt = freshnessResult.spotScore.coerceIn(0, 100)
        val textureScoreInt = freshnessResult.textureScore.coerceIn(0, 100)

        val message = buildString {
            append(freshnessResult.details)
            append("\n\n")
            append(getString(R.string.component_scores))
            append("\n")
            append(getString(R.string.color_vibrancy_format, colorScoreInt))
            append("\n")
            append(getString(R.string.browning_level_format, browningScoreInt))
            append("\n")
            append(getString(R.string.spotting_format, spotScoreInt))
            append("\n")
            append(getString(R.string.texture_quality_format, textureScoreInt))
            append("\n\n")
            append(getString(R.string.freshness_affects_shelf_life))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.freshness_analysis_details)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    /**
     * ✅ NEW: Display accurate description based on actual image characteristics
     */
    private fun displayAccurateDescription(
        characteristics: com.example.mushroom_grader.ml.MushroomCharacteristics
    ) {
        currentCharacteristics = characteristics

        binding.tvDetailedInfo.text = buildString {
            append(characteristics.description)
            append("\n\n")
            append("📏 Size: ${characteristics.estimatedSize}\n")
            append("🎨 Colors: ${characteristics.dominantColors.joinToString(", ")}\n")
            append("✨ Surface: ${characteristics.surfaceCondition}")
            if (characteristics.visualDefects.isNotEmpty()) {
                append("\n⚠️ Notes: ${characteristics.visualDefects.joinToString(", ")}")
            }
        }

        Log.d(TAG, "✅ Accurate description displayed")
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

        val icon = getStorageMethodIcon(prediction.storageMethod.name)

        val methodNameView = rowView.findViewById<TextView>(R.id.textStorageMethodName)
        methodNameView.text = getString(
            R.string.storage_method_format,
            icon,
            prediction.storageMethod.displayName
        )

        val daysView = rowView.findViewById<TextView>(R.id.textStorageDays)
        daysView.text = getString(R.string.days_format, prediction.calculatedDays)

        val color = getShelfLifeColor(prediction.calculatedDays)
        daysView.setTextColor(ContextCompat.getColor(this, color))

        val freshnessIndicator = rowView.findViewById<View>(R.id.viewFreshnessIndicator)
        val freshnessStatus = prediction.getFreshnessStatus()
        freshnessIndicator.setBackgroundColor(
            ContextCompat.getColor(this, freshnessStatus.color)
        )

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
            append(getString(R.string.storage_method_header, prediction.storageMethod.displayName))
            append("\n\n")

            append(getString(R.string.freshness_label, freshnessStatus.message))
            append("\n")
            append(getString(R.string.days_remaining_label, daysRemaining))
            append("\n\n")

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
                append("• $warning\n")
            }
        }
    }

    /**
     * Setup all button click listeners
     */
    private fun setupClickListeners() {
        binding.btnShare.setOnClickListener { shareResult() }
        binding.btnTakeAnother.setOnClickListener { navigateToCameraActivity() }
        binding.btnViewHistory.setOnClickListener { navigateToHistoryActivity() }
        binding.btnBackHome.setOnClickListener { navigateToMainActivity() }
        binding.btnMoreInfo.setOnClickListener { showMoreInfoDialog() }
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

            val confidencePercent = String.format(Locale.getDefault(), "%.2f%%", confidence * 100f)
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
    @OptIn(ExperimentalCamera2Interop::class)
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
     * ✅ UPDATED: Show detailed mushroom information dialog with actual photo characteristics
     */
    private fun showMoreInfoDialog() {
        val message = mlModelHelper.getMushroomInfo(classId, currentCharacteristics)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.detailed_information)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    /**
     * Save classification result to database
     */
    private fun saveToDatabase() {
        Log.d(TAG, "saveToDatabase called")

        lifecycleScope.launch {
            try {
                Log.d(TAG, "Creating ClassificationResult object...")

                withContext(Dispatchers.IO) {
                    val result = ClassificationResult(
                        className = className,
                        classId = classId,
                        confidence = confidence,
                        isPoisonous = isPoisonous,
                        category = try {
                            MushroomCategory.valueOf(category)
                        } catch (_: Exception) {
                            MushroomCategory.UNKNOWN
                        },
                        grade = grade,
                        imagePath = imagePath
                    )

                    Log.d(TAG, "Result object created: $className")

                    val database = AppDatabase.getDatabase(applicationContext)
                    Log.d(TAG, "Database instance obtained")

                    val rowId = database.resultDao().insertResult(result)
                    Log.d(TAG, "✅ SAVED to database! Row ID: $rowId")

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@ResultActivity,
                            R.string.saved_to_history,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "❌ FAILED to save to database!", ex)
                Log.e(TAG, "Error: ${ex.message}")

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ResultActivity,
                        getString(R.string.error_database),
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
     * Clean up ML model resources and bitmap
     */
    override fun onDestroy() {
        super.onDestroy()
        mlModelHelper.close()
        imageBitmap?.recycle()
        Log.d(TAG, "🔚 Activity destroyed, resources cleaned up")
    }
}
