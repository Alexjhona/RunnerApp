package com.example.runnerapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GoalsAdapter(
    private val goals: List<Goal>,
    private val onProgressUpdate: (Goal) -> Unit
) : RecyclerView.Adapter<GoalsAdapter.GoalViewHolder>() {

    class GoalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tv_goal_title)
        val tvType: TextView = itemView.findViewById(R.id.tv_goal_type)
        val tvProgress: TextView = itemView.findViewById(R.id.tv_goal_progress)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progress_goal)
        val btnUpdate: Button = itemView.findViewById(R.id.btn_update_progress)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_goal_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GoalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_goal, parent, false)
        return GoalViewHolder(view)
    }

    override fun onBindViewHolder(holder: GoalViewHolder, position: Int) {
        val goal = goals[position]
        
        holder.tvTitle.text = goal.title
        holder.tvType.text = "📋 ${goal.type}"
        holder.tvProgress.text = "${goal.currentValue} / ${goal.targetValue}"
        
        val progress = ((goal.currentValue.toFloat() / goal.targetValue.toFloat()) * 100).toInt()
        holder.progressBar.progress = progress
        
        if (goal.isCompleted) {
            holder.tvStatus.text = "🏆 ¡COMPLETADA!"
            holder.tvStatus.setTextColor(holder.itemView.context.getColor(R.color.accent_success))
            holder.btnUpdate.isEnabled = false
            holder.btnUpdate.text = "✅ Completada"
        } else {
            holder.tvStatus.text = "🏃‍♂️ En progreso"
            holder.tvStatus.setTextColor(holder.itemView.context.getColor(R.color.primary_color))
            holder.btnUpdate.isEnabled = true
            holder.btnUpdate.text = "📈 Actualizar"
        }
        
        holder.btnUpdate.setOnClickListener {
            onProgressUpdate(goal)
        }
    }

    override fun getItemCount() = goals.size
}
