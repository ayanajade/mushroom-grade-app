package com.example.mushroom_grader


import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.yourpackage.mushroomgrader.databinding.ActivityHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        loadHistoryData()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun loadHistoryData() {
        // TODO: Load actual data from storage/database
        // For now, create sample history items
        createSampleHistoryItems()
    }

    private fun createSampleHistoryItems() {
        val sampleHistory = listOf(
            HistoryItem("Oyster Mushroom", "Edible", "Grade A", "Dec 15, 2024 - 2:30 PM", false),
            HistoryItem("Death Cap", "Poisonous", "", "Dec 14, 2024 - 10:15 AM", true),
            HistoryItem("Button Mushroom", "Edible", "Grade B", "Dec 13, 2024 - 4:45 PM", false),
            HistoryItem("Shiitake", "Edible", "Grade A", "Dec 12, 2024 - 11:20 AM", false)
        )

        // Hide the "no history" placeholder
        binding.noHistoryLayout.visibility = View.GONE

        // Add history items to container
        for (item in sampleHistory) {
            addHistoryItemToContainer(item)
        }
    }

    private fun addHistoryItemToContainer(item: HistoryItem) {
        val cardView = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 32)
            }
            radius = 12f
            cardElevation = 3f
            setCardBackgroundColor(ContextCompat.getColor(context,
                if (item.isPoisonous) R.color.danger_red else R.color.background_white
            ))
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(48, 48, 48, 48)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Species and details
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val speciesText = TextView(this).apply {
            text = item.species
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context,
                if (item.isPoisonous) R.color.white else R.color.text_primary
            ))
        }

        val dateText = TextView(this).apply {
            text = item.dateTime
            textSize = 12f
            setTextColor(ContextCompat.getColor(context,
                if (item.isPoisonous) R.color.white else R.color.text_secondary
            ))
            setPadding(0, 12, 0, 6)
        }

        val statusText = TextView(this).apply {
            text = if (item.grade.isNotEmpty()) "${item.type} • ${item.grade}" else item.type
            textSize = 14f
            setTextColor(ContextCompat.getColor(context,
                if (item.isPoisonous) R.color.white else R.color.primary_green
            ))
        }

        textContainer.addView(speciesText)
        textContainer.addView(dateText)
        textContainer.addView(statusText)

        // Arrow icon
        val arrowText = TextView(this).apply {
            text = "→"
            textSize = 20f
            setTextColor(ContextCompat.getColor(context,
                if (item.isPoisonous) R.color.white else R.color.text_secondary
            ))
        }

        contentLayout.addView(textContainer)
        contentLayout.addView(arrowText)
        cardView.addView(contentLayout)

        // Click listener to navigate to result view
        cardView.setOnClickListener {
            val intent = Intent(this, ResultActivity::class.java)
            intent.putExtra("is_poisonous", item.isPoisonous)
            intent.putExtra("species", item.species)
            intent.putExtra("type", item.type)
            intent.putExtra("grade", item.grade)
            intent.putExtra("from_history", true)
            startActivity(intent)
        }

        binding.historyContainer.addView(cardView)
    }

    data class HistoryItem(
        val species: String,
        val type: String,
        val grade: String,
        val dateTime: String,
        val isPoisonous: Boolean
    )
}