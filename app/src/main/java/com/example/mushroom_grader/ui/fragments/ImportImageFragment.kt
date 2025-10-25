package com.example.mushroom_grader.ui.fragments

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.mushroom_grader.R
import com.example.mushroom_grader.databinding.FragmentImportImageBinding
import com.example.mushroom_grader.database.AppDatabase
import com.example.mushroom_grader.ml.ClassificationResult
import com.example.mushroom_grader.ml.ImageProcessor
import com.example.mushroom_grader.ml.MLModelHelper
import kotlinx.coroutines.launch
import java.io.IOException

class ImportImageFragment : Fragment() {
    private var _binding: FragmentImportImageBinding? = null
    private val binding get() = _binding!!

    private lateinit var mlHelper: MLModelHelper
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var database: AppDatabase
    private val PICK_IMAGE = 100

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImportImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mlHelper = MLModelHelper(requireContext())
        imageProcessor = ImageProcessor()
        database = AppDatabase.getDatabase(requireContext())

        binding.btnSelectImage.setOnClickListener {
            openGallery()
        }

        binding.btnClassify.setOnClickListener {
            classifyImage()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && data != null) {
            val imageUri: Uri? = data.data
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, imageUri)
                binding.imageView.setImageBitmap(bitmap)
                binding.imageView.tag = bitmap
                binding.btnClassify.isEnabled = true
            } catch (e: IOException) {
                Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun classifyImage() {
        val bitmap = binding.imageView.tag as? Bitmap
        if (bitmap == null) {
            Toast.makeText(requireContext(), "Please select an image first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val inputArray = imageProcessor.preprocessImage(bitmap)
                val output = mlHelper.classifyImage(inputArray)
                val result = imageProcessor.postprocessOutput(output)

                database.resultDao().insertResult(result)

                binding.progressBar.visibility = View.GONE
                binding.resultText.visibility = View.VISIBLE
                binding.resultText.text = "Classification Result:\n" +
                        "Class ID: ${result.classId}\n" +
                        "Confidence: ${result.getConfidencePercentage()}%\n" +
                        "Saved to History"

                Toast.makeText(requireContext(), "Classification saved!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        mlHelper.close()
    }
}