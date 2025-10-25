package com.example.mushroom_grader.ui.fragments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mushroom_grader.databinding.ItemResultBinding
import com.example.mushroom_grader.ml.ClassificationResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {
    private val items = mutableListOf<ClassificationResult>()

    fun submitList(results: List<ClassificationResult>) {
        items.clear()
        items.addAll(results)
        notifyDataSetChanged()
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class HistoryViewHolder(private val binding: ItemResultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(result: ClassificationResult) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val date = dateFormat.format(Date(result.timestamp))

            binding.textClassId.text = "Class ID: ${result.classId}"
            binding.textConfidence.text = "Confidence: ${result.getConfidencePercentage()}%"
            binding.textDate.text = date
        }
    }
}
