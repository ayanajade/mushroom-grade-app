package com.example.mushroom_grader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.mushroom_grader.databinding.ActivityCameraBinding
import com.example.mushroom_grader.ml.ImageProcessor
import com.example.mushroom_grader.ml.MLModelHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var mlModelHelper: MLModelHelper
    private var imageCapture: ImageCapture? = null

    // ✅ FIXED: Changed to lowercase to follow Kotlin naming conventions
    private val tag = "CameraActivity"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageProcessor = ImageProcessor(this)
        mlModelHelper = MLModelHelper(this)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnCapture.setOnClickListener {
            takePhoto()
        }
        binding.btnClose.setOnClickListener {
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(cameraProvider)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(tag, "Camera start failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

        // ✅ FIXED: Removed deprecated setTargetResolution() and defaultDisplay
        // Using aspect ratio instead for better compatibility
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetRotation(Surface.ROTATION_0) // Portrait orientation
            .build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture
            )
            Log.d(tag, "✅ Camera bound successfully with quality optimization")
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to bind camera: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(tag, "Camera binding failed", e)
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.btnCapture.isEnabled = false

        val photoFile = createFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    processAndClassifyImage(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnCapture.isEnabled = true
                    Toast.makeText(
                        this@CameraActivity,
                        "Photo capture failed: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e(tag, "Photo capture failed", exception)
                }
            }
        )
    }

    private fun processAndClassifyImage(imageFile: File) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    // Apply initial processing (rotation, resize)
                    val bitmap = imageProcessor.processImage(imageFile.absolutePath)

                    // ✅ Apply image enhancement for better detection
                    val enhancedBitmap = imageProcessor.enhanceImageForDetection(bitmap)

                    // Classify the enhanced image
                    mlModelHelper.classifyImage(enhancedBitmap)
                }

                if (result != null) {
                    navigateToResult(result, imageFile)
                } else {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnCapture.isEnabled = true
                    showNonMushroomError()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnCapture.isEnabled = true
                Toast.makeText(
                    this@CameraActivity,
                    "Classification failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e(tag, "Classification error", e)
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
                        "• Poor lighting conditions\n\n" +
                        "💡 Tips for better detection:\n" +
                        "• Ensure good natural lighting\n" +
                        "• Keep the mushroom centered\n" +
                        "• Make sure the entire mushroom is visible\n" +
                        "• Avoid shadows and reflections\n" +
                        "• Hold camera steady to avoid blur"
            )
            .setPositiveButton("Try Again") { _, _ -> }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }

    private fun navigateToResult(result: com.example.mushroom_grader.ml.ClassificationResult, imageFile: File) {
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra("className", result.className)
            putExtra("classId", result.classId)
            putExtra("confidence", result.confidence)
            putExtra("isPoisonous", result.isPoisonous)
            putExtra("category", result.category.name)
            putExtra("grade", result.grade)
            putExtra("imagePath", imageFile.absolutePath)
        }

        startActivity(intent)
        finish()
    }

    private fun createFile(): File {
        // ✅ FIXED: Typo "Hmmss" changed to "HHmmss"
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile("MUSHROOM_${timeStamp}_", ".jpg", storageDir)
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        super.onDestroy()
        mlModelHelper.close()
    }
}
