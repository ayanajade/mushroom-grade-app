package com.example.mushroom_grader

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.mushroom_grader.databinding.ActivityMainBinding
import com.example.mushroom_grader.utils.PermissionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionManager: PermissionManager

    // Gallery launcher
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            processGalleryImage(it)
        }
    }

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            openCamera()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionManager = PermissionManager(this)

        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        // Set up action bar
        supportActionBar?.title = getString(R.string.app_name)

        // Welcome message
        binding.tvWelcome.text = getString(R.string.welcome_message)
        binding.tvDescription.text = getString(R.string.app_description)
    }


    private fun setupClickListeners() {
        // Camera button
        binding.btnCamera.setOnClickListener {
            checkPermissionsAndOpenCamera()
        }

        // Gallery button
        binding.btnGallery.setOnClickListener {
            openGallery()
        }

        // History button
        binding.btnHistory.setOnClickListener {
            openHistory()
        }

        // About button
        binding.btnAbout.setOnClickListener {
            openAbout()
        }
    }

    /**
     * Check camera permissions and open camera
     */
    private fun checkPermissionsAndOpenCamera() {
        when {
            permissionManager.hasCameraPermission() -> {
                openCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionRationaleDialog()
            }
            else -> {
                requestPermissions()
            }
        }
    }

    /**
     * Request camera permissions
     */
    private fun requestPermissions() {
        requestPermissionLauncher.launch(permissionManager.getRequiredPermissions())
    }

    /**
     * Open camera activity
     */
    private fun openCamera() {
        val intent = Intent(this, CameraActivity::class.java)
        startActivity(intent)
    }

    /**
     * Open gallery to select image - NOW FULLY FUNCTIONAL!
     */
    private fun openGallery() {
        if (permissionManager.hasStoragePermission()) {
            galleryLauncher.launch("image/*")
        } else {
            requestPermissions()
        }
    }

    /**
     * Process selected gallery image
     */
    private fun processGalleryImage(uri: Uri) {
        val intent = Intent(this, GalleryProcessActivity::class.java).apply {
            putExtra("imageUri", uri.toString())
        }
        startActivity(intent)
    }

    /**
     * Open history activity
     */
    private fun openHistory() {
        val intent = Intent(this, HistoryActivity::class.java)
        startActivity(intent)
    }

    /**
     * Open about activity
     */
    private fun openAbout() {
        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)
    }

    /**
     * Show dialog explaining why permissions are needed
     */
    private fun showPermissionRationaleDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Camera Permission Required")
            .setMessage(
                "This app needs camera access to capture mushroom photos for identification. " +
                        "Without camera permission, you won't be able to use the main feature of the app."
            )
            .setPositiveButton("Grant Permission") { _, _ ->
                requestPermissions()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show dialog when permissions are denied
     */
    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permission Denied")
            .setMessage(
                "Camera permission is required to use this app. " +
                        "You can grant permission in Settings."
            )
            .setPositiveButton("Settings") { _, _ ->
                permissionManager.openAppSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Refresh UI if needed
    }
}
