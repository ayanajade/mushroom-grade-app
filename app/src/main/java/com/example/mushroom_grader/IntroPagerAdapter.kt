package com.example.mushroom_grader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView

data class IntroPage(
    @DrawableRes val iconRes: Int,
    val title: String,
    val description: String
)

class IntroPagerAdapter(
    private val items: List<IntroPage>
) : RecyclerView.Adapter<IntroPagerAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.imgIcon)
        val title: TextView = itemView.findViewById(R.id.tvTitle)
        val desc: TextView = itemView.findViewById(R.id.tvDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_intro_page, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.icon.setImageResource(item.iconRes)
        holder.title.text = item.title
        holder.desc.text = item.description
    }

    override fun getItemCount(): Int = items.size
}
