package com.example.mushroom_grader.ui.fragments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.mushroom_grader.R
import com.example.mushroom_grader.databinding.ItemHistoryBinding
import com.example.mushroom_grader.ml.ClassificationResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onItemClick: (ClassificationResult) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val items = mutableListOf<ClassificationResult>()

    fun submitList(results: List<ClassificationResult>) {
        val diffCallback = HistoryDiffCallback(items, results)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        items.clear()
        items.addAll(results)
        diffResult.dispatchUpdatesTo(this)
    }

    fun clear() {
        val size = items.size
        items.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun removeItem(position: Int) {
        if (position in 0 until items.size) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        if (position < items.size) {
            holder.bind(items[position])
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HistoryViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(result: ClassificationResult) {
            val context = binding.root.context

            // Format date
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val date = dateFormat.format(Date(result.timestamp))

            // Format confidence
            val confidenceStr = String.format(Locale.getDefault(), "Confidence: %.1f%%", result.confidence * 100)

            // ✅ CORRECT IDs from YOUR item_history.xml
            binding.tvName.text = result.className
            binding.tvConfidence.text = confidenceStr
            binding.tvTimestamp.text = date

            // Set safety badge based on poisonous status
            if (result.isPoisonous) {
                binding.tvSafetyBadge.text = "⚠️ POISONOUS"
                binding.tvSafetyBadge.setBackgroundColor(
                    ContextCompat.getColor(context, android.R.color.holo_red_dark)
                )
            } else {
                binding.tvSafetyBadge.text = "✓ SAFE"
                binding.tvSafetyBadge.setBackgroundColor(
                    ContextCompat.getColor(context, android.R.color.holo_green_dark)
                )
            }

            // Load image if path exists
            result.imagePath?.let { path ->
                try {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(path)
                    binding.ivMushroom.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    binding.ivMushroom.setImageResource(R.mipmap.ic_launcher)
                }
            } ?: run {
                binding.ivMushroom.setImageResource(R.mipmap.ic_launcher)
            }

            binding.root.setOnClickListener {
                onItemClick(result)
            }
        }
    }

    private class HistoryDiffCallback(
        private val oldList: List<ClassificationResult>,
        private val newList: List<ClassificationResult>
    ) : DiffUtil.Callback() {

        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
