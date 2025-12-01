package com.example.mushroom_grader

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mushroom_grader.databinding.ActivityHistoryBinding
import com.example.mushroom_grader.ui.fragments.HistoryAdapter
import com.example.mushroom_grader.database.AppDatabase
import com.example.mushroom_grader.ml.ClassificationResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter
    private var allResults = mutableListOf<ClassificationResult>()
    private var currentSortOrder = SortOrder.NEWEST_FIRST

    companion object {
        private const val TAG = "HistoryActivity"
    }

    enum class SortOrder {
        NEWEST_FIRST,
        OLDEST_FIRST,
        HIGHEST_CONFIDENCE,
        LOWEST_CONFIDENCE,
        NAME_AZ,
        NAME_ZA
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "🚀 onCreate started")

        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "✅ View binding successful")

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        setupSwipeToDelete()

        Log.d(TAG, "📊 Loading history...")
        loadHistory()
    }

    private fun setupToolbar() {
        Log.d(TAG, "⚙️ Setting up toolbar")
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.history)
        }
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "⚙️ Setting up RecyclerView")

        adapter = HistoryAdapter { result ->
            Log.d(TAG, "👆 Item clicked: ${result.className}")
            navigateToResultPage(result)
        }

        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = this@HistoryActivity.adapter
            setHasFixedSize(true)
        }

        Log.d(TAG, "✅ RecyclerView setup complete")
    }

    private fun setupSwipeToDelete() {
        Log.d(TAG, "⚙️ Setting up swipe to delete")

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && position < allResults.size) {
                    val result = allResults[position]
                    Log.d(TAG, "👈 Swiped to delete: ${result.className} at position $position")
                    deleteResult(result, position)
                }
            }
        }

        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvHistory)
    }

    private fun loadHistory() {
        Log.d(TAG, "🔄 loadHistory() called")

        lifecycleScope.launch {
            try {
                Log.d(TAG, "📥 Fetching from database...")

                val results = withContext(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(applicationContext)
                    Log.d(TAG, "✅ Database instance obtained")

                    val fetchedResults = database.resultDao().getAllResults()
                    Log.d(TAG, "📦 Database returned ${fetchedResults.size} results")

                    fetchedResults
                }

                // Log each result
                results.forEachIndexed { index, result ->
                    Log.d(TAG, "📋 Result $index: ${result.className} - Confidence: ${result.confidence} - Time: ${result.timestamp}")
                }

                Log.d(TAG, "🔄 Updating allResults list...")
                allResults.clear()
                allResults.addAll(results)
                Log.d(TAG, "✅ allResults now has ${allResults.size} items")

                Log.d(TAG, "🎨 Calling displayResults()...")
                displayResults()

            } catch (ex: Exception) {
                Log.e(TAG, "❌ ERROR loading history", ex)
                Log.e(TAG, "❌ Error message: ${ex.message}")
                Log.e(TAG, "❌ Error stack trace: ${ex.stackTraceToString()}")

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@HistoryActivity,
                        "Error loading history: ${ex.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }

                showEmptyState()
            }
        }
    }

    private fun displayResults() {
        Log.d(TAG, "🎨 displayResults() called")
        Log.d(TAG, "📊 allResults.size = ${allResults.size}")
        Log.d(TAG, "📊 currentSortOrder = $currentSortOrder")

        val sortedResults = sortResults(allResults, currentSortOrder)

        Log.d(TAG, "📊 sortedResults.size = ${sortedResults.size}")

        if (sortedResults.isNotEmpty()) {
            Log.d(TAG, "✅ sortedResults is NOT empty, displaying...")

            // Log each sorted result
            sortedResults.forEachIndexed { index, result ->
                Log.d(TAG, "  📌 Sorted[$index]: ${result.className} (${result.confidence})")
            }

            // Hide empty state
            binding.emptyStateLayout.visibility = View.GONE
            binding.rvHistory.visibility = View.VISIBLE

            Log.d(TAG, "🔄 Calling adapter.submitList() with ${sortedResults.size} items...")
            adapter.submitList(sortedResults)

            Log.d(TAG, "✅ adapter.submitList() completed")
            Log.d(TAG, "📊 adapter.itemCount = ${adapter.itemCount}")

            // Update stats
            updateStats(sortedResults)

        } else {
            Log.d(TAG, "⚠️ sortedResults is EMPTY!")
            showEmptyState()
        }
    }

    private fun sortResults(
        results: List<ClassificationResult>,
        sortOrder: SortOrder
    ): List<ClassificationResult> {
        Log.d(TAG, "🔄 sortResults() - input size: ${results.size}, order: $sortOrder")

        val sorted = when (sortOrder) {
            SortOrder.NEWEST_FIRST -> results.sortedByDescending { it.timestamp }
            SortOrder.OLDEST_FIRST -> results.sortedBy { it.timestamp }
            SortOrder.HIGHEST_CONFIDENCE -> results.sortedByDescending { it.confidence }
            SortOrder.LOWEST_CONFIDENCE -> results.sortedBy { it.confidence }
            SortOrder.NAME_AZ -> results.sortedBy { it.className }
            SortOrder.NAME_ZA -> results.sortedByDescending { it.className }
        }

        Log.d(TAG, "✅ sortResults() - output size: ${sorted.size}")
        return sorted
    }

    private fun updateStats(results: List<ClassificationResult>) {
        Log.d(TAG, "📊 updateStats() with ${results.size} results")

        val totalCount = results.size
        val poisonousCount = results.count { it.isPoisonous }
        val edibleCount = results.count { !it.isPoisonous && it.category.name != "INEDIBLE" }

        Log.d(TAG, "📊 Stats - Total: $totalCount, Poisonous: $poisonousCount, Edible: $edibleCount")

        // ✅ FIXED: Use correct IDs from your XML
        binding.tvTotal.text = "Total: $totalCount"
        binding.tvPoisonous.text = "Poison: $poisonousCount"
        binding.tvEdible.text = "Edible: $edibleCount"
    }

    private fun showEmptyState() {
        Log.d(TAG, "📭 showEmptyState() called")

        binding.rvHistory.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE

        // Reset stats
        binding.tvTotal.text = "Total: 0"
        binding.tvPoisonous.text = "Poison: 0"
        binding.tvEdible.text = "Edible: 0"
    }

    private fun setupClickListeners() {
        Log.d(TAG, "⚙️ Setting up click listeners")

        binding.btnClear.setOnClickListener {
            Log.d(TAG, "🗑️ Clear button clicked")
            if (allResults.isNotEmpty()) {
                showClearAllConfirmation()
            } else {
                Toast.makeText(this, "No history to clear", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToResultPage(result: ClassificationResult) {
        Log.d(TAG, "🔄 Navigating to result page: ${result.className}")

        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra("className", result.className)
            putExtra("classId", result.classId)
            putExtra("confidence", result.confidence)
            putExtra("isPoisonous", result.isPoisonous)
            putExtra("category", result.category.name)
            putExtra("grade", result.grade)
            putExtra("imagePath", result.imagePath)
            putExtra("fromHistory", true)
        }

        startActivity(intent)
    }

    private fun deleteResult(result: ClassificationResult, position: Int) {
        Log.d(TAG, "🗑️ deleteResult() - ${result.className} at position $position")

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(applicationContext)
                    database.resultDao().deleteResult(result)
                }

                Log.d(TAG, "✅ Deleted from database")

                allResults.removeAt(position)
                adapter.removeItem(position)

                if (allResults.isEmpty()) {
                    showEmptyState()
                } else {
                    updateStats(allResults)
                }

                Snackbar.make(binding.root, "Result deleted", Snackbar.LENGTH_LONG)
                    .setAction("UNDO") {
                        Log.d(TAG, "↩️ Undo delete")
                        undoDelete(result)
                    }
                    .show()

            } catch (ex: Exception) {
                Log.e(TAG, "❌ Failed to delete", ex)
                Toast.makeText(
                    this@HistoryActivity,
                    "Failed to delete",
                    Toast.LENGTH_SHORT
                ).show()
                adapter.notifyItemChanged(position)
            }
        }
    }

    private fun undoDelete(result: ClassificationResult) {
        Log.d(TAG, "↩️ undoDelete() - ${result.className}")

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(applicationContext)
                    database.resultDao().insertResult(result)
                }

                Log.d(TAG, "✅ Restored to database")
                loadHistory()
                Toast.makeText(this@HistoryActivity, "Restored", Toast.LENGTH_SHORT).show()

            } catch (ex: Exception) {
                Log.e(TAG, "❌ Failed to restore", ex)
                Toast.makeText(
                    this@HistoryActivity,
                    "Failed to restore",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showClearAllConfirmation() {
        Log.d(TAG, "⚠️ showClearAllConfirmation()")

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.clear_history))
            .setMessage("Delete all ${allResults.size} records? This cannot be undone.")
            .setPositiveButton(getString(R.string.delete_all)) { _, _ ->
                Log.d(TAG, "✅ User confirmed clear all")
                clearAllHistory()
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                Log.d(TAG, "❌ User cancelled clear all")
            }
            .show()
    }

    private fun clearAllHistory() {
        Log.d(TAG, "🗑️ clearAllHistory()")

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(applicationContext)
                    database.resultDao().deleteAllResults()
                }

                Log.d(TAG, "✅ All history cleared from database")

                allResults.clear()
                adapter.clear()
                showEmptyState()
                Toast.makeText(this@HistoryActivity, "History cleared", Toast.LENGTH_SHORT).show()

            } catch (ex: Exception) {
                Log.e(TAG, "❌ Failed to clear history", ex)
                Toast.makeText(
                    this@HistoryActivity,
                    "Failed to clear history",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_history, menu)

        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                Log.d(TAG, "🔍 Search query: $newText")
                filterBySearch(newText ?: "")
                return true
            }
        })

        return true
    }

    private fun filterBySearch(query: String) {
        Log.d(TAG, "🔍 filterBySearch() - query: '$query'")

        if (query.isEmpty()) {
            Log.d(TAG, "  Empty query, showing all results")
            displayResults()
        } else {
            val searchResults = allResults.filter {
                it.className.contains(query, ignoreCase = true) ||
                        it.grade?.contains(query, ignoreCase = true) == true
            }

            Log.d(TAG, "  Found ${searchResults.size} matching results")

            if (searchResults.isNotEmpty()) {
                binding.emptyStateLayout.visibility = View.GONE
                binding.rvHistory.visibility = View.VISIBLE
                adapter.submitList(sortResults(searchResults, currentSortOrder))
                updateStats(searchResults)
            } else {
                showEmptyState()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                Log.d(TAG, "⬅️ Back button pressed")
                finish()
                true
            }

            R.id.action_sort_newest -> {
                Log.d(TAG, "🔄 Sort: Newest First")
                currentSortOrder = SortOrder.NEWEST_FIRST
                displayResults()
                true
            }

            R.id.action_sort_oldest -> {
                Log.d(TAG, "🔄 Sort: Oldest First")
                currentSortOrder = SortOrder.OLDEST_FIRST
                displayResults()
                true
            }

            R.id.action_sort_confidence_high -> {
                Log.d(TAG, "🔄 Sort: Highest Confidence")
                currentSortOrder = SortOrder.HIGHEST_CONFIDENCE
                displayResults()
                true
            }

            R.id.action_sort_confidence_low -> {
                Log.d(TAG, "🔄 Sort: Lowest Confidence")
                currentSortOrder = SortOrder.LOWEST_CONFIDENCE
                displayResults()
                true
            }

            R.id.action_sort_name_az -> {
                Log.d(TAG, "🔄 Sort: Name A-Z")
                currentSortOrder = SortOrder.NAME_AZ
                displayResults()
                true
            }

            R.id.action_sort_name_za -> {
                Log.d(TAG, "🔄 Sort: Name Z-A")
                currentSortOrder = SortOrder.NAME_ZA
                displayResults()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "🔄 onResume() - reloading history")
        loadHistory()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
