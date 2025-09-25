package com.example.runnerapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.runnerapp.data.AppDatabase
import com.example.runnerapp.data.RunSession
import com.example.runnerapp.data.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.ContextCompat // <- para tint

class HistorialActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView

    private val adapter = HistoryAdapter { item ->
        when (item) {
            is HistoryItem.RunItem -> {
                val i = Intent(this, RunDetailActivity::class.java)
                i.putExtra("run_id", item.runSession.id)
                startActivity(i)
            }
            is HistoryItem.WorkoutItem -> {
                val i = Intent(this, WorkoutSessionDetailActivity::class.java)
                i.putExtra("workout_session_id", item.workoutSession.id)
                startActivity(i)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)

        // Toolbar con flecha back
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar_hist)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { finish() }

        rv = findViewById(R.id.rvRuns)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                val runSessions = db.runSessionDao().getAll()
                val workoutSessions = db.workoutSessionDao().getAll()

                val historyItems = mutableListOf<HistoryItem>()
                runSessions.forEach { runSession: RunSession ->
                    historyItems.add(HistoryItem.RunItem(runSession))
                }
                workoutSessions.forEach { workoutSession: WorkoutSession ->
                    historyItems.add(HistoryItem.WorkoutItem(workoutSession))
                }

                // Ordenar por startTime (más recientes primero)
                historyItems.sortedByDescending {
                    when (it) {
                        is HistoryItem.RunItem -> it.runSession.startTime
                        is HistoryItem.WorkoutItem -> it.workoutSession.startTime
                    }
                }
            }
            // Ojo: usamos el resultado ordenado que devuelve la corrutina
            adapter.submit(data)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

sealed class HistoryItem {
    data class RunItem(val runSession: RunSession) : HistoryItem()
    data class WorkoutItem(val workoutSession: WorkoutSession) : HistoryItem()
}

private class HistoryAdapter(
    val onClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryVH>() {

    private val items = mutableListOf<HistoryItem>()
    fun submit(list: List<HistoryItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): HistoryVH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryVH(v, onClick)
    }

    override fun onBindViewHolder(holder: HistoryVH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
}

private class HistoryVH(
    v: android.view.View,
    val onClick: (HistoryItem) -> Unit
) : RecyclerView.ViewHolder(v) {
    private val title = v.findViewById<android.widget.TextView>(R.id.tvTitle)
    private val sub = v.findViewById<android.widget.TextView>(R.id.tvSub)
    private val icon = v.findViewById<android.widget.ImageView>(R.id.ivIcon)
    private val df = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    fun bind(item: HistoryItem) {
        when (item) {
            is HistoryItem.RunItem -> {
                val run = item.runSession
                title.text = "${df.format(Date(run.startTime))} • ${run.sport}"
                val km = run.distanceMeters / 1000.0
                val h = run.durationSec / 3600
                val m = (run.durationSec % 3600) / 60
                val s = run.durationSec % 60
                sub.text = String.format(
                    Locale.getDefault(),
                    "Tiempo %02d:%02d:%02d • Dist %.2f km • Pasos %d",
                    h, m, s, km, run.steps
                )

                // Icono según deporte (sin *_selected)
                val iconRes = when (run.sport.uppercase(Locale.getDefault())) {
                    "BIKE"  -> R.drawable.ic_bike
                    "SKATE" -> R.drawable.ic_skate
                    "RUN"   -> R.drawable.ic_run
                    else    -> R.drawable.ic_run
                }
                icon.setImageResource(iconRes)

                // Tint opcional (mismo look que antes)
                icon.imageTintList = ContextCompat.getColorStateList(itemView.context, R.color.primary_blue)
            }

            is HistoryItem.WorkoutItem -> {
                val workout = item.workoutSession
                title.text = "${df.format(Date(workout.startTime))} • ${workout.routineName}"
                val exercises = workout.completedExercises.size
                val totalExercises = workout.completedExercises.sumOf { it.setsCompleted }
                sub.text = String.format(
                    Locale.getDefault(),
                    "Duración %d min • %d ejercicios • %d series • %.0f cal",
                    workout.durationMinutes, exercises, totalExercises, workout.totalCaloriesBurned
                )
                icon.setImageResource(R.drawable.ic_fitness_center)
                icon.imageTintList = ContextCompat.getColorStateList(itemView.context, R.color.primary_blue)
            }
        }

        itemView.setOnClickListener { onClick(item) }
    }
}
