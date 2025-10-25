package com.example.mushroom_grader.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mushroom_grader.databinding.FragmentHistoryBinding
import com.example.mushroom_grader.database.AppDatabase
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: AppDatabase
    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = AppDatabase.getDatabase(requireContext())
        adapter = HistoryAdapter()

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        loadHistory()

        binding.btnClearHistory.setOnClickListener {
            lifecycleScope.launch {
                database.resultDao().deleteAll()
                adapter.clear()
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val results = database.resultDao().getAllResults()
            adapter.submitList(results)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

