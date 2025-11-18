package com.example.mushroom_grader

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.mushroom_grader.database.AppDatabase
import com.example.mushroom_grader.databinding.ActivityResultBinding
import com.example.mushroom_grader.ml.ClassificationResult
import com.example.mushroom_grader.ml.MLModelHelper
import com.example.mushroom_grader.ml.MushroomCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        supportActionBar?.title = "Classification Result"

        mlModelHelper = MLModelHelper(this)
        getIntentData()
        displayResults()
        setupClickListeners()
        saveToDatabase()
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
            try {
                val bitmap = BitmapFactory.decodeFile(it)
                binding.ivMushroom.setImageBitmap(bitmap)
            } catch (e: Exception) {
                binding.ivMushroom.setImageResource(android.R.mipmap.sym_def_app_icon)
            }
        }

        binding.tvMushroomName.text = className
        val confidencePercent = String.format(Locale.getDefault(), "%.2f%%", confidence * 100)
        binding.tvConfidence.text = "Confidence: $confidencePercent"

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
                binding.tvSafetyTitle.text = "⚠️ POISONOUS"
                binding.tvSafetyMessage.text = "This mushroom is POISONOUS and MUST NOT be consumed."
                binding.tvSafetyTitle.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                binding.tvSafetyMessage.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            }

            category == MushroomCategory.INEDIBLE.name -> {
                binding.cardSafety.setCardBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.holo_orange_dark)
                )
                binding.tvSafetyTitle.text = "⚠️ INEDIBLE"
                binding.tvSafetyMessage.text = "This mushroom is not recommended for consumption."
                binding.tvSafetyTitle.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                binding.tvSafetyMessage.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            }

            else -> {
                binding.cardSafety.setCardBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.holo_green_dark)
                )
                binding.tvSafetyTitle.text = "✓ SAFE TO EAT"
                binding.tvSafetyMessage.text = "This mushroom appears to be safe to consume."
                binding.tvSafetyTitle.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                binding.tvSafetyMessage.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            }
        }
    }

    private fun displayGrade() {
        if (grade != null) {
            binding.cardGrade.visibility = View.VISIBLE
            binding.tvGradeValue.text = grade
        } else {
            binding.cardGrade.visibility = View.GONE
        }
    }

    private fun displayDetailedInfo() {
        val detailedInfo = mlModelHelper.getMushroomInfo(classId)
        binding.tvDetailedInfo.text = detailedInfo
        binding.tvCategory.text = "Category: $category"
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
            .setTitle("Detailed Information")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun saveToDatabase() {
        lifecycleScope.launch {
            try {
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
                    val database = AppDatabase.getDatabase(applicationContext)
                    database.resultDao().insertResult(result)
                }
            } catch (e: Exception) {
                // Silent fail for database errors
            }
        }
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
