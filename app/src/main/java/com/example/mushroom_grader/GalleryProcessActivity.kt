package com.example.mushroom_grader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.mushroom_grader.databinding.ActivityGalleryProcessBinding
import com.example.mushroom_grader.database.AppDatabase
import com.example.mushroom_grader.ml.ImageProcessor
import com.example.mushroom_grader.ml.MLModelHelper
import com.example.mushroom_grader.ml.ClassificationResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryProcessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryProcessBinding
    private lateinit var mlModelHelper: MLModelHelper
    private lateinit var imageProcessor: ImageProcessor

    companion object {
        private const val TAG = "GalleryProcessActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryProcessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.processing_image)

        // Initialize ML components
        mlModelHelper = MLModelHelper(this)
        imageProcessor = ImageProcessor(this)

        // Get image URI from intent
        val imageUriString = intent.getStringExtra("imageUri")
        if (imageUriString != null) {
            val imageUri = imageUriString.toUri()  // Use KTX extension
            processImage(imageUri)
        } else {
            showError(getString(R.string.error_no_image))
            finish()
        }
    }

    private fun processImage(uri: Uri) {
        showLoading(true)

        // Display the selected image
        binding.ivPreview.setImageURI(uri)

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    // Load bitmap from URI
                    val bitmap = imageProcessor.loadBitmapFromUri(uri)

                    if (bitmap != null) {
                        // Classify image
                        mlModelHelper.classifyImage(bitmap)
                    } else {
                        null
                    }
                }

                showLoading(false)

                if (result != null) {
                    // Save image to permanent storage
                    val savedPath = withContext(Dispatchers.IO) {
                        val bitmap = imageProcessor.loadBitmapFromUri(uri)
                        bitmap?.let {
                            imageProcessor.saveBitmapToFile(
                                it,
                                imageProcessor.generateFileName("mushroom")
                            )
                        }
                    }

                    // Save result to database
                    saveResult(result, savedPath)

                    // Show result screen
                    showResultScreen(result, savedPath)
                } else {
                    showError(getString(R.string.error_classification_failed))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Image processing failed", e)
                showLoading(false)
                showError(getString(R.string.error_processing, e.message ?: "Unknown error"))
            }
        }
    }

    private suspend fun saveResult(result: ClassificationResult, imagePath: String?) {
        withContext(Dispatchers.IO) {
            try {
                val resultToSave = result.copy(imagePath = imagePath)
                val database = AppDatabase.getDatabase(applicationContext)
                database.resultDao().insertResult(resultToSave)
                Log.d(TAG, "Result saved to database")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save result", e)
            }
        }
    }

    private fun showResultScreen(result: ClassificationResult, imagePath: String?) {
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

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvProcessing.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error_title)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _, _ ->
                finish()
            }
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
