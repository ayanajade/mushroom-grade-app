package com.example.mushroom_grader

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mushroom_grader.database.AppDatabase
import com.example.mushroom_grader.databinding.ActivityHistoryBinding
import com.example.mushroom_grader.ml.ClassificationResult
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var historyAdapter: HistoryAdapter
    private val historyList = mutableListOf<ClassificationResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.analysis_history)

        setupRecyclerView()
        setupClickListeners()
        loadHistory()
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(historyList) { result ->
            showResultDetail(result)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = historyAdapter
        }
    }

    private fun setupClickListeners() {
        // Clear all button
        binding.btnClearAll.setOnClickListener {
            showClearConfirmationDialog()
        }

        // Filter buttons
        binding.chipAll.setOnClickListener {
            loadHistory()
        }

        binding.chipPoisonous.setOnClickListener {
            filterPoisonous()
        }

        binding.chipEdible.setOnClickListener {
            filterEdible()
        }
    }

    private fun loadHistory() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(applicationContext)
                    database.resultDao().getAllResults()
                }

                historyList.clear()
                historyList.addAll(results)
                historyAdapter.notifyItemRangeChanged(0, historyList.size)

                binding.progressBar.visibility = View.GONE

                if (historyList.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                }

                updateStats(results)

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                showError(getString(R.string.error_load_history, e.message))
            }
        }
    }

    private fun filterPoisonous() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(applicationContext)
                    database.resultDao().getPoisonousResults()
                }

                historyList.clear()
                historyList.addAll(results)
                historyAdapter.notifyItemRangeChanged(0, historyList.size)

                binding.progressBar.visibility = View.GONE

                if (historyList.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                showError(getString(R.string.error_filter, e.message))
            }
        }
    }

    private fun filterEdible() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(applicationContext)
                    database.resultDao().getEdibleResults()
                }

                historyList.clear()
                historyList.addAll(results)
                historyAdapter.notifyItemRangeChanged(0, historyList.size)

                binding.progressBar.visibility = View.GONE

                if (historyList.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                showError(getString(R.string.error_filter, e.message))
            }
        }
    }

    private fun updateStats(results: List<ClassificationResult>) {
        val total = results.size
        val poisonous = results.count { it.isPoisonous }
        val edible = results.count { !it.isPoisonous }

        binding.tvTotal.text = getString(R.string.total_format, total)
        binding.tvPoisonous.text = getString(R.string.poisonous_format, poisonous)
        binding.tvEdible.text = getString(R.string.edible_format, edible)
    }

    private fun showResultDetail(result: ClassificationResult) {
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra("className", result.className)
            putExtra("classId", result.classId)
            putExtra("confidence", result.confidence)
            putExtra("isPoisonous", result.isPoisonous)
            putExtra("category", result.category.name)
            putExtra("grade", result.grade)
            putExtra("imagePath", result.imagePath)
        }
        startActivity(intent)
    }

    private fun showClearConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_history_title)
            .setMessage(R.string.clear_history_message)
            .setPositiveButton(R.string.delete_all) { _, _ ->
                clearAllHistory()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearAllHistory() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(applicationContext)
                    database.resultDao().deleteAll()
                }

                historyList.clear()
                historyAdapter.notifyItemRangeChanged(0, historyList.size)

                binding.emptyState.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE

                updateStats(emptyList())

            } catch (e: Exception) {
                showError(getString(R.string.error_clear_history, e.message))
            }
        }
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error_title)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }
}

// RecyclerView Adapter
class HistoryAdapter(
    private val items: List<ClassificationResult>,
    private val onItemClick: (ClassificationResult) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardItem)
        val image: ImageView = view.findViewById(R.id.ivMushroom)
        val name: TextView = view.findViewById(R.id.tvName)
        val confidence: TextView = view.findViewById(R.id.tvConfidence)
        val timestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val safetyBadge: TextView = view.findViewById(R.id.tvSafetyBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        // Set mushroom name
        holder.name.text = item.className

        // Set confidence
        holder.confidence.text = item.getConfidencePercentage()

        // Set timestamp
        holder.timestamp.text = item.getFormattedTimestamp()

        // Set safety badge
        if (item.isPoisonous) {
            holder.safetyBadge.text = context.getString(R.string.badge_poisonous)
            holder.safetyBadge.setBackgroundColor(
                context.getColor(android.R.color.holo_red_dark)
            )
        } else {
            holder.safetyBadge.text = context.getString(R.string.badge_safe)
            holder.safetyBadge.setBackgroundColor(
                context.getColor(android.R.color.holo_green_dark)
            )
        }

        // Load image
        item.imagePath?.let { path ->
            try {
                val bitmap = BitmapFactory.decodeFile(path)
                holder.image.setImageBitmap(bitmap)
            } catch (e: Exception) {
                holder.image.setImageResource(R.mipmap.ic_launcher)
            }
        }

        // Click listener
        holder.card.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount() = items.size
}
