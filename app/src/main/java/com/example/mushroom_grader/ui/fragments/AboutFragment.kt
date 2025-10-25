package com.example.mushroom_grader.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.mushroom_grader.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {
    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val aboutText = """
            Mushroom Grader
            
            Version: 1.0
            
            Purpose:
            This app uses YOLOv8 machine learning model to classify and grade mushrooms from images.
            
            Features:
            • Capture images using your phone camera
            • Import images from your gallery
            • View classification results with confidence scores
            • Save and view classification history
            • Offline classification (no internet required)
            
            How to Use:
            1. Tap "Capture Image" to take a photo or "Import Image" to select from gallery
            2. The app will automatically classify the mushroom
            3. View results and historical data in History
            
            Technology:
            • TensorFlow Lite for model inference
            • Android Camera API
            • Room Database for local storage
        """.trimIndent()

        binding.textAbout.text = aboutText
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}