package com.example.mushroom_grader

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
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
import com.example.mushroom_grader.ml.FreshnessAnalysisResult
import com.example.mushroom_grader.ml.FreshnessAnalyzer
import com.example.mushroom_grader.ml.MLModelHelper
import com.example.mushroom_grader.ml.MushroomCategory
import com.example.mushroom_grader.ml.MushroomCharacteristics
import com.example.mushroom_grader.ml.MushroomCharacteristicsAnalyzer
import com.example.mushroom_grader.ml.ShelfLifeData
import com.example.mushroom_grader.ml.ShelfLifePredictor
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * ResultActivity - Displays mushroom classification results
 *
 * Features:
 * - Shows mushroom image and classification details
 * - Displays safety status (Edible/Poisonous/Inedible)
 * - Shows grading information
 * - Analyzes actual mushroom freshness from image
 * - Analyzes visual characteristics (size, color, surface)
 * - Storage & shelf life predictions based on visual freshness
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

    // Store characteristics for "More Info" dialog
    private var currentCharacteristics: MushroomCharacteristics? = null

    companion object {
        private const val TAG = "ResultActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
     * Initialize ML Model Helper and analyzers
     */
    private fun initializeAnalyzers() {
        mlModelHelper = MLModelHelper(this)
        freshnessAnalyzer = FreshnessAnalyzer()
        characteristicsAnalyzer = MushroomCharacteristicsAnalyzer()
        Log.d(TAG, "ML model and analyzers initialized")
    }

    /**
     * Load intent data and display all results
     */
    private fun loadDataAndDisplay() {
        Log.d(TAG, "Loading data from intent")
        getIntentData()

        displayResults()
        displayStoragePredictions()
        setupClickListeners()

        val isFromHistory = intent.getBooleanExtra("fromHistory", false)
        if (!isFromHistory) {
            saveToDatabase()
        } else {
            Log.d(TAG, "Viewing from history - skipping database save")
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
        category = intent.getStringExtra("category") ?: MushroomCategory.INEDIBLE.name
        grade = intent.getStringExtra("grade")
        imagePath = intent.getStringExtra("imagePath")

        Log.d(TAG, "Data loaded: $className (id=$classId, conf=$confidence, category=$category)")
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
                Log.d(TAG, "Image loaded from: $path")
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to load image", ex)
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
     * Display storage predictions with freshness & characteristics analysis
     */
    private fun displayStoragePredictions() {
        if (category != MushroomCategory.EDIBLE.name) {
            binding.cardStoragePredictions.visibility = View.GONE
            binding.cardFreshness.visibility = View.GONE
            return
        }

        try {
            binding.cardStoragePredictions.visibility = View.VISIBLE

            lifecycleScope.launch {
                val analysisResults = withContext(Dispatchers.Default) {
                    imageBitmap?.let { bitmap ->
                        val freshnessResult = freshnessAnalyzer.analyzeFreshness(bitmap, className)
                        val characteristics = characteristicsAnalyzer.analyzeCharacteristics(bitmap, className, grade)
                        Pair(freshnessResult, characteristics)
                    }
                }

                if (analysisResults != null) {
                    val (freshnessResult, characteristics) = analysisResults

                    displayFreshnessCard(freshnessResult)
                    displayAccurateDescription(characteristics)

                    val predictions = ShelfLifePredictor.getAllStoragePredictions(
                        mushroomName = className,
                        freshnessMultiplier = freshnessResult.shelfLifeMultiplier
                    )

                    binding.layoutStorageRows.removeAllViews()
                    predictions.forEach { addStorageRow(it) }
                } else {
                    binding.cardFreshness.visibility = View.GONE

                    val predictions = ShelfLifePredictor.getAllStoragePredictions(className)
                    binding.layoutStorageRows.removeAllViews()
                    predictions.forEach { addStorageRow(it) }
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to display storage predictions", ex)
            binding.cardStoragePredictions.visibility = View.GONE
            binding.cardFreshness.visibility = View.GONE
        }
    }

    /**
     * Display freshness analysis in dedicated card
     */
    private fun displayFreshnessCard(freshnessResult: FreshnessAnalysisResult) {
        binding.cardFreshness.visibility = View.VISIBLE

        val freshnessScoreInt = freshnessResult.freshnessScore.coerceIn(0, 100)
        binding.tvFreshnessScore.text = getString(R.string.percentage_format, freshnessScoreInt)
        binding.tvFreshnessStatus.text = freshnessResult.status
        binding.progressFreshness.progress = freshnessScoreInt

        val (progressColor, textColor) = when {
            freshnessScoreInt >= 85 -> Pair(android.R.color.holo_green_dark, android.R.color.holo_green_dark)
            freshnessScoreInt >= 70 -> Pair(android.R.color.holo_green_light, android.R.color.holo_green_light)
            freshnessScoreInt >= 50 -> Pair(android.R.color.holo_orange_light, android.R.color.holo_orange_dark)
            freshnessScoreInt >= 30 -> Pair(android.R.color.holo_orange_dark, android.R.color.holo_red_dark)
            else -> Pair(android.R.color.holo_red_dark, android.R.color.holo_red_dark)
        }

        binding.progressFreshness.progressTintList = ContextCompat.getColorStateList(this, progressColor)
        binding.tvFreshnessScore.setTextColor(ContextCompat.getColor(this, textColor))
        binding.tvFreshnessStatus.setTextColor(ContextCompat.getColor(this, textColor))

        binding.btnFreshnessDetails.setOnClickListener {
            showFreshnessDetailsDialog(freshnessResult)
        }

        val dynamicTitle = buildString {
            append(getString(R.string.shelf_life_by_storage))
            append("\n")
            append(getString(R.string.current_freshness_format, freshnessScoreInt, freshnessResult.status))
        }
        binding.tvStoragePredictionsTitle.text = dynamicTitle
    }

    /**
     * Professional BottomSheet: freshness details
     */
    private fun showFreshnessDetailsDialog(freshnessResult: FreshnessAnalysisResult) {
        val overall = freshnessResult.freshnessScore.coerceIn(0, 100)
        val color = freshnessResult.colorScore.coerceIn(0, 100)
        val browning = freshnessResult.browningScore.coerceIn(0, 100)
        val spots = freshnessResult.spotScore.coerceIn(0, 100)
        val texture = freshnessResult.textureScore.coerceIn(0, 100)

        val sheet = buildBottomSheetView(
            title = getString(R.string.freshness_analysis_details),
            subtitle = freshnessResult.status,
            sections = listOf(
                SheetSection(
                    "Overview",
                    listOf(
                        SheetRow.Pill("Overall freshness", "$overall%", scoreColorRes(overall)),
                        SheetRow.KeyValue("Shelf-life multiplier", String.format(Locale.getDefault(), "%.2fx", freshnessResult.shelfLifeMultiplier)),
                        SheetRow.Body(getString(R.string.freshness_affects_shelf_life))
                    )
                ),
                SheetSection(
                    "Component scores",
                    listOf(
                        SheetRow.Progress("Color vibrancy", color),
                        SheetRow.Progress("Browning level", browning),
                        SheetRow.Progress("Spotting", spots),
                        SheetRow.Progress("Texture quality", texture)
                    )
                )
            )
        )

        showBottomSheet(sheet)
    }

    /**
     * Display accurate description based on actual image characteristics (no emojis)
     */
    private fun displayAccurateDescription(characteristics: MushroomCharacteristics) {
        currentCharacteristics = characteristics

        binding.tvDetailedInfo.text = buildString {
            append(characteristics.description)
            append("\n\n")
            append("Size: ${characteristics.estimatedSize}\n")
            append("Colors: ${characteristics.dominantColors.joinToString(", ")}\n")
            append("Surface: ${characteristics.surfaceCondition}")
            if (characteristics.visualDefects.isNotEmpty()) {
                append("\nNotes: ${characteristics.visualDefects.joinToString(", ")}")
            }
        }
    }

    /**
     * Add a single storage method row to the table with freshness indicator
     */
    private fun addStorageRow(prediction: ShelfLifeData) {
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
        freshnessIndicator.setBackgroundColor(ContextCompat.getColor(this, freshnessStatus.color))

        rowView.setOnClickListener {
            showDetailedStorageInfo(prediction)
        }

        binding.layoutStorageRows.addView(rowView)
    }

    /**
     * Get professional (non-emoji) icon/label for storage method
     */
    private fun getStorageMethodIcon(methodName: String): String {
        return when (methodName) {
            "VACUUM_SEALED" -> "VAC"
            "REFRIGERATED_SEALED" -> "REF"
            "REFRIGERATED_OPEN" -> "REF"
            "ROOM_TEMPERATURE" -> "AMB"
            "FROZEN" -> "FRZ"
            else -> "STO"
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
     * Professional BottomSheet: storage details
     */
    private fun showDetailedStorageInfo(prediction: ShelfLifeData) {
        val freshnessStatus = prediction.getFreshnessStatus()
        val daysRemaining = prediction.getDaysRemaining()

        val sheet = buildBottomSheetView(
            title = getString(R.string.storage_details),
            subtitle = prediction.storageMethod.displayName,
            sections = listOf(
                SheetSection(
                    "Status",
                    listOf(
                        SheetRow.Pill("Freshness", freshnessStatus.message, freshnessStatus.color),
                        SheetRow.KeyValue("Days remaining", daysRemaining.toString())
                    )
                ),
                SheetSection(
                    "Storage method",
                    listOf(
                        SheetRow.KeyValue("Temperature", prediction.storageTemperature),
                        SheetRow.KeyValue("Predicted shelf life", "${prediction.calculatedDays} days"),
                        SheetRow.KeyValue("Expiration date", prediction.getFormattedExpirationDate())
                    )
                ),
                SheetSection(
                    "Tips",
                    prediction.tips.map { SheetRow.Bullet(it) }.ifEmpty { listOf(SheetRow.Body("No tips available.")) }
                ),
                SheetSection(
                    "Warnings",
                    prediction.warnings.map { SheetRow.Bullet(it) }.ifEmpty { listOf(SheetRow.Body("No warnings available.")) }
                )
            )
        )

        showBottomSheet(sheet)
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

            val safetyText = if (isPoisonous) getString(R.string.poisonous) else getString(R.string.safe_to_eat)
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
     * Professional BottomSheet: detailed mushroom information (photo + reference)
     */
    private fun showMoreInfoDialog() {
        val confidencePercent = String.format(Locale.getDefault(), "%.2f%%", confidence * 100f)
        val safetyText = if (isPoisonous) getString(R.string.poisonous) else getString(R.string.safe_to_eat)

        val characteristics = currentCharacteristics
        val photoDetails = buildString {
            append("Size: ${characteristics?.estimatedSize ?: "Unknown"}\n")
            append("Colors: ${characteristics?.dominantColors?.joinToString(", ") ?: "Unknown"}\n")
            append("Surface: ${characteristics?.surfaceCondition ?: "Unknown"}")
            val defects = characteristics?.visualDefects.orEmpty()
            if (defects.isNotEmpty()) append("\nNotes: ${defects.joinToString(", ")}")
        }

        val referenceInfo = mlModelHelper.getMushroomInfo(classId, currentCharacteristics)

        val sheet = buildBottomSheetView(
            title = getString(R.string.detailed_information),
            subtitle = className,
            sections = listOf(
                SheetSection(
                    "Classification",
                    listOf(
                        SheetRow.KeyValue("Category", category),
                        SheetRow.KeyValue("Safety", safetyText),
                        SheetRow.KeyValue("Confidence", confidencePercent),
                        SheetRow.KeyValue("Grade", grade ?: "N/A")
                    )
                ),
                SheetSection(
                    "Photo analysis",
                    listOf(SheetRow.Body(photoDetails))
                ),
                SheetSection(
                    "Mushroom information",
                    listOf(SheetRow.Body(referenceInfo))
                )
            )
        )

        showBottomSheet(sheet)
    }

    /**
     * Save classification result to database
     */
    private fun saveToDatabase() {
        Log.d(TAG, "saveToDatabase called")

        lifecycleScope.launch {
            try {
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

                    val database = AppDatabase.getDatabase(applicationContext)
                    database.resultDao().insertResult(result)
                }

                Toast.makeText(
                    this@ResultActivity,
                    R.string.saved_to_history,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to save to database", ex)
                Toast.makeText(
                    this@ResultActivity,
                    getString(R.string.error_database),
                    Toast.LENGTH_LONG
                ).show()
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
        Log.d(TAG, "Activity destroyed - resources cleaned up")
    }

    // ------------------------------------------------------------
    // Bottom Sheet UI (professional, no emojis, padding-safe)
    // ------------------------------------------------------------

    private data class SheetSection(
        val title: String,
        val rows: List<SheetRow>
    )

    private sealed class SheetRow {
        data class KeyValue(val key: String, val value: String) : SheetRow()
        data class Progress(val label: String, val value: Int) : SheetRow()
        data class Body(val text: String) : SheetRow()
        data class Bullet(val text: String) : SheetRow()
        data class Pill(val key: String, val value: String, val colorRes: Int) : SheetRow()
    }

    private fun showBottomSheet(contentView: View) {
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(contentView)
        dialog.show()
    }

    private fun buildBottomSheetView(
        title: String,
        subtitle: String?,
        sections: List<SheetSection>
    ): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(makeSheetHandle())
        root.addView(makeHeader(title, subtitle))
        root.addView(makeDivider())

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingAll(16.dp)
        }

        sections.forEachIndexed { index, section ->
            if (index > 0) content.addView(spacer(12.dp))
            content.addView(makeSectionCard(section))
        }

        root.addView(content)
        scroll.addView(root)
        return scroll
    }

    private fun makeSheetHandle(): View {
        val handle = View(this)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f
            setColor(ContextCompat.getColor(this@ResultActivity, android.R.color.darker_gray))
            alpha = 80
        }
        handle.background = bg
        handle.layoutParams = LinearLayout.LayoutParams(44.dp, 5.dp).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = 10.dp
            bottomMargin = 8.dp
        }
        return handle
    }

    private fun makeHeader(title: String, subtitle: String?): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingAll(16.dp)
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(this@ResultActivity, android.R.color.black))
        }
        wrapper.addView(titleView)

        if (!subtitle.isNullOrBlank()) {
            val subtitleView = TextView(this).apply {
                text = subtitle
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@ResultActivity, android.R.color.darker_gray))
                setPadding(0, 6.dp, 0, 0)
            }
            wrapper.addView(subtitleView)
        }

        return wrapper
    }

    private fun makeDivider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.dp
            )
            setBackgroundColor(ContextCompat.getColor(this@ResultActivity, android.R.color.darker_gray))
            alpha = 0.25f
        }
    }

    private fun makeSectionCard(section: SheetSection): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground()
            setPaddingAll(14.dp)
        }

        val titleView = TextView(this).apply {
            text = section.title
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(this@ResultActivity, android.R.color.black))
        }
        card.addView(titleView)
        card.addView(spacer(10.dp))

        section.rows.forEachIndexed { idx, row ->
            if (idx > 0) card.addView(spacer(10.dp))
            card.addView(makeRow(row))
        }

        return card
    }

    private fun makeRow(row: SheetRow): View {
        return when (row) {
            is SheetRow.KeyValue -> makeKeyValueRow(row.key, row.value)
            is SheetRow.Progress -> makeProgressRow(row.label, row.value)
            is SheetRow.Body -> makeBodyText(row.text)
            is SheetRow.Bullet -> makeBullet(row.text)
            is SheetRow.Pill -> makePillRow(row.key, row.value, row.colorRes)
        }
    }

    private fun makeKeyValueRow(key: String, value: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val keyView = TextView(this).apply {
            text = key
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@ResultActivity, android.R.color.darker_gray))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val valueView = TextView(this).apply {
            text = value
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(this@ResultActivity, android.R.color.black))
        }

        row.addView(keyView)
        row.addView(valueView)
        return row
    }

    private fun makeProgressRow(label: String, value: Int): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val v = value.coerceIn(0, 100)
        wrapper.addView(makeKeyValueRow(label, "$v%"))

        val bar = LinearProgressIndicator(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dp
            }
            trackCornerRadius = 8.dp
            setProgressCompat(v, true)
        }

        wrapper.addView(bar)
        return wrapper
    }

    private fun makePillRow(key: String, value: String, colorRes: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val keyView = TextView(this).apply {
            text = key
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@ResultActivity, android.R.color.darker_gray))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val pill = TextView(this).apply {
            text = value
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(this@ResultActivity, android.R.color.white))
            setPaddingVH(10.dp, 6.dp)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 999f
                setColor(ContextCompat.getColor(this@ResultActivity, colorRes))
            }
        }

        row.addView(keyView)
        row.addView(pill)
        return row
    }

    private fun makeBodyText(text: String): View {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@ResultActivity, android.R.color.black))
            setLineSpacing(0f, 1.15f)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun makeBullet(text: String): View {
        return TextView(this).apply {
            this.text = "• $text"
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@ResultActivity, android.R.color.black))
            setLineSpacing(0f, 1.15f)
        }
    }

    private fun spacer(heightPx: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                heightPx
            )
        }
    }

    private fun cardBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16.dp.toFloat()
            setColor(ContextCompat.getColor(this@ResultActivity, android.R.color.white))
            setStroke(1.dp, ContextCompat.getColor(this@ResultActivity, android.R.color.darker_gray))
            alpha = 255
        }
    }

    private fun scoreColorRes(score: Int): Int {
        return when {
            score >= 85 -> android.R.color.holo_green_dark
            score >= 70 -> android.R.color.holo_green_light
            score >= 50 -> android.R.color.holo_orange_dark
            score >= 30 -> android.R.color.holo_orange_dark
            else -> android.R.color.holo_red_dark
        }
    }

    // Padding helpers to avoid setPadding(...) param errors
    private fun View.setPaddingAll(p: Int) {
        setPadding(p, p, p, p)
    }

    private fun View.setPaddingVH(horizontal: Int, vertical: Int) {
        setPadding(horizontal, vertical, horizontal, vertical)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
