package com.example.mushroom_grader.ui.fragments

import android.graphics.BitmapFactory
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
        if (position < items.size) holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class HistoryViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(result: ClassificationResult) {
            val context = binding.root.context

            // Date
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            binding.tvTimestamp.text = dateFormat.format(Date(result.timestamp))

            // Name
            binding.tvName.text = result.className

            // Confidence (item_history.xml already has the "Confidence:" label)
            binding.tvConfidence.text =
                String.format(Locale.getDefault(), "%.1f%%", result.confidence * 100)

            // Optional: tint confidence based on safety
            val confidenceColor = when {
                result.isPoisonous -> android.R.color.holo_red_dark
                result.category.name == "INEDIBLE" -> android.R.color.holo_orange_dark
                else -> android.R.color.holo_green_dark
            }
            binding.tvConfidence.setTextColor(ContextCompat.getColor(context, confidenceColor))

            // Badge
            when {
                result.isPoisonous -> {
                    binding.tvSafetyBadge.text = "⚠️ POISONOUS"
                    binding.tvSafetyBadge.setBackgroundResource(R.drawable.badge_poison)
                }
                result.category.name == "INEDIBLE" -> {
                    binding.tvSafetyBadge.text = "INEDIBLE"
                    binding.tvSafetyBadge.setBackgroundResource(R.drawable.badge_inedible)
                }
                else -> {
                    binding.tvSafetyBadge.text = "✓ SAFE"
                    binding.tvSafetyBadge.setBackgroundResource(R.drawable.badge_safe)
                }
            }

            // Image
            val path = result.imagePath
            if (!path.isNullOrBlank()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(path)
                    if (bitmap != null) binding.ivMushroom.setImageBitmap(bitmap)
                    else binding.ivMushroom.setImageResource(R.mipmap.ic_launcher)
                } catch (_: Exception) {
                    binding.ivMushroom.setImageResource(R.mipmap.ic_launcher)
                }
            } else {
                binding.ivMushroom.setImageResource(R.mipmap.ic_launcher)
            }

            binding.root.setOnClickListener { onItemClick(result) }
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
