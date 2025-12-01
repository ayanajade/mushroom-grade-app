package com.example.mushroom_grader

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.mushroom_grader.databinding.ItemStorageMethodBinding
import com.example.mushroom_grader.ml.ShelfLifeData

class StorageMethodAdapter(
    private var items: List<ShelfLifeData>,
    private val onItemClick: (ShelfLifeData) -> Unit
) : RecyclerView.Adapter<StorageMethodAdapter.ViewHolder>() {

    private var selectedPosition = 0

    fun updateSelection(position: Int) {
        val previousPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(previousPosition)
        notifyItemChanged(selectedPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStorageMethodBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position == selectedPosition)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemStorageMethodBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(data: ShelfLifeData, isSelected: Boolean) {
            binding.textMethodName.text = data.storageMethod.displayName
            binding.textMethodDescription.text = data.storageMethod.description
            binding.textMethodDays.text = data.calculatedDays.toString()

            // Highlight selected item
            if (isSelected) {
                binding.cardStorageMethod.strokeWidth = 4
                binding.cardStorageMethod.strokeColor =
                    ContextCompat.getColor(binding.root.context, R.color.primary)
                binding.textMethodDays.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.primary)
                )
            } else {
                binding.cardStorageMethod.strokeWidth = 0
                binding.textMethodDays.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.text_secondary)
                )
            }

            // Color code by shelf life
            val color = when {
                data.calculatedDays >= 10 -> R.color.green_600
                data.calculatedDays >= 5 -> R.color.orange_500
                else -> R.color.red_500
            }
            binding.textMethodDays.setTextColor(
                ContextCompat.getColor(binding.root.context, color)
            )

            binding.root.setOnClickListener {
                onItemClick(data)
            }
        }
    }
}
