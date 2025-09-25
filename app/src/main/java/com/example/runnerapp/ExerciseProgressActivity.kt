package com.example.runnerapp

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.runnerapp.data.AppDatabase
import com.example.runnerapp.data.WorkoutSession
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class ExerciseProgressActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var sessionManager: SessionManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ExerciseProgressAdapter
    
    private lateinit var weightProgressChart: LineChart
    private lateinit var distanceProgressChart: LineChart
    private lateinit var caloriesChart: BarChart
    private lateinit var timeRangeSpinner: Spinner
    private lateinit var exerciseFilterSpinner: Spinner
    private lateinit var summaryTextView: TextView
    
    private var allSessions: List<WorkoutSession> = emptyList()
    private var filteredSessions: List<WorkoutSession> = emptyList()
    private var availableExercises: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_progress)

        // Setup toolbar
        supportActionBar?.apply {
            title = "Progreso de Ejercicios"
            setDisplayHomeAsUpEnabled(true)
        }

        database = AppDatabase.getDatabase(this)
        sessionManager = SessionManager(this)
        
        initializeViews()
        setupSpinners()
        loadExerciseProgress()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    private fun initializeViews() {
        weightProgressChart = findViewById(R.id.chart_weight_progress)
        distanceProgressChart = findViewById(R.id.chart_distance_progress)
        caloriesChart = findViewById(R.id.chart_calories_progress)
        timeRangeSpinner = findViewById(R.id.spinner_time_range)
        exerciseFilterSpinner = findViewById(R.id.spinner_exercise_filter)
        summaryTextView = findViewById(R.id.tv_progress_summary)
        
        recyclerView = findViewById(R.id.recycler_exercise_progress)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        setupCharts()
    }
    
    private fun setupSpinners() {
        // Time range spinner
        val timeRanges = arrayOf("Última semana", "Último mes", "Últimos 3 meses", "Último año", "Todo el tiempo")
        val timeRangeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, timeRanges)
        timeRangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        timeRangeSpinner.adapter = timeRangeAdapter
        timeRangeSpinner.setSelection(1) // Default to "Último mes"
        
        timeRangeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterSessionsByTimeRange(position)
                updateChartsAndData()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Exercise filter spinner will be populated after loading data
        exerciseFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateChartsAndData()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupCharts() {
        setupWeightProgressChart()
        setupDistanceProgressChart()
        setupCaloriesChart()
    }
    
    private fun setupWeightProgressChart() {
        weightProgressChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setBackgroundColor(Color.WHITE)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "S${value.toInt() + 1}"
                    }
                }
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()}kg"
                    }
                }
            }
            
            axisRight.isEnabled = false
            legend.isEnabled = true
        }
    }
    
    private fun setupDistanceProgressChart() {
        distanceProgressChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setBackgroundColor(Color.WHITE)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "S${value.toInt() + 1}"
                    }
                }
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value}km"
                    }
                }
            }
            
            axisRight.isEnabled = false
            legend.isEnabled = true
        }
    }
    
    private fun setupCaloriesChart() {
        caloriesChart.apply {
            description.isEnabled = false
            setMaxVisibleValueCount(60)
            setPinchZoom(false)
            setDrawBarShadow(false)
            setDrawGridBackground(false)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()} cal"
                    }
                }
            }
            
            axisRight.isEnabled = false
            legend.isEnabled = true
        }
    }

    private fun loadExerciseProgress() {
        lifecycleScope.launch {
            val userEmail = sessionManager.getUserSession()
            if (userEmail != null) {
                allSessions = database.workoutSessionDao().getUserWorkoutSessions(userEmail)
                
                // Extract all unique exercises
                availableExercises = allSessions
                    .flatMap { it.completedExercises }
                    .map { it.exerciseName }
                    .distinct()
                    .sorted()
                
                setupExerciseFilterSpinner()
                filterSessionsByTimeRange(1) // Default to last month
                updateChartsAndData()
            }
        }
    }
    
    private fun setupExerciseFilterSpinner() {
        val exerciseOptions = mutableListOf("Todos los ejercicios").apply {
            addAll(availableExercises)
        }
        
        val exerciseAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, exerciseOptions)
        exerciseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        exerciseFilterSpinner.adapter = exerciseAdapter
    }
    
    private fun filterSessionsByTimeRange(timeRangeIndex: Int) {
        val calendar = Calendar.getInstance()
        val currentTime = calendar.timeInMillis
        
        val startTime = when (timeRangeIndex) {
            0 -> { // Last week
                calendar.add(Calendar.WEEK_OF_YEAR, -1)
                calendar.timeInMillis
            }
            1 -> { // Last month
                calendar.add(Calendar.MONTH, -1)
                calendar.timeInMillis
            }
            2 -> { // Last 3 months
                calendar.add(Calendar.MONTH, -3)
                calendar.timeInMillis
            }
            3 -> { // Last year
                calendar.add(Calendar.YEAR, -1)
                calendar.timeInMillis
            }
            else -> 0L // All time
        }
        
        filteredSessions = if (startTime == 0L) {
            allSessions
        } else {
            allSessions.filter { it.startTime >= startTime }
        }
    }
    
    private fun updateChartsAndData() {
        val selectedExercise = if (exerciseFilterSpinner.selectedItemPosition == 0) {
            null // All exercises
        } else {
            availableExercises[exerciseFilterSpinner.selectedItemPosition - 1]
        }
        
        val sessionsToAnalyze = if (selectedExercise != null) {
            filteredSessions.map { session ->
                session.copy(
                    completedExercises = session.completedExercises.filter { 
                        it.exerciseName == selectedExercise 
                    }
                )
            }.filter { it.completedExercises.isNotEmpty() }
        } else {
            filteredSessions
        }
        
        updateChartsWithData(sessionsToAnalyze)
        updateProgressSummary(sessionsToAnalyze)
        
        val progressData = calculateProgressData(sessionsToAnalyze)
        adapter = ExerciseProgressAdapter(progressData)
        recyclerView.adapter = adapter
    }
    
    private fun updateChartsWithData(sessions: List<WorkoutSession>) {
        updateWeightProgressChart(sessions)
        updateDistanceProgressChart(sessions)
        updateCaloriesChart(sessions)
    }
    
    private fun updateProgressSummary(sessions: List<WorkoutSession>) {
        if (sessions.isEmpty()) {
            summaryTextView.text = "No hay datos para el período seleccionado"
            return
        }
        
        val totalSessions = sessions.size
        val totalCalories = sessions.sumOf { it.totalCaloriesBurned }.roundToInt()
        val totalDuration = sessions.sumOf { it.durationMinutes }
        val avgCaloriesPerSession = if (totalSessions > 0) totalCalories / totalSessions else 0
        val avgDurationPerSession = if (totalSessions > 0) totalDuration / totalSessions else 0
        
        // Calculate improvement metrics
        val improvementText = calculateImprovementMetrics(sessions)
        
        summaryTextView.text = """
            📊 RESUMEN DEL PERÍODO
            
            🏋️ Entrenamientos: $totalSessions sesiones
            🔥 Calorías totales: $totalCalories kcal
            ⏱️ Tiempo total: ${totalDuration} minutos
            📈 Promedio por sesión: $avgCaloriesPerSession kcal, ${avgDurationPerSession} min
            
            $improvementText
        """.trimIndent()
    }
    
    private fun calculateImprovementMetrics(sessions: List<WorkoutSession>): String {
        if (sessions.size < 2) return "💡 Necesitas más entrenamientos para ver tendencias"
        
        val sortedSessions = sessions.sortedBy { it.startTime }
        val firstHalf = sortedSessions.take(sortedSessions.size / 2)
        val secondHalf = sortedSessions.drop(sortedSessions.size / 2)
        
        val firstHalfAvgCalories = firstHalf.map { it.totalCaloriesBurned }.average()
        val secondHalfAvgCalories = secondHalf.map { it.totalCaloriesBurned }.average()
        
        val calorieImprovement = ((secondHalfAvgCalories - firstHalfAvgCalories) / firstHalfAvgCalories * 100).roundToInt()
        
        val firstHalfAvgDuration = firstHalf.map { it.durationMinutes }.average()
        val secondHalfAvgDuration = secondHalf.map { it.durationMinutes }.average()
        
        val durationImprovement = ((secondHalfAvgDuration - firstHalfAvgDuration) / firstHalfAvgDuration * 100).roundToInt()
        
        return buildString {
            append("📈 TENDENCIAS:\n")
            
            when {
                calorieImprovement > 5 -> append("🔥 Quema de calorías: +${calorieImprovement}% ¡Excelente!\n")
                calorieImprovement < -5 -> append("🔥 Quema de calorías: ${calorieImprovement}% - Puedes mejorar\n")
                else -> append("🔥 Quema de calorías: Estable\n")
            }
            
            when {
                durationImprovement > 5 -> append("⏱️ Duración: +${durationImprovement}% ¡Más resistencia!\n")
                durationImprovement < -5 -> append("⏱️ Duración: ${durationImprovement}% - Intenta entrenar más tiempo\n")
                else -> append("⏱️ Duración: Estable\n")
            }
            
            // Weight progression analysis
            val weightProgression = analyzeWeightProgression(sessions)
            if (weightProgression.isNotEmpty()) {
                append(weightProgression)
            }
        }
    }
    
    private fun analyzeWeightProgression(sessions: List<WorkoutSession>): String {
        val exerciseWeights = mutableMapOf<String, MutableList<Pair<Long, Float>>>()
        
        sessions.forEach { session ->
            session.completedExercises.forEach { exercise ->
                if (exercise.weightUsed > 0) {
                    exerciseWeights.getOrPut(exercise.exerciseName) { mutableListOf() }
                        .add(Pair(session.startTime, exercise.weightUsed))
                }
            }
        }
        
        val improvements = mutableListOf<String>()
        
        exerciseWeights.forEach { (exerciseName, weights) ->
            if (weights.size >= 2) {
                val sortedWeights = weights.sortedBy { it.first }
                val firstWeight = sortedWeights.first().second
                val lastWeight = sortedWeights.last().second
                val improvement = ((lastWeight - firstWeight) / firstWeight * 100).roundToInt()
                
                if (improvement > 10) {
                    improvements.add("💪 $exerciseName: +${improvement}% peso")
                }
            }
        }
        
        return if (improvements.isNotEmpty()) {
            improvements.joinToString("\n") + "\n"
        } else ""
    }
    
    private fun updateWeightProgressChart(sessions: List<WorkoutSession>) {
        val weightData = mutableMapOf<String, MutableList<Pair<Int, Float>>>()
        
        sessions.sortedBy { it.startTime }.forEachIndexed { sessionIndex, session ->
            session.completedExercises.forEach { exercise ->
                if (exercise.weightUsed > 0) {
                    val exerciseName = exercise.exerciseName
                    weightData.getOrPut(exerciseName) { mutableListOf() }
                        .add(Pair(sessionIndex, exercise.weightUsed))
                }
            }
        }
        
        if (weightData.isEmpty()) {
            weightProgressChart.clear()
            weightProgressChart.setNoDataText("No hay datos de peso para mostrar")
            return
        }
        
        val dataSets = mutableListOf<ILineDataSet>()
        val colors = listOf(
            Color.parseColor("#2196F3"), // Blue
            Color.parseColor("#F44336"), // Red
            Color.parseColor("#4CAF50"), // Green
            Color.parseColor("#FF9800"), // Orange
            Color.parseColor("#9C27B0"), // Purple
            Color.parseColor("#00BCD4")  // Cyan
        )
        var colorIndex = 0
        
        weightData.forEach { (exerciseName, weights) ->
            // Group by session and take max weight per session
            val sessionWeights = weights.groupBy { it.first }
                .mapValues { entry -> entry.value.maxOf { it.second } }
                .toSortedMap()
            
            val entries = sessionWeights.map { (sessionIndex, maxWeight) ->
                Entry(sessionIndex.toFloat(), maxWeight)
            }
            
            if (entries.isNotEmpty()) {
                val dataSet = LineDataSet(entries, exerciseName).apply {
                    color = colors[colorIndex % colors.size]
                    setCircleColor(colors[colorIndex % colors.size])
                    lineWidth = 3f
                    circleRadius = 5f
                    setDrawCircleHole(false)
                    valueTextSize = 10f
                    setDrawValues(true)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()}kg"
                        }
                    }
                }
                
                dataSets.add(dataSet as ILineDataSet)
                colorIndex++
            }
        }
        
        if (dataSets.isNotEmpty()) {
            val lineData = LineData(dataSets)
            weightProgressChart.data = lineData
            weightProgressChart.invalidate()
        } else {
            weightProgressChart.clear()
            weightProgressChart.setNoDataText("No hay datos de peso para mostrar")
        }
    }
    
    private fun updateDistanceProgressChart(sessions: List<WorkoutSession>) {
        val distanceData = mutableMapOf<String, MutableList<Pair<Int, Float>>>()
        
        sessions.sortedBy { it.startTime }.forEachIndexed { sessionIndex, session ->
            session.completedExercises.forEach { exercise ->
                if (exercise.distanceCompleted > 0) {
                    val exerciseName = exercise.exerciseName
                    distanceData.getOrPut(exerciseName) { mutableListOf() }
                        .add(Pair(sessionIndex, exercise.distanceCompleted))
                }
            }
        }
        
        if (distanceData.isEmpty()) {
            distanceProgressChart.clear()
            distanceProgressChart.setNoDataText("No hay datos de distancia para mostrar")
            return
        }
        
        val dataSets = mutableListOf<ILineDataSet>()
        val colors = listOf(
            Color.parseColor("#4CAF50"), // Green
            Color.parseColor("#2196F3"), // Blue
            Color.parseColor("#F44336"), // Red
            Color.parseColor("#FF9800"), // Orange
            Color.parseColor("#9C27B0"), // Purple
            Color.parseColor("#00BCD4")  // Cyan
        )
        var colorIndex = 0
        
        distanceData.forEach { (exerciseName, distances) ->
            // Group by session and sum distances per session
            val sessionDistances = distances.groupBy { it.first }
                .mapValues { entry -> entry.value.sumOf { it.second.toDouble() }.toFloat() }
                .toSortedMap()
            
            val entries = sessionDistances.map { (sessionIndex, totalDistance) ->
                Entry(sessionIndex.toFloat(), totalDistance)
            }
            
            if (entries.isNotEmpty()) {
                val dataSet = LineDataSet(entries, exerciseName).apply {
                    color = colors[colorIndex % colors.size]
                    setCircleColor(colors[colorIndex % colors.size])
                    lineWidth = 3f
                    circleRadius = 5f
                    setDrawCircleHole(false)
                    valueTextSize = 10f
                    setDrawValues(true)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${String.format("%.1f", value)}km"
                        }
                    }
                }
                
                dataSets.add(dataSet as ILineDataSet)
                colorIndex++
            }
        }
        
        if (dataSets.isNotEmpty()) {
            val lineData = LineData(dataSets)
            distanceProgressChart.data = lineData
            distanceProgressChart.invalidate()
        } else {
            distanceProgressChart.clear()
            distanceProgressChart.setNoDataText("No hay datos de distancia para mostrar")
        }
    }
    
    private fun updateCaloriesChart(sessions: List<WorkoutSession>) {
        if (sessions.isEmpty()) {
            caloriesChart.clear()
            caloriesChart.setNoDataText("No hay datos de calorías para mostrar")
            return
        }
        
        // Group sessions by week
        val weeklyCalories = mutableMapOf<String, Float>()
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        
        sessions.forEach { session ->
            val calendar = Calendar.getInstance().apply { timeInMillis = session.startTime }
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            val weekKey = dateFormat.format(calendar.time)
            
            weeklyCalories[weekKey] = (weeklyCalories[weekKey] ?: 0f) + session.totalCaloriesBurned.toFloat()
        }
        
        val sortedWeeks = weeklyCalories.toSortedMap()
        val entries = sortedWeeks.values.mapIndexed { index, calories ->
            BarEntry(index.toFloat(), calories)
        }
        
        val dataSet = BarDataSet(entries, "Calorías semanales").apply {
            color = Color.parseColor("#FF9800")
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "${value.toInt()}"
                }
            }
        }
        
        val barData = BarData(dataSet)
        caloriesChart.data = barData
        
        val weekLabels = sortedWeeks.keys.toTypedArray()
        caloriesChart.xAxis.valueFormatter = IndexAxisValueFormatter(weekLabels)
        
        caloriesChart.invalidate()
    }

    private fun calculateProgressData(sessions: List<WorkoutSession>): List<ExerciseProgressItem> {
        val progressData = mutableListOf<ExerciseProgressItem>()
        
        if (sessions.isEmpty()) {
            progressData.add(ExerciseProgressItem("Sin datos", "Aún no tienes entrenamientos registrados para este período", "", "empty"))
            return progressData
        }

        // Exercise-specific progress with detailed analysis
        val exerciseStats = mutableMapOf<String, DetailedExerciseStats>()
        
        sessions.forEach { session ->
            session.completedExercises.forEach { exercise ->
                val stats = exerciseStats.getOrPut(exercise.exerciseName) {
                    DetailedExerciseStats(
                        exerciseName = exercise.exerciseName,
                        totalSessions = 0,
                        maxWeight = 0f,
                        maxDistance = 0f,
                        totalCalories = 0.0,
                        totalReps = 0,
                        totalTime = 0,
                        weights = mutableListOf(),
                        distances = mutableListOf(),
                        sessionDates = mutableListOf()
                    )
                }
                
                stats.totalSessions++
                if (exercise.weightUsed > stats.maxWeight) stats.maxWeight = exercise.weightUsed
                if (exercise.distanceCompleted > stats.maxDistance) stats.maxDistance = exercise.distanceCompleted
                stats.totalCalories += exercise.caloriesBurned
                stats.totalReps += exercise.repsCompleted
                stats.totalTime += exercise.timeCompleted
                
                if (exercise.weightUsed > 0) stats.weights.add(exercise.weightUsed)
                if (exercise.distanceCompleted > 0) stats.distances.add(exercise.distanceCompleted)
                stats.sessionDates.add(session.startTime)
            }
        }

        if (exerciseStats.isNotEmpty()) {
            progressData.add(ExerciseProgressItem("🎯 ANÁLISIS DETALLADO POR EJERCICIO", "", "", "header"))
            
            exerciseStats.values.sortedByDescending { it.totalSessions }.forEach { stats ->
                val analysis = analyzeExerciseProgress(stats)
                
                progressData.add(ExerciseProgressItem(
                    "💪 ${stats.exerciseName}",
                    analysis.summary,
                    analysis.trend,
                    "exercise_detailed"
                ))
            }
        }

        // Performance goals and recommendations
        progressData.add(ExerciseProgressItem("🎯 RECOMENDACIONES", "", "", "header"))
        
        val recommendations = generateRecommendations(sessions, exerciseStats.values.toList())
        recommendations.forEach { recommendation ->
            progressData.add(ExerciseProgressItem(
                recommendation.title,
                recommendation.description,
                recommendation.action,
                "recommendation"
            ))
        }

        return progressData
    }
    
    private fun analyzeExerciseProgress(stats: DetailedExerciseStats): ExerciseAnalysis {
        val summary = buildString {
            append("${stats.totalSessions} sesiones")
            if (stats.maxWeight > 0) append(" • Peso máx: ${stats.maxWeight}kg")
            if (stats.maxDistance > 0) append(" • Distancia máx: ${String.format("%.1f", stats.maxDistance)}km")
            if (stats.totalReps > 0) append(" • Total reps: ${stats.totalReps}")
            if (stats.totalTime > 0) append(" • Tiempo total: ${stats.totalTime / 60}min")
        }
        
        val trend = when {
            stats.weights.size >= 2 -> {
                val improvement = ((stats.weights.last() - stats.weights.first()) / stats.weights.first() * 100).roundToInt()
                when {
                    improvement > 10 -> "📈 +${improvement}%"
                    improvement < -10 -> "📉 ${improvement}%"
                    else -> "➡️ Estable"
                }
            }
            stats.distances.size >= 2 -> {
                val improvement = ((stats.distances.last() - stats.distances.first()) / stats.distances.first() * 100).roundToInt()
                when {
                    improvement > 10 -> "📈 +${improvement}%"
                    improvement < -10 -> "📉 ${improvement}%"
                    else -> "➡️ Estable"
                }
            }
            else -> "📊 Datos insuficientes"
        }
        
        return ExerciseAnalysis(summary, trend)
    }
    
    private fun generateRecommendations(sessions: List<WorkoutSession>, exerciseStats: List<DetailedExerciseStats>): List<Recommendation> {
        val recommendations = mutableListOf<Recommendation>()
        
        // Frequency recommendation
        val avgSessionsPerWeek = sessions.size.toDouble() / 4 // Assuming last month data
        when {
            avgSessionsPerWeek < 2 -> recommendations.add(
                Recommendation(
                    "🗓️ Aumenta la frecuencia",
                    "Entrenas ${String.format("%.1f", avgSessionsPerWeek)} veces por semana. Intenta llegar a 3-4 sesiones.",
                    "Planifica más entrenamientos"
                )
            )
            avgSessionsPerWeek > 6 -> recommendations.add(
                Recommendation(
                    "⚠️ Considera el descanso",
                    "Entrenas ${String.format("%.1f", avgSessionsPerWeek)} veces por semana. El descanso es importante.",
                    "Incluye días de recuperación"
                )
            )
        }
        
        // Progressive overload recommendation
        val stagnantExercises = exerciseStats.filter { stats ->
            stats.weights.size >= 3 && 
            stats.weights.takeLast(3).let { lastThree ->
                lastThree.max() == lastThree.min()
            }
        }
        
        if (stagnantExercises.isNotEmpty()) {
            recommendations.add(
                Recommendation(
                    "📈 Progresión de carga",
                    "Algunos ejercicios no han progresado en peso. Considera aumentar gradualmente.",
                    "Aumenta 2.5-5kg próxima sesión"
                )
            )
        }
        
        // Variety recommendation
        val uniqueExercises = exerciseStats.size
        if (uniqueExercises < 5) {
            recommendations.add(
                Recommendation(
                    "🎯 Añade variedad",
                    "Solo realizas $uniqueExercises ejercicios diferentes. La variedad mejora el desarrollo.",
                    "Prueba nuevos ejercicios"
                )
            )
        }
        
        // Consistency recommendation
        val sessionDates = sessions.map { it.startTime }.sorted()
        if (sessionDates.size >= 2) {
            val gaps = sessionDates.zipWithNext { a, b -> (b - a) / (24 * 60 * 60 * 1000) }
            val avgGap = gaps.average()
            if (avgGap > 7) {
                recommendations.add(
                    Recommendation(
                        "⏰ Mejora la consistencia",
                        "Hay gaps largos entre entrenamientos (${avgGap.roundToInt()} días promedio).",
                        "Mantén rutina regular"
                    )
                )
            }
        }
        
        return recommendations
    }
}

data class ExerciseProgressItem(
    val title: String,
    val description: String,
    val value: String,
    val type: String
)

data class DetailedExerciseStats(
    val exerciseName: String,
    var totalSessions: Int,
    var maxWeight: Float,
    var maxDistance: Float,
    var totalCalories: Double,
    var totalReps: Int,
    var totalTime: Int,
    val weights: MutableList<Float>,
    val distances: MutableList<Float>,
    val sessionDates: MutableList<Long>
)

data class ExerciseAnalysis(
    val summary: String,
    val trend: String
)

data class Recommendation(
    val title: String,
    val description: String,
    val action: String
)
