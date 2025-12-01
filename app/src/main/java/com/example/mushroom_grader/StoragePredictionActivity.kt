package com.example.mushroom_grader

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mushroom_grader.databinding.ActivityStoragePredictionBinding
import com.example.mushroom_grader.ml.ShelfLifeData
import com.example.mushroom_grader.ml.ShelfLifePredictor

/**
 * StoragePredictionActivity - Displays detailed storage predictions
 * Shows all storage methods and their shelf life predictions for a classified mushroom
 * Features:
 * - List of all storage methods
 * - Detailed view of selected storage method
 * - Freshness status indicators
 * - Storage tips and warnings
 */
class StoragePredictionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStoragePredictionBinding
    private lateinit var adapter: StorageMethodAdapter
    private lateinit var allPredictions: List<ShelfLifeData>
    private var currentPrediction: ShelfLifeData? = null
    private var mushroomName: String = ""
    private var classificationDate: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStoragePredictionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        getIntentData()
        loadPredictions()
        setupRecyclerView()
        displaySelectedPrediction(allPredictions.first())
    }

    /**
     * Setup toolbar with back navigation
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.storage_shelf_life)
        }
    }

    /**
     * Get intent data (mushroom name and classification date)
     */
    private fun getIntentData() {
        mushroomName = intent.getStringExtra("mushroomName") ?: getString(R.string.unknown_mushroom)
        classificationDate = intent.getLongExtra("classificationDate", System.currentTimeMillis())
    }

    /**
     * ✅ FIXED: Load all storage predictions for the mushroom
     */
    private fun loadPredictions() {
        allPredictions = ShelfLifePredictor.getAllStoragePredictions(
            mushroomName = mushroomName,
            freshnessMultiplier = 1.0f,  // ✅ Default to fresh (no freshness data available here)
            classificationDate = classificationDate
        )
    }

    /**
     * Setup RecyclerView with storage method adapter
     */
    private fun setupRecyclerView() {
        adapter = StorageMethodAdapter(allPredictions) { selectedData ->
            val position = allPredictions.indexOf(selectedData)
            adapter.updateSelection(position)
            displaySelectedPrediction(selectedData)
        }

        binding.rvStorageMethods.apply {
            layoutManager = LinearLayoutManager(this@StoragePredictionActivity)
            adapter = this@StoragePredictionActivity.adapter
        }
    }

    /**
     * Display detailed information for selected storage method
     */
    private fun displaySelectedPrediction(data: ShelfLifeData) {
        currentPrediction = data

        // Mushroom info
        binding.textMushroomName.text = data.mushroomName

        // Storage method details
        binding.textStorageMethod.text = data.storageMethod.displayName
        binding.textStorageDescription.text = data.storageMethod.description

        // Shelf life
        binding.textShelfLifeDays.text = data.calculatedDays.toString()
        binding.textExpirationDate.text = getString(
            R.string.expires_on_format,
            data.getFormattedExpirationDate()
        )

        binding.textTemperature.text = getString(
            R.string.store_at_format,
            data.storageTemperature
        )

        // Color code based on freshness status
        val freshnessStatus = data.getFreshnessStatus()
        binding.textShelfLifeDays.setTextColor(
            ContextCompat.getColor(this, freshnessStatus.color)
        )

        // Display freshness status message
        binding.textFreshnessStatus.text = freshnessStatus.message

        // Storage tips
        val tipsText = data.tips.joinToString("\n") { "• $it" }
        binding.textStorageTips.text = tipsText

        // Warnings
        val warningsText = data.warnings.joinToString("\n") { "⚠️ $it" }
        binding.textWarnings.text = warningsText
    }

    /**
     * Handle toolbar back button
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Save current state
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("mushroomName", mushroomName)
        outState.putLong("classificationDate", classificationDate)
    }

    /**
     * Restore state if needed
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        mushroomName = savedInstanceState.getString("mushroomName", "")
        classificationDate = savedInstanceState.getLong("classificationDate", System.currentTimeMillis())
    }
}
