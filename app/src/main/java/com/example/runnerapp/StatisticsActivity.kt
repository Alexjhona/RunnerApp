package com.example.runnerapp

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.runnerapp.data.AppDatabase
import com.example.runnerapp.data.RunSession
import com.example.runnerapp.data.Usuario
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.utils.MPPointF
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class StatisticsActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var sessionManager: SessionManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StatisticsAdapter
    
    private lateinit var weeklyProgressChart: LineChart
    private lateinit var activityDistributionChart: PieChart
    private lateinit var monthlyComparisonChart: BarChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        // Setup toolbar
        supportActionBar?.apply {
            title = "Estadísticas Avanzadas"
            setDisplayHomeAsUpEnabled(true)
        }

        database = AppDatabase.getDatabase(this)
        sessionManager = SessionManager(this)
        
        initializeCharts()
        
        recyclerView = findViewById(R.id.recycler_statistics)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        loadStatistics()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    private fun initializeCharts() {
        weeklyProgressChart = findViewById(R.id.chart_weekly_progress)
        activityDistributionChart = findViewById(R.id.chart_activity_distribution)
        monthlyComparisonChart = findViewById(R.id.chart_monthly_comparison)
        
        setupWeeklyProgressChart()
        setupActivityDistributionChart()
        setupMonthlyComparisonChart()
    }
    
    private fun setupWeeklyProgressChart() {
        weeklyProgressChart.apply {
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
                labelCount = 7
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
            }
            
            axisRight.isEnabled = false
            legend.isEnabled = true
        }
    }
    
    private fun setupActivityDistributionChart() {
        activityDistributionChart.apply {
            setUsePercentValues(true)
            description.isEnabled = false
            setExtraOffsets(5f, 10f, 5f, 5f)
            
            dragDecelerationFrictionCoef = 0.95f
            
            isDrawHoleEnabled = true
            setHoleColor(Color.WHITE)
            
            setTransparentCircleColor(Color.WHITE)
            setTransparentCircleAlpha(110)
            
            holeRadius = 58f
            transparentCircleRadius = 61f
            
            setDrawCenterText(true)
            centerText = "Distribución\nde Actividades"
            
            isRotationEnabled = true
            isHighlightPerTapEnabled = true
            
            legend.isEnabled = true
        }
    }
    
    private fun setupMonthlyComparisonChart() {
        monthlyComparisonChart.apply {
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
            }
            
            axisRight.isEnabled = false
            legend.isEnabled = true
        }
    }

    private fun loadStatistics() {
        lifecycleScope.launch {
            val userEmail = sessionManager.getUserSession()
            if (userEmail != null) {
                val user = database.usuarioDao().getUsuarioByEmail(userEmail)
                val runs = database.runSessionDao().getAll()
                
                updateChartsWithData(runs)
                
                val statistics = calculateStatistics(user, runs)
                adapter = StatisticsAdapter(statistics)
                recyclerView.adapter = adapter
            }
        }
    }
    
    private fun updateChartsWithData(runs: List<RunSession>) {
        updateWeeklyProgressChart(runs)
        updateActivityDistributionChart(runs)
        updateMonthlyComparisonChart(runs)
    }
    
    private fun updateWeeklyProgressChart(runs: List<RunSession>) {
        val calendar = Calendar.getInstance()
        val weekData = mutableMapOf<Int, Float>()
        
        // Initialize week data
        for (i in 0..6) {
            weekData[i] = 0f
        }
        
        // Calculate weekly distances
        runs.filter { 
            val runDate = Calendar.getInstance().apply { timeInMillis = it.startTime }
            val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
            runDate.after(weekAgo)
        }.forEach { run ->
            val runCalendar = Calendar.getInstance().apply { timeInMillis = run.startTime }
            val dayOfWeek = runCalendar.get(Calendar.DAY_OF_WEEK) - 1
            weekData[dayOfWeek] = weekData[dayOfWeek]!! + (run.distanceMeters.toFloat() / 1000f)
        }
        
        val entries = weekData.map { Entry(it.key.toFloat(), it.value) }
        val dataSet = LineDataSet(entries, "Distancia (km)").apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
            valueTextSize = 9f
            setDrawFilled(true)
            fillColor = Color.BLUE
            fillAlpha = 50
        }
        
        val lineData = LineData(dataSet)
        weeklyProgressChart.data = lineData
        
        val dayLabels = arrayOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
        weeklyProgressChart.xAxis.valueFormatter = IndexAxisValueFormatter(dayLabels)
        
        weeklyProgressChart.invalidate()
    }
    
    private fun updateActivityDistributionChart(runs: List<RunSession>) {
        val sportCounts = runs.groupBy { it.sport }.mapValues { it.value.size }
        
        if (sportCounts.isEmpty()) {
            activityDistributionChart.clear()
            return
        }
        
        val entries = sportCounts.map { (sport, count) ->
            val sportName = when(sport) {
                "RUN" -> "Correr"
                "BIKE" -> "Bicicleta"
                "SKATE" -> "Patinar"
                else -> sport
            }
            PieEntry(count.toFloat(), sportName)
        }
        
        val dataSet = PieDataSet(entries, "Actividades").apply {
            setDrawIcons(false)
            sliceSpace = 3f
            iconsOffset = MPPointF(0f, 40f)
            selectionShift = 5f
            colors = ColorTemplate.MATERIAL_COLORS.toList()
        }
        
        val pieData = PieData(dataSet).apply {
            setValueTextSize(11f)
            setValueTextColor(Color.WHITE)
        }
        
        activityDistributionChart.data = pieData
        activityDistributionChart.invalidate()
    }
    
    private fun updateMonthlyComparisonChart(runs: List<RunSession>) {
        val calendar = Calendar.getInstance()
        val monthlyData = mutableMapOf<String, Float>()
        
        // Get last 6 months data
        for (i in 5 downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.MONTH, -i)
            val monthKey = SimpleDateFormat("MMM", Locale.getDefault()).format(calendar.time)
            monthlyData[monthKey] = 0f
        }
        
        // Calculate monthly distances
        runs.forEach { run ->
            val runCalendar = Calendar.getInstance().apply { timeInMillis = run.startTime }
            val monthKey = SimpleDateFormat("MMM", Locale.getDefault()).format(runCalendar.time)
            if (monthlyData.containsKey(monthKey)) {
                monthlyData[monthKey] = monthlyData[monthKey]!! + (run.distanceMeters.toFloat() / 1000f)
            }
        }
        
        val entries = monthlyData.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value)
        }
        
        val dataSet = BarDataSet(entries, "Distancia mensual (km)").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextSize = 10f
        }
        
        val barData = BarData(dataSet)
        monthlyComparisonChart.data = barData
        
        val monthLabels = monthlyData.keys.toTypedArray()
        monthlyComparisonChart.xAxis.valueFormatter = IndexAxisValueFormatter(monthLabels)
        
        monthlyComparisonChart.invalidate()
    }

    private fun calculateStatistics(user: Usuario?, runs: List<RunSession>): List<StatisticItem> {
        val statistics = mutableListOf<StatisticItem>()
        
        if (runs.isEmpty()) {
            statistics.add(StatisticItem("Sin datos", "Aún no tienes actividades registradas", ""))
            return statistics
        }

        statistics.add(StatisticItem("RESUMEN VISUAL", "", "header"))
        statistics.add(StatisticItem("📊 Gráficos disponibles", "Consulta los gráficos superiores para análisis detallado", "info"))
        
        // General Statistics
        statistics.add(StatisticItem("RESUMEN GENERAL", "", "header"))
        statistics.add(StatisticItem("🏃 Total de actividades", runs.size.toString(), "count"))
        
        val totalDistance = runs.sumOf { it.distanceMeters } / 1000.0
        statistics.add(StatisticItem("🛣️ Distancia total", String.format("%.2f km", totalDistance), "distance"))
        
        val totalDuration = runs.sumOf { it.durationSec } / 3600.0
        statistics.add(StatisticItem("⏱️ Tiempo total", String.format("%.1f horas", totalDuration), "time"))
        
        val totalSteps = runs.sumOf { it.steps }
        statistics.add(StatisticItem("👣 Pasos totales", String.format("%,d", totalSteps), "steps"))
        
        val totalCalories = runs.sumOf { it.caloriesBurned }
        statistics.add(StatisticItem("🔥 Calorías quemadas", String.format("%.0f kcal", totalCalories), "calories"))

        // Averages with enhanced visual elements
        statistics.add(StatisticItem("PROMEDIOS DESTACADOS", "", "header"))
        val avgDistance = totalDistance / runs.size
        statistics.add(StatisticItem("📏 Distancia promedio", String.format("%.2f km", avgDistance), "distance"))
        
        val avgDuration = runs.map { it.durationSec }.average() / 60.0
        statistics.add(StatisticItem("⏰ Duración promedio", String.format("%.1f min", avgDuration), "time"))
        
        val avgSpeed = if (totalDuration > 0) totalDistance / totalDuration else 0.0
        statistics.add(StatisticItem("🚀 Velocidad promedio", String.format("%.2f km/h", avgSpeed), "speed"))
        
        val avgSteps = runs.map { it.steps }.average()
        statistics.add(StatisticItem("🦶 Pasos promedio", String.format("%.0f", avgSteps), "steps"))

        // Records with trophy icons
        statistics.add(StatisticItem("🏆 RÉCORDS PERSONALES", "", "header"))
        val longestRun = runs.maxByOrNull { it.distanceMeters }
        longestRun?.let {
            statistics.add(StatisticItem("🥇 Mayor distancia", String.format("%.2f km", it.distanceMeters / 1000.0), "record"))
        }
        
        val longestTime = runs.maxByOrNull { it.durationSec }
        longestTime?.let {
            statistics.add(StatisticItem("⏳ Mayor duración", formatDuration(it.durationSec), "record"))
        }
        
        val fastestRun = runs.filter { it.durationSec > 0 && it.distanceMeters > 0 }
            .maxByOrNull { (it.distanceMeters / 1000.0) / (it.durationSec / 3600.0) }
        fastestRun?.let {
            val speed = (it.distanceMeters / 1000.0) / (it.durationSec / 3600.0)
            statistics.add(StatisticItem("⚡ Velocidad máxima", String.format("%.2f km/h", speed), "record"))
        }
        
        val mostSteps = runs.maxByOrNull { it.steps }
        mostSteps?.let {
            statistics.add(StatisticItem("👟 Más pasos", String.format("%,d", it.steps), "record"))
        }

        // Activity by Sport with enhanced presentation
        val sportGroups = runs.groupBy { it.sport }
        if (sportGroups.size > 1) {
            statistics.add(StatisticItem("🎯 POR DEPORTE", "", "header"))
            sportGroups.forEach { (sport, sportRuns) ->
                val sportDistance = sportRuns.sumOf { it.distanceMeters } / 1000.0
                val sportIcon = when(sport) {
                    "RUN" -> "🏃"
                    "BIKE" -> "🚴"
                    "SKATE" -> "🛼"
                    else -> "🏃"
                }
                val sportName = when(sport) {
                    "RUN" -> "Correr"
                    "BIKE" -> "Bicicleta"
                    "SKATE" -> "Patinar"
                    else -> sport
                }
                statistics.add(StatisticItem("$sportIcon $sportName", 
                    "${sportRuns.size} actividades - ${String.format("%.2f km", sportDistance)}", "sport"))
            }
        }

        // Recent Activity with time indicators
        statistics.add(StatisticItem("📅 ACTIVIDAD RECIENTE", "", "header"))
        val last7Days = runs.filter { 
            val runDate = Date(it.startTime)
            val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.time
            runDate.after(weekAgo)
        }
        
        if (last7Days.isNotEmpty()) {
            val weekDistance = last7Days.sumOf { it.distanceMeters } / 1000.0
            statistics.add(StatisticItem("📊 Últimos 7 días", 
                "${last7Days.size} actividades - ${String.format("%.2f km", weekDistance)}", "recent"))
        }
        
        val lastRun = runs.firstOrNull()
        lastRun?.let {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val lastRunDate = dateFormat.format(Date(it.startTime))
            statistics.add(StatisticItem("🕐 Última actividad", lastRunDate, "recent"))
        }

        // User Profile Info with enhanced icons
        user?.let { u ->
            if (u.profileCompleted) {
                statistics.add(StatisticItem("👤 PERFIL PERSONAL", "", "header"))
                u.edad?.let { statistics.add(StatisticItem("🎂 Edad", "$it años", "profile")) }
                u.peso?.let { statistics.add(StatisticItem("⚖️ Peso", "${it.roundToInt()} kg", "profile")) }
                u.estatura?.let { statistics.add(StatisticItem("📏 Estatura", "${it.roundToInt()} cm", "profile")) }
                
                // Calculate BMI if height and weight are available
                if (u.peso != null && u.estatura != null && u.estatura!! > 0) {
                    val heightInMeters = u.estatura!! / 100.0
                    val bmi = u.peso!! / (heightInMeters * heightInMeters)
                    val bmiCategory = when {
                        bmi < 18.5 -> "Bajo peso"
                        bmi < 25 -> "Normal"
                        bmi < 30 -> "Sobrepeso"
                        else -> "Obesidad"
                    }
                    statistics.add(StatisticItem("📊 IMC", String.format("%.1f ($bmiCategory)", bmi), "profile"))
                }
            }
        }

        return statistics
    }

    private fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
            else -> String.format("%d:%02d", minutes, secs)
        }
    }
}

data class StatisticItem(
    val title: String,
    val value: String,
    val type: String
)
