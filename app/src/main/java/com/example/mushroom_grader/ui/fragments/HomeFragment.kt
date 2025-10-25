package com.example.mushroom_grader.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.mushroom_grader.R
import com.example.mushroom_grader.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCaptureImage.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_captureImageFragment)
        }

        binding.btnImportImage.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_importImageFragment)
        }

        binding.btnHistory.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_historyFragment)
        }

        binding.btnAboutApp.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_aboutFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}