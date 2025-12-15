package com.example.mushroom_grader

import android.annotation.SuppressLint
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
    private val tag = "GalleryProcessActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryProcessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageProcessor = ImageProcessor(this)
        mlModelHelper = MLModelHelper(this)

        val imageUriString = intent.getStringExtra("imageUri")
        if (imageUriString != null) {
            processImage(imageUriString.toUri())
        } else {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun processImage(uri: Uri) {
        binding.tvProcessing.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Load bitmap with proper EXIF handling (instance method - needs context)
                val bitmap = imageProcessor.loadBitmapFromUri(uri)
                if (bitmap == null) {
                    showError("Failed to load image")
                    return@launch
                }

                binding.ivPreview.setImageBitmap(bitmap)

                // Analyze lighting quality for user feedback only
                val lightingQuality = ImageProcessor.analyzeLightingQuality(bitmap)
                Log.d(tag, "📊 Lighting quality: $lightingQuality")

                when (lightingQuality) {
                    ImageProcessor.Companion.LightingQuality.TOO_DARK -> {
                        binding.tvProcessing.text = "⚠️ Image is too dark. Processing..."
                        Toast.makeText(this@GalleryProcessActivity,
                            "Image appears dark. Model will try to handle it.",
                            Toast.LENGTH_SHORT).show()
                    }
                    ImageProcessor.Companion.LightingQuality.TOO_BRIGHT -> {
                        binding.tvProcessing.text = "⚠️ Image is too bright. Processing..."
                        Toast.makeText(this@GalleryProcessActivity,
                            "Image appears bright. Model will try to handle it.",
                            Toast.LENGTH_SHORT).show()
                    }
                    ImageProcessor.Companion.LightingQuality.SLIGHTLY_DARK -> {
                        binding.tvProcessing.text = "Processing image (slightly dark)..."
                    }
                    ImageProcessor.Companion.LightingQuality.SLIGHTLY_BRIGHT -> {
                        binding.tvProcessing.text = "Processing image (slightly bright)..."
                    }
                    ImageProcessor.Companion.LightingQuality.GOOD -> {
                        binding.tvProcessing.text = "Processing image (good lighting)..."
                    }
                }

                // Save bitmap to file (instance method - needs context)
                val savedPath = imageProcessor.saveBitmapToFile(
                    bitmap,
                    imageProcessor.generateFileName("gallery")
                )

                if (savedPath == null) {
                    showError("Failed to save image")
                    return@launch
                }

                Log.d(tag, "✅ Image saved to: $savedPath")

                // ✅ FIXED: Direct processing without enhancement
                // Load and rotate only - resize and normalization happen in MLModelHelper
                val processedBitmap = withContext(Dispatchers.Default) {
                    ImageProcessor.processImage(savedPath)
                }

                Log.d(tag, "✅ Image loaded with training-consistent preprocessing")

                // Classify the image
                val result = withContext(Dispatchers.Default) {
                    mlModelHelper.classifyImage(processedBitmap)
                }

                // Handle all confidence levels
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
