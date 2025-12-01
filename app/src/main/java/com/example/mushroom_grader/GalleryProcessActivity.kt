package com.example.mushroom_grader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.mushroom_grader.databinding.ActivityGalleryProcessBinding
import com.example.mushroom_grader.ml.ImageProcessor
import com.example.mushroom_grader.ml.MLModelHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class GalleryProcessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryProcessBinding
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var mlModelHelper: MLModelHelper

    // ✅ FIXED: Changed to lowercase to follow Kotlin naming conventions
    private val tag = "GalleryProcessActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryProcessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageProcessor = ImageProcessor(this)
        mlModelHelper = MLModelHelper(this)

        val imageUriString = intent.getStringExtra("imageUri")
        if (imageUriString != null) {
            // ✅ FIXED: Using KTX extension function String.toUri()
            processImage(imageUriString.toUri())
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
                // ✅ Load bitmap with proper EXIF handling
                val bitmap = imageProcessor.loadBitmapFromUri(uri)
                if (bitmap == null) {
                    showError("Failed to load image")
                    return@launch
                }

                binding.ivPreview.setImageBitmap(bitmap)

                // Save bitmap to file
                val savedPath = imageProcessor.saveBitmapToFile(
                    bitmap,
                    imageProcessor.generateFileName("gallery")
                )

                if (savedPath == null) {
                    showError("Failed to save image")
                    return@launch
                }

                Log.d(tag, "✅ Image saved to: $savedPath")

                // ✅ Process with full enhancement pipeline
                val processedBitmap = withContext(Dispatchers.Default) {
                    val basic = imageProcessor.processImage(savedPath)
                    imageProcessor.enhanceImageForDetection(basic)
                }

                Log.d(tag, "✅ Image enhancement complete")

                // Classify the enhanced image
                val result = withContext(Dispatchers.Default) {
                    mlModelHelper.classifyImage(processedBitmap)
                }

                // ✅ Handle all confidence levels
                if (result != null) {
                    val confidenceLevel = mlModelHelper.getConfidenceLevel(result.confidence)
                    Log.d(tag, "🎯 Detection result: ${result.className} (${result.confidence}, Level: $confidenceLevel)")

                    when (confidenceLevel) {
                        "REJECTED" -> {
                            binding.tvProcessing.visibility = View.GONE
                            binding.progressBar.visibility = View.GONE
                            showNonMushroomError()
                        }

                        "RETAKE" -> {
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
                    binding.tvProcessing.visibility = View.GONE
                    binding.progressBar.visibility = View.GONE
                    Log.d(tag, "❌ Detection returned null")
                    showNonMushroomError()
                }

            } catch (e: Exception) {
                Log.e(tag, "Error processing image", e)
                showError("Error processing image: ${e.message}")
            }
        }
    }

    private fun showNonMushroomError() {
        MaterialAlertDialogBuilder(this)
            .setTitle("❌ Not a Mushroom Detected")
            .setMessage(
                "The image quality or content does not appear to be a mushroom.\n\n" +
                        "This could mean:\n" +
                        "• The image shows a non-mushroom object\n" +
                        "• The mushroom is partially visible or obscured\n" +
                        "• The image is unclear or out of focus\n" +
                        "• Poor image quality or color space issues\n" +
                        "• The confidence is too low (<60%)\n\n" +
                        "💡 Tips for better results:\n" +
                        "• Use high-resolution images\n" +
                        "• Ensure good lighting\n" +
                        "• Keep mushroom centered and fully visible\n" +
                        "• Avoid blurry or heavily compressed images"
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

    private fun showLowConfidenceWarning(
        result: com.example.mushroom_grader.ml.ClassificationResult,
        imagePath: String
    ) {
        // ✅ FIXED: Explicitly specify Locale.US for String.format()
        val confidencePercent = String.format(Locale.US, "%.1f%%", result.confidence * 100)

        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Low Confidence Detection")
            .setMessage(
                "Detected: ${result.className}\n" +
                        "Confidence: $confidencePercent\n\n" +
                        "The confidence level is low (60-85%). This may not be accurate.\n\n" +
                        "Recommendations:\n" +
                        "• Select a clearer photo with better lighting\n" +
                        "• Ensure the mushroom is fully visible\n" +
                        "• Try a different angle or image\n" +
                        "• Use a higher resolution photo\n\n" +
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

    private fun navigateToResult(
        result: com.example.mushroom_grader.ml.ClassificationResult,
        imagePath: String
    ) {
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
