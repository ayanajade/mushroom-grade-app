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
import com.example.mushroom_grader.database.AppDatabase
import com.example.mushroom_grader.databinding.ActivityHistoryBinding
import com.example.mushroom_grader.ml.ClassificationResult
import com.example.mushroom_grader.ui.fragments.HistoryAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter

    // Keep ALL results from DB
    private val allResults = mutableListOf<ClassificationResult>()

    // Keep what is currently displayed (sorted + filtered)
    private var displayedResults: List<ClassificationResult> = emptyList()

    private var currentSortOrder = SortOrder.NEWEST_FIRST
    private var currentQuery: String = ""

    enum class SortOrder {
        NEWEST_FIRST,
        OLDEST_FIRST,
        HIGHEST_CONFIDENCE,
        LOWEST_CONFIDENCE,
        NAME_AZ,
        NAME_ZA
    }

    companion object {
        private const val TAG = "HistoryActivity"
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
                if (position == RecyclerView.NO_POSITION) return

                if (position >= displayedResults.size) {
                    // Safety fallback (shouldn't happen, but prevents crashes)
                    adapter.notifyItemChanged(position)
                    return
                }

                val result = displayedResults[position]
                Log.d(TAG, "👈 Swiped to delete: ${result.className} at position $position")
                deleteResult(result, position)
            }
        }

        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvHistory)
    }

    private fun loadHistory() {
        Log.d(TAG, "loadHistory called")

        lifecycleScope.launch {
            try {
                Log.d(TAG, "Fetching from database...")
                val results = withContext(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(applicationContext)
                    database.resultDao().getAllResults()
                }

                allResults.clear()
                allResults.addAll(results)

                Log.d(TAG, "✅ Loaded ${allResults.size} results")
                displayResults()

            } catch (ex: Exception) {
                Log.e(TAG, "❌ ERROR loading history: ${ex.message}", ex)

                Toast.makeText(
                    this@HistoryActivity,
                    getString(R.string.error_database),
                    Toast.LENGTH_LONG
                ).show()

                showEmptyState()
            }
        }
    }

    private fun displayResults() {
        Log.d(TAG, "🎨 displayResults() called")
        Log.d(TAG, "📊 allResults.size = ${allResults.size}")
        Log.d(TAG, "📊 currentSortOrder = $currentSortOrder")
        Log.d(TAG, "🔎 currentQuery = '$currentQuery'")

        val baseList = if (currentQuery.isBlank()) {
            allResults
        } else {
            allResults.filter {
                it.className.contains(currentQuery, ignoreCase = true) ||
                        (it.grade?.contains(currentQuery, ignoreCase = true) == true)
            }
        }

        val sortedResults = sortResults(baseList, currentSortOrder)
        displayedResults = sortedResults

        if (sortedResults.isNotEmpty()) {
            binding.emptyStateLayout.visibility = View.GONE
            binding.rvHistory.visibility = View.VISIBLE

            adapter.submitList(sortedResults)
            updateStats(sortedResults)
        } else {
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

        // These TextViews are designed as big numbers in your cards.
        binding.tvTotal.text = totalCount.toString()
        binding.tvPoisonous.text = poisonousCount.toString()
        binding.tvEdible.text = edibleCount.toString()
    }

    private fun showEmptyState() {
        Log.d(TAG, "📭 showEmptyState() called")

        binding.rvHistory.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE

        binding.tvTotal.text = "0"
        binding.tvPoisonous.text = "0"
        binding.tvEdible.text = "0"

        // Keep adapter in a clean state
        displayedResults = emptyList()
        adapter.submitList(emptyList())
    }

    private fun setupClickListeners() {
        Log.d(TAG, "⚙️ Setting up click listeners")

        binding.btnClear.setOnClickListener {
            Log.d(TAG, "🗑️ Clear button clicked")
            if (allResults.isNotEmpty()) {
                showClearAllConfirmation()
            } else {
                Toast.makeText(this, R.string.no_history, Toast.LENGTH_SHORT).show()
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

    private fun deleteResult(result: ClassificationResult, positionInDisplayed: Int) {
        Log.d(TAG, "🗑️ deleteResult() - ${result.className} at displayed position $positionInDisplayed")

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(applicationContext)
                    database.resultDao().deleteResult(result)
                }

                // Remove from master list by identity (id) to keep correctness after sort/search.
                allResults.removeAll { it.id == result.id }

                // Update UI by rebuilding list (keeps sorting/search consistent)
                displayResults()

                Snackbar.make(binding.root, R.string.deleted_successfully, Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.cancel).uppercase()) {
                        Log.d(TAG, "↩️ Undo delete")
                        undoDelete(result)
                    }
                    .show()

            } catch (ex: Exception) {
                Log.e(TAG, "❌ Failed to delete", ex)

                Toast.makeText(
                    this@HistoryActivity,
                    R.string.error_database,
                    Toast.LENGTH_SHORT
                ).show()

                // Restore swiped item visually
                if (positionInDisplayed in displayedResults.indices) {
                    adapter.notifyItemChanged(positionInDisplayed)
                } else {
                    adapter.notifyDataSetChanged()
                }
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

                Toast.makeText(this@HistoryActivity, "Restored", Toast.LENGTH_SHORT).show()
                loadHistory()

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
            .setTitle(R.string.clear_history)
            .setMessage(getString(R.string.delete_confirmation))
            .setPositiveButton(R.string.delete_all) { _, _ ->
                Log.d(TAG, "✅ User confirmed clear all")
                clearAllHistory()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
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

                allResults.clear()
                currentQuery = ""
                currentSortOrder = SortOrder.NEWEST_FIRST

                adapter.clear()
                showEmptyState()

                Toast.makeText(this@HistoryActivity, "History cleared", Toast.LENGTH_SHORT).show()

            } catch (ex: Exception) {
                Log.e(TAG, "❌ Failed to clear history", ex)
                Toast.makeText(this@HistoryActivity, "Failed to clear history", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_history, menu)

        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView

        searchView?.queryHint = getString(R.string.search)

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
        currentQuery = query.trim()
        displayResults()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                Log.d(TAG, "⬅️ Back button pressed")
                finish()
                true
            }

            R.id.action_sort_newest -> {
                currentSortOrder = SortOrder.NEWEST_FIRST
                displayResults()
                true
            }

            R.id.action_sort_oldest -> {
                currentSortOrder = SortOrder.OLDEST_FIRST
                displayResults()
                true
            }

            R.id.action_sort_confidence_high -> {
                currentSortOrder = SortOrder.HIGHEST_CONFIDENCE
                displayResults()
                true
            }

            R.id.action_sort_confidence_low -> {
                currentSortOrder = SortOrder.LOWEST_CONFIDENCE
                displayResults()
                true
            }

            R.id.action_sort_name_az -> {
                currentSortOrder = SortOrder.NAME_AZ
                displayResults()
                true
            }

            R.id.action_sort_name_za -> {
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
