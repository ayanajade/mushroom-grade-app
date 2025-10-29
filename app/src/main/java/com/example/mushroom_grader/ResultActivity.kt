package com.example.mushroom_grader

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View

import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

import com.example.mushroom_grader.databinding.ActivityResultBinding
import com.example.mushroom_grader.ml.MLModelHelper
import com.example.mushroom_grader.ml.MushroomCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private lateinit var mlModelHelper: MLModelHelper

    private var className: String = ""
    private var classId: Int = 0
    private var confidence: Float = 0f
    private var isPoisonous: Boolean = false
    private var category: String = ""
    private var grade: String? = null
    private var imagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.classification_result)

        mlModelHelper = MLModelHelper(this)

        getIntentData()
        displayResults()
        setupClickListeners()
    }

    private fun getIntentData() {
        className = intent.getStringExtra("className") ?: "Unknown"
        classId = intent.getIntExtra("classId", -1)
        confidence = intent.getFloatExtra("confidence", 0f)
        isPoisonous = intent.getBooleanExtra("isPoisonous", false)
        category = intent.getStringExtra("category") ?: "INEDIBLE"
        grade = intent.getStringExtra("grade")
        imagePath = intent.getStringExtra("imagePath")
    }

    private fun displayResults() {
        imagePath?.let {
            val bitmap = BitmapFactory.decodeFile(it)
            binding.ivMushroom.setImageBitmap(bitmap)
        }

        binding.tvMushroomName.text = className

        val confidencePercent = String.format(Locale.getDefault(), "%.2f%%", confidence * 100)
        binding.tvConfidence.text = getString(R.string.confidence_format, confidencePercent)

        displaySafetyStatus()
        displayGrade()
        displayDetailedInfo()
    }

    private fun displaySafetyStatus() {
        when {
            isPoisonous -> {
                binding.cardSafety.setCardBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.holo_red_dark)
                )
                binding.tvSafetyTitle.text = getString(R.string.danger_poisonous)
                binding.tvSafetyMessage.text = getString(R.string.poisonous_warning)
                binding.tvSafetyTitle.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                binding.tvSafetyMessage.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            }
            category == MushroomCategory.INEDIBLE.name -> {
                binding.cardSafety.setCardBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.holo_orange_dark)
                )
                binding.tvSafetyTitle.text = getString(R.string.warning_inedible)
                binding.tvSafetyMessage.text = getString(R.string.inedible_warning)
                binding.tvSafetyTitle.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                binding.tvSafetyMessage.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            }
            else -> {
                binding.cardSafety.setCardBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.holo_green_dark)
                )
                binding.tvSafetyTitle.text = getString(R.string.safe_to_eat)
                binding.tvSafetyMessage.text = getString(R.string.safe_warning)
                binding.tvSafetyTitle.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                binding.tvSafetyMessage.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            }
        }
    }

    private fun displayGrade() {
        if (grade != null) {
            binding.cardGrade.visibility = View.VISIBLE
            binding.tvGradeValue.text = grade

            val gradeDescription = when (grade) {
                "Class A", "Extra" -> getString(R.string.grade_premium)
                "Class B", "Class I" -> getString(R.string.grade_good)
                "Class C", "Class II" -> getString(R.string.grade_fair)
                "Defective" -> getString(R.string.grade_defective)
                else -> ""
            }
            binding.tvGradeDescription.text = gradeDescription
        } else {
            binding.cardGrade.visibility = View.GONE
        }
    }

    private fun displayDetailedInfo() {
        val detailedInfo = mlModelHelper.getMushroomInfo(classId)
        binding.tvDetailedInfo.text = detailedInfo

        val categoryText = when (category) {
            MushroomCategory.EDIBLE.name -> getString(R.string.category_edible)
            MushroomCategory.POISONOUS.name -> getString(R.string.category_poisonous)
            MushroomCategory.INEDIBLE.name -> getString(R.string.category_inedible)
            else -> getString(R.string.category_unknown)
        }
        binding.tvCategory.text = categoryText
    }

    private fun setupClickListeners() {
        binding.btnShare.setOnClickListener {
            shareResult()
        }

        binding.btnTakeAnother.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
            finish()
        }

        binding.btnViewHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
            finish()
        }

        binding.btnBackHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }

        binding.btnMoreInfo.setOnClickListener {
            showMoreInfoDialog()
        }
    }

    private fun shareResult() {
        val shareText = buildString {
            append("Mushroom Classification Result\n\n")
            append("Species: $className\n")
            append("Confidence: ${String.format(Locale.getDefault(), "%.2f%%", confidence * 100)}\n")
            append("Safety: ${if (isPoisonous) "⚠️ POISONOUS" else "✓ SAFE"}\n")
            grade?.let { append("Grade: $it\n") }
            append("\nGenerated by Mushroom Grader App")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Mushroom Classification: $className")
        }

        startActivity(Intent.createChooser(shareIntent, "Share Result"))
    }

    private fun showMoreInfoDialog() {
        val message = mlModelHelper.getMushroomInfo(classId)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.detailed_information)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        mlModelHelper.close()
    }
}
