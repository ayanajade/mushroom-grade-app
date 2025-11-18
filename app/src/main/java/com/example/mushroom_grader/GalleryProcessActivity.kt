package com.example.mushroom_grader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.mushroom_grader.databinding.ActivityGalleryProcessBinding
import com.example.mushroom_grader.ml.ImageProcessor
import com.example.mushroom_grader.ml.MLModelHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryProcessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryProcessBinding
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var mlModelHelper: MLModelHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryProcessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageProcessor = ImageProcessor(this)
        mlModelHelper = MLModelHelper(this)

        val imageUriString = intent.getStringExtra("imageUri")
        if (imageUriString != null) {
            processImage(Uri.parse(imageUriString))
        } else {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun processImage(uri: Uri) {
        binding.tvProcessing.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val bitmap = imageProcessor.loadBitmapFromUri(uri)
                if (bitmap == null) {
                    showError("Failed to load image")
                    return@launch
                }

                binding.ivPreview.setImageBitmap(bitmap)

                val savedPath = imageProcessor.saveBitmapToFile(
                    bitmap,
                    imageProcessor.generateFileName("gallery")
                )

                if (savedPath == null) {
                    showError("Failed to save image")
                    return@launch
                }

                // ✅ ENHANCED: Classification with threshold check
                val result = withContext(Dispatchers.Default) {
                    mlModelHelper.classifyImage(bitmap)
                }

                // ✅ ENHANCED: Check if result is null (below threshold or non-mushroom)
                if (result != null) {
                    // ✅ ENHANCED: Additional confidence check
                    val confidenceLevel = mlModelHelper.getConfidenceLevel(result.confidence)

                    when (confidenceLevel) {
                        "REJECTED" -> {
                            // Below minimum threshold
                            binding.tvProcessing.visibility = View.GONE
                            binding.progressBar.visibility = View.GONE
                            showNonMushroomError()
                        }
                        "RETAKE" -> {
                            // Low confidence (60-85%)
                            binding.tvProcessing.visibility = View.GONE
                            binding.progressBar.visibility = View.GONE
                            showLowConfidenceWarning(result, savedPath)
                        }
                        else -> {
                            // Good confidence (>85%)
                            navigateToResult(result, savedPath)
                        }
                    }
                } else {
                    // Result is null - definitely not a mushroom or too low confidence
                    binding.tvProcessing.visibility = View.GONE
                    binding.progressBar.visibility = View.GONE
                    showNonMushroomError()
                }

            } catch (e: Exception) {
                showError("Error processing image: ${e.message}")
            }
        }
    }

    // ✅ ENHANCED: Non-mushroom detection dialog
    private fun showNonMushroomError() {
        MaterialAlertDialogBuilder(this)
            .setTitle("❌ Not a Mushroom Detected")
            .setMessage(
                "The image quality or content does not appear to be a mushroom.\n\n" +
                        "This could mean:\n" +
                        "• The image shows a non-mushroom object\n" +
                        "• The mushroom is partially visible or obscured\n" +
                        "• The image is unclear or out of focus\n" +
                        "• The confidence is too low (<60%)\n\n" +
                        "Please select a clearer picture of an entire mushroom."
            )
            .setPositiveButton("Choose Another") { _, _ ->
                finish()
            }
            .setNegativeButton("Cancel") { _, _ ->
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .setCancelable(false)
            .show()
    }

    // ✅ ENHANCED: Low confidence warning (60-85%)
    private fun showLowConfidenceWarning(result: com.example.mushroom_grader.ml.ClassificationResult, imagePath: String) {
        val confidencePercent = String.format("%.1f%%", result.confidence * 100)

        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Low Confidence Detection")
            .setMessage(
                "Detected: ${result.className}\n" +
                        "Confidence: $confidencePercent\n\n" +
                        "The confidence level is low (60-85%). This may not be accurate.\n\n" +
                        "Recommendations:\n" +
                        "• Take a clearer photo with better lighting\n" +
                        "• Ensure the mushroom is fully visible\n" +
                        "• Try a different angle\n\n" +
                        "Do you want to proceed with this result or try another image?"
            )
            .setPositiveButton("Proceed Anyway") { _, _ ->
                navigateToResult(result, imagePath)
            }
            .setNegativeButton("Choose Another") { _, _ ->
                finish()
            }
            .setNeutralButton("Go Home") { _, _ ->
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun navigateToResult(result: com.example.mushroom_grader.ml.ClassificationResult, imagePath: String) {
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra("className", result.className)
            putExtra("classId", result.classId)
            putExtra("confidence", result.confidence)
            putExtra("isPoisonous", result.isPoisonous)
            putExtra("category", result.category.name)
            putExtra("grade", result.grade)
            putExtra("imagePath", imagePath)
        }
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        binding.tvProcessing.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        mlModelHelper.close()
    }
}
