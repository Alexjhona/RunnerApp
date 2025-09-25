package com.example.runnerapp

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class ExerciseProgressAdapter(
    private val progressItems: List<ExerciseProgressItem>
) : RecyclerView.Adapter<ExerciseProgressAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.tv_progress_title)
        val descriptionText: TextView = view.findViewById(R.id.tv_progress_description)
        val valueText: TextView = view.findViewById(R.id.tv_progress_value)
        val cardView: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise_progress, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = progressItems[position]
        val context = holder.itemView.context
        
        holder.titleText.text = item.title
        holder.descriptionText.text = item.description
        holder.valueText.text = item.value
        
        // Style based on type
        when (item.type) {
            "header" -> {
                holder.titleText.textSize = 18f
                holder.titleText.setTypeface(null, Typeface.BOLD)
                holder.titleText.setTextColor(ContextCompat.getColor(context, R.color.primary_blue))
                holder.descriptionText.visibility = View.GONE
                holder.valueText.visibility = View.GONE
                holder.cardView.setBackgroundColor(ContextCompat.getColor(context, R.color.background_light))
            }
            "exercise_detailed" -> {
                holder.titleText.textSize = 16f
                holder.titleText.setTypeface(null, Typeface.BOLD)
                holder.titleText.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                holder.descriptionText.visibility = View.VISIBLE
                holder.descriptionText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                holder.valueText.visibility = View.VISIBLE
                holder.valueText.setTextColor(ContextCompat.getColor(context, R.color.accent_orange))
                holder.valueText.setTypeface(null, Typeface.BOLD)
            }
            "recommendation" -> {
                holder.titleText.textSize = 15f
                holder.titleText.setTypeface(null, Typeface.BOLD)
                holder.titleText.setTextColor(ContextCompat.getColor(context, R.color.primary_green))
                holder.descriptionText.visibility = View.VISIBLE
                holder.descriptionText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                holder.valueText.visibility = View.VISIBLE
                holder.valueText.setTextColor(ContextCompat.getColor(context, R.color.primary_green))
                holder.valueText.setTypeface(null, Typeface.BOLD)
                holder.valueText.textSize = 12f
            }
            "empty" -> {
                holder.titleText.textSize = 16f
                holder.titleText.setTypeface(null, Typeface.NORMAL)
                holder.titleText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                holder.descriptionText.visibility = View.VISIBLE
                holder.descriptionText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                holder.valueText.visibility = View.GONE
            }
            else -> {
                holder.titleText.textSize = 14f
                holder.titleText.setTypeface(null, Typeface.NORMAL)
                holder.titleText.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                holder.descriptionText.visibility = View.VISIBLE
                holder.descriptionText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                holder.valueText.visibility = View.VISIBLE
                holder.valueText.setTextColor(ContextCompat.getColor(context, R.color.accent_orange))
            }
        }
    }

    override fun getItemCount() = progressItems.size
}
