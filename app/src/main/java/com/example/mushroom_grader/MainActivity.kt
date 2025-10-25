package com.example.mushroom_grader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.mushroom_grader.databinding.ActivityMainBinding


import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.mushroom_grader.ui.theme.Mushroom_GraderTheme

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCameraActivity()
        } else {
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { imageUri ->
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra("image_uri", imageUri.toString())
                putExtra("is_poisonous", false) // TODO replace with inference result
            }
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.captureImageButton.setOnClickListener {
            checkCameraPermissionAndStart()
        }

        binding.importImageButton.setOnClickListener {
            openImagePicker()
        }

        binding.historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        binding.aboutAppButton.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun checkCameraPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCameraActivity()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCameraActivity() {
        startActivity(Intent(this, CameraActivity::class.java))
    }

    private fun openImagePicker() {
        pickImageLauncher.launch("image/*")
    }
}

/*
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Mushroom_GraderTheme {
        Greeting("Android")
    }
}

 */