package com.example.runnerapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StatisticsAdapter(private val statistics: List<StatisticItem>) : 
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (statistics[position].type == "header") TYPE_HEADER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_statistics_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_statistics, parent, false)
                StatisticViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = statistics[position]
        when (holder) {
            is HeaderViewHolder -> holder.bind(item)
            is StatisticViewHolder -> holder.bind(item)
        }
    }

    override fun getItemCount() = statistics.size

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.text_header_title)

        fun bind(item: StatisticItem) {
            titleText.text = item.title
        }
    }

    class StatisticViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.text_stat_title)
        private val valueText: TextView = itemView.findViewById(R.id.text_stat_value)

        fun bind(item: StatisticItem) {
            titleText.text = item.title
            valueText.text = item.value
        }
    }
}
