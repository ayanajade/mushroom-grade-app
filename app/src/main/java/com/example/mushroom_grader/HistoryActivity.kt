package com.example.mushroom_grader

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mushroom_grader.databinding.ActivityHistoryBinding
import com.example.mushroom_grader.database.AppDatabase
import com.example.mushroom_grader.ml.ClassificationResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter
    private val historyList = mutableListOf<ClassificationResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "History"

        setupRecyclerView()
        loadHistory()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        // ✅ ENHANCED: Navigate to ResultActivity instead of showing dialog
        adapter = HistoryAdapter { result ->
            navigateToResultPage(result)
        }

        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(applicationContext)
                    database.resultDao().getAllResults()
                }

                if (results.isNotEmpty()) {
                    binding.emptyStateLayout.visibility = View.GONE
                    binding.rvHistory.visibility = View.VISIBLE
                    historyList.clear()
                    historyList.addAll(results)
                    adapter.submitList(results)
                } else {
                    binding.rvHistory.visibility = View.GONE
                    binding.emptyStateLayout.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                binding.emptyStateLayout.visibility = View.VISIBLE
                binding.rvHistory.visibility = View.GONE
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnClear.setOnClickListener {
            showClearConfirmation()
        }
    }

    // ✅ ENHANCED: Navigate to full ResultActivity page with all details
    private fun navigateToResultPage(result: ClassificationResult) {
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra("className", result.className)
            putExtra("classId", result.classId)
            putExtra("confidence", result.confidence)
            putExtra("isPoisonous", result.isPoisonous)
            putExtra("category", result.category.name)
            putExtra("grade", result.grade)
            putExtra("imagePath", result.imagePath)
            putExtra("fromHistory", true) // Optional: to track it came from history
        }
        startActivity(intent)
    }

    private fun showClearConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear History")
            .setMessage("Are you sure you want to delete all history records? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                clearHistory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearHistory() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(applicationContext)
                    database.resultDao().deleteAllResults()
                }
                historyList.clear()
                adapter.clear()
                binding.rvHistory.visibility = View.GONE
                binding.emptyStateLayout.visibility = View.VISIBLE
            } catch (e: Exception) {
                MaterialAlertDialogBuilder(this@HistoryActivity)
                    .setTitle("Error")
                    .setMessage("Failed to clear history")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // ✅ ENHANCED: Reload history when returning from ResultActivity
        loadHistory()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
