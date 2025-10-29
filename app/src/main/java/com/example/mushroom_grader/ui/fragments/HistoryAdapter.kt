package com.example.mushroom_grader.ui.fragments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mushroom_grader.R
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
        val binding = ItemResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class HistoryViewHolder(
        private val binding: ItemResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(result: ClassificationResult) {
            val context = binding.root.context
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val date = dateFormat.format(Date(result.timestamp))
            val confidenceStr = String.format(Locale.getDefault(), "%.1f", result.confidence)

            binding.textClassId.text = context.getString(R.string.class_id_format, result.className)
            binding.textConfidence.text = context.getString(R.string.confidence_format, confidenceStr)
            binding.textDate.text = date
        }
    }
}
