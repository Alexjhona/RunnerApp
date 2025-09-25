package com.example.runnerapp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.runnerapp.data.AppDatabase
import com.example.runnerapp.data.WorkoutSession
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class WorkoutSessionDetailActivity : AppCompatActivity() {

    private lateinit var tvRoutineName: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvExercises: TextView
    private lateinit var tvCalories: TextView
    private lateinit var tvSets: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_session_detail)

        // Setup toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { finish() }

        // Initialize views
        tvRoutineName = findViewById(R.id.tvRoutineName)
        tvDate = findViewById(R.id.tvDate)
        tvDuration = findViewById(R.id.tvDuration)
        tvExercises = findViewById(R.id.tvExercises)
        tvCalories = findViewById(R.id.tvCalories)
        tvSets = findViewById(R.id.tvSets)

        val workoutSessionId = intent.getIntExtra("workout_session_id", -1)
        if (workoutSessionId != -1) {
            loadWorkoutSessionDetails(workoutSessionId)
        }
    }

    private fun loadWorkoutSessionDetails(sessionId: Int) {
        lifecycleScope.launch {
            val workoutSession = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(this@WorkoutSessionDetailActivity)
                    .workoutSessionDao()
                    .getSessionById(sessionId)
            }

            workoutSession?.let { session ->
                displayWorkoutSession(session)
            }
        }
    }

    private fun displayWorkoutSession(session: WorkoutSession) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        
        tvRoutineName.text = session.routineName
        tvDate.text = dateFormat.format(Date(session.startTime))
        tvDuration.text = "${session.durationMinutes} minutos"
        tvExercises.text = "${session.completedExercises.size} ejercicios"
        tvCalories.text = "${session.totalCaloriesBurned.toInt()} calorías"
        
        val totalSets = session.completedExercises.sumOf { it.setsCompleted }
        tvSets.text = "$totalSets series completadas"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
