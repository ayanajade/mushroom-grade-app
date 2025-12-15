package com.example.mushroom_grader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.edit
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@ExperimentalCamera2Interop
class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var mlModelHelper: MLModelHelper
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isFocused = false
    private var captureCount = 0

    companion object {
        private const val TAG = "CameraActivity"
        private const val PREFS_NAME = "mushroom_grader"
        private const val KEY_FIRST_CAMERA_USE = "first_camera_use"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageProcessor = ImageProcessor(this)
        mlModelHelper = MLModelHelper(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Show guide instructions
        binding.tvDistance.setText(R.string.camera_checklist)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setupClickListeners()

        // Show tutorial hint on first use
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val firstTime = preferences.getBoolean(KEY_FIRST_CAMERA_USE, true)
        if (firstTime) {
            showQuickTips()
            preferences.edit {
                putBoolean(KEY_FIRST_CAMERA_USE, false)
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnCapture.setOnClickListener {
            if (!isFocused) {
                Toast.makeText(this, R.string.tap_to_focus_first, Toast.LENGTH_SHORT).show()
            } else {
                takePhoto()
            }
        }

        binding.btnClose.setOnClickListener {
            finish()
        }

        // Help button
        binding.btnHelp.setOnClickListener {
            showQuickTips()
        }
    }

    private fun showQuickTips() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.quick_tips_title)
            .setMessage(R.string.quick_tips_message)
            .setPositiveButton(R.string.got_it) { _, _ -> }
            .setNeutralButton(R.string.full_tutorial) { _, _ ->
                startActivity(Intent(this, ImagingTutorialActivity::class.java))
            }
            .show()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(cameraProvider)
            } catch (e: Exception) {
                val message = getString(R.string.camera_start_failed, e.message)
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Camera start failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        cameraProvider.unbindAll()

        val preview = Preview.Builder()
            .build()
            .also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

        // LOCKED SETTINGS for consistency
        val imageCaptureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetRotation(Surface.ROTATION_0)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)

        val camera2Interop = Camera2Interop.Extender(imageCaptureBuilder)
        camera2Interop.setCaptureRequestOption(
            android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE,
            android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_AUTO
        )
        camera2Interop.setCaptureRequestOption(
            android.hardware.camera2.CaptureRequest.SENSOR_SENSITIVITY,
            400
        )

        imageCapture = imageCaptureBuilder.build()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            val camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture
            )

            val cameraControl = camera.cameraControl
            val exposureState = camera.cameraInfo.exposureState
            val range = exposureState.exposureCompensationRange
            if (range.contains(0)) {
                cameraControl.setExposureCompensationIndex(0)
                Log.d(TAG, "✅ Exposure locked at neutral")
            }

            // Tap-to-focus with visual feedback
            binding.previewView.setOnTouchListener { view, event ->
                view.performClick()
                val factory = binding.previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = androidx.camera.core.FocusMeteringAction.Builder(point)
                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val future = cameraControl.startFocusAndMetering(action)
                future.addListener({
                    try {
                        val result = future.get()
                        if (result.isFocusSuccessful) {
                            runOnUiThread {
                                isFocused = true
                                binding.btnCapture.isEnabled = true
                                binding.btnCapture.setText(R.string.capture_focused)
                                Toast.makeText(this, R.string.focused_on_mushroom, Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Focus failed", e)
                    }
                }, ContextCompat.getMainExecutor(this))
                true
            }

            // Initial button state
            binding.btnCapture.isEnabled = true
            binding.btnCapture.setText(R.string.tap_mushroom_to_focus)
            Log.d(TAG, "✅ Camera configured with guidance system")

        } catch (e: Exception) {
            val message = getString(R.string.camera_bind_failed, e.message)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            Log.e(TAG, "❌ Camera binding failed", e)
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // Show reminder before first capture
        if (captureCount == 0) {
            Toast.makeText(this, R.string.hold_steady_tip, Toast.LENGTH_SHORT).show()
        }
        captureCount++

        binding.progressBar.visibility = View.VISIBLE
        binding.btnCapture.isEnabled = false
        binding.tvDistance.setText(R.string.capturing)

        val photoFile = createFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "✅ Photo captured with consistent settings")
                    binding.tvDistance.setText(R.string.processing)
                    processAndClassifyImage(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCapture.isEnabled = true
                    binding.btnCapture.setText(R.string.tap_mushroom_to_focus)
                    binding.tvDistance.setText(R.string.camera_checklist)
                    val message = getString(R.string.photo_capture_failed, exception.message)
                    Toast.makeText(this@CameraActivity, message, Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Photo capture failed", exception)
                }
            }
        )
    }

    private fun processAndClassifyImage(imageFile: File) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    Log.d(TAG, "🔄 Processing with training-consistent pipeline")

                    // Load and rotate image (EXIF handling only)
                    val bitmap = ImageProcessor.processImage(imageFile.absolutePath)

                    // Analyze lighting quality for user feedback only (doesn't modify image)
                    val lightingQuality = ImageProcessor.analyzeLightingQuality(bitmap)
                    Log.d(TAG, "✅ Lighting quality: $lightingQuality")

                    withContext(Dispatchers.Main) {
                        when (lightingQuality) {
                            ImageProcessor.Companion.LightingQuality.TOO_DARK -> {
                                binding.tvDistance.setText(R.string.dark_image_adjusting)
                            }
                            ImageProcessor.Companion.LightingQuality.TOO_BRIGHT -> {
                                binding.tvDistance.setText(R.string.bright_image_adjusting)
                            }
                            else -> {
                                binding.tvDistance.setText(R.string.good_lighting_processing)
                            }
                        }
                    }

                    // ✅ FIXED: Direct classification without enhancement
                    // Resize and normalization happen in MLModelHelper.preprocessImage()
                    // Model handles variations using learned patterns from 20 training augmentations
                    mlModelHelper.classifyImage(bitmap)
                }

                if (result != null) {
                    Log.d(TAG, "🎯 Classification: ${result.className} (${result.confidence})")
                    navigateToResult(result, imageFile)
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCapture.isEnabled = true
                    binding.btnCapture.setText(R.string.tap_mushroom_to_focus)
                    isFocused = false
                    binding.tvDistance.setText(R.string.camera_checklist)
                    showImagingTips()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnCapture.isEnabled = true
                binding.btnCapture.setText(R.string.tap_mushroom_to_focus)
                isFocused = false
                binding.tvDistance.setText(R.string.camera_checklist)
                val message = getString(R.string.classification_failed, e.message)
                Toast.makeText(this@CameraActivity, message, Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Classification error", e)
            }
        }
    }

    private fun showImagingTips() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.not_mushroom_title)
            .setMessage(R.string.imaging_tips_message)
            .setPositiveButton(R.string.try_again) { _, _ ->
                binding.btnCapture.isEnabled = true
            }
            .setNeutralButton(R.string.see_tutorial) { _, _ ->
                startActivity(Intent(this, ImagingTutorialActivity::class.java))
            }
            .setNegativeButton(R.string.cancel) { _, _ -> finish() }
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
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile("MUSHROOM_${timeStamp}_", ".jpg", storageDir)
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        mlModelHelper.close()
    }
}
