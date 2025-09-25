package com.example.runnerapp

import android.os.Bundle
import android.os.CountDownTimer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.runnerapp.data.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class WorkoutSessionActivity : ComponentActivity() {
    
    private lateinit var database: AppDatabase
    private lateinit var sessionManager: SessionManager
    private var routineId: Int = 0
    private var routineName: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        database = AppDatabase.getDatabase(this)
        sessionManager = SessionManager(this)
        
        routineId = intent.getIntExtra("routine_id", 0)
        routineName = intent.getStringExtra("routine_name") ?: "Rutina"
        
        setContent {
            WorkoutSessionScreen()
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun WorkoutSessionScreen() {
        var routine by remember { mutableStateOf<WorkoutRoutine?>(null) }
        var currentExerciseIndex by remember { mutableStateOf(0) }
        var isWorkoutStarted by remember { mutableStateOf(false) }
        var isWorkoutCompleted by remember { mutableStateOf(false) }
        var sessionStartTime by remember { mutableStateOf(0L) }
        var completedExercises by remember { mutableStateOf(listOf<CompletedExercise>()) }
        var timerSeconds by remember { mutableStateOf(0) }
        var isResting by remember { mutableStateOf(false) }
        
        LaunchedEffect(routineId) {
            routine = database.workoutRoutineDao().getRoutineById(routineId)
        }
        
        routine?.let { workoutRoutine ->
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { 
                            Text(
                                workoutRoutine.name,
                                fontWeight = FontWeight.Bold
                            ) 
                        },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                ) {
                    if (!isWorkoutStarted) {
                        WorkoutPreview(
                            routine = workoutRoutine,
                            onStartWorkout = {
                                isWorkoutStarted = true
                                sessionStartTime = System.currentTimeMillis()
                            }
                        )
                    } else if (isWorkoutCompleted) {
                        WorkoutSummary(
                            routine = workoutRoutine,
                            completedExercises = completedExercises,
                            sessionDuration = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                            onFinish = { finish() }
                        )
                    } else {
                        ActiveWorkout(
                            routine = workoutRoutine,
                            currentExerciseIndex = currentExerciseIndex,
                            timerSeconds = timerSeconds,
                            isResting = isResting,
                            onExerciseCompleted = { completedExercise ->
                                completedExercises = completedExercises + completedExercise
                                if (currentExerciseIndex < workoutRoutine.exercises.size - 1) {
                                    currentExerciseIndex++
                                    isResting = true
                                    timerSeconds = workoutRoutine.exercises[currentExerciseIndex - 1].restSeconds
                                } else {
                                    isWorkoutCompleted = true
                                    saveWorkoutSession(workoutRoutine, completedExercises, sessionStartTime)
                                }
                            },
                            onTimerUpdate = { seconds -> timerSeconds = seconds },
                            onRestCompleted = { isResting = false }
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    fun WorkoutPreview(routine: WorkoutRoutine, onStartWorkout: () -> Unit) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(20.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.FitnessCenter,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "¿Listo para entrenar?",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "${routine.exercises.size} ejercicios • ${routine.durationMinutes} min",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            
            item {
                Text(
                    "Ejercicios de la rutina:",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            itemsIndexed(routine.exercises) { index, exercise ->
                ExercisePreviewCard(exercise = exercise, index = index + 1)
            }
            
            item {
                Button(
                    onClick = onStartWorkout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Comenzar Entrenamiento", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    
    @Composable
    fun ExercisePreviewCard(exercise: Exercise, index: Int) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        index.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        exercise.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    val details = buildString {
                        if (exercise.sets > 0) append("${exercise.sets} series")
                        if (exercise.reps > 0) {
                            if (isNotEmpty()) append(" • ")
                            append("${exercise.reps} reps")
                        }
                        if (exercise.durationSeconds > 0) {
                            if (isNotEmpty()) append(" • ")
                            append("${exercise.durationSeconds}s")
                        }
                        if (exercise.weight > 0) {
                            if (isNotEmpty()) append(" • ")
                            append("${exercise.weight}kg")
                        }
                    }
                    
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
    
    @Composable
    fun ActiveWorkout(
        routine: WorkoutRoutine,
        currentExerciseIndex: Int,
        timerSeconds: Int,
        isResting: Boolean,
        onExerciseCompleted: (CompletedExercise) -> Unit,
        onTimerUpdate: (Int) -> Unit,
        onRestCompleted: () -> Unit
    ) {
        val currentExercise = routine.exercises[currentExerciseIndex]
        var currentSet by remember { mutableStateOf(1) }
        var repsCompleted by remember { mutableStateOf(0) }
        var weightUsed by remember { mutableStateOf(currentExercise.weight.toString()) }
        var distanceCompleted by remember { mutableStateOf(currentExercise.distance.toString()) }
        var timeCompleted by remember { mutableStateOf(0) }
        
        // Timer effect
        LaunchedEffect(timerSeconds, isResting) {
            if (timerSeconds > 0) {
                kotlinx.coroutines.delay(1000)
                onTimerUpdate(timerSeconds - 1)
            } else if (isResting) {
                onRestCompleted()
            }
        }
        
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Progress indicator
            LinearProgressIndicator(
                progress = (currentExerciseIndex + 1).toFloat() / routine.exercises.size,
                modifier = Modifier.fillMaxWidth()
            )
            
            Text(
                "Ejercicio ${currentExerciseIndex + 1} de ${routine.exercises.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (isResting) {
                RestScreen(
                    timerSeconds = timerSeconds,
                    nextExercise = if (currentExerciseIndex < routine.exercises.size - 1) 
                        routine.exercises[currentExerciseIndex + 1] else null
                )
            } else {
                ExerciseExecutionCard(
                    exercise = currentExercise,
                    currentSet = currentSet,
                    repsCompleted = repsCompleted,
                    weightUsed = weightUsed,
                    distanceCompleted = distanceCompleted,
                    timeCompleted = timeCompleted,
                    onRepsChanged = { repsCompleted = it },
                    onWeightChanged = { weightUsed = it },
                    onDistanceChanged = { distanceCompleted = it },
                    onSetCompleted = {
                        if (currentSet < currentExercise.sets) {
                            currentSet++
                            repsCompleted = 0
                        } else {
                            // Exercise completed
                            val completedExercise = CompletedExercise(
                                exerciseName = currentExercise.name,
                                setsCompleted = currentExercise.sets,
                                repsCompleted = repsCompleted,
                                weightUsed = weightUsed.toFloatOrNull() ?: 0f,
                                distanceCompleted = distanceCompleted.toFloatOrNull() ?: 0f,
                                timeCompleted = if (currentExercise.durationSeconds > 0) currentExercise.durationSeconds else timeCompleted,
                                caloriesBurned = calculateCaloriesBurned(currentExercise, repsCompleted, weightUsed.toFloatOrNull() ?: 0f)
                            )
                            onExerciseCompleted(completedExercise)
                            
                            // Reset for next exercise
                            currentSet = 1
                            repsCompleted = 0
                            timeCompleted = 0
                        }
                    }
                )
            }
        }
    }
    
    @Composable
    fun RestScreen(timerSeconds: Int, nextExercise: Exercise?) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Descanso",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    formatTime(timerSeconds),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                nextExercise?.let { exercise ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Siguiente: ${exercise.name}",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    
    @Composable
    fun ExerciseExecutionCard(
        exercise: Exercise,
        currentSet: Int,
        repsCompleted: Int,
        weightUsed: String,
        distanceCompleted: String,
        timeCompleted: Int,
        onRepsChanged: (Int) -> Unit,
        onWeightChanged: (String) -> Unit,
        onDistanceChanged: (String) -> Unit,
        onSetCompleted: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    exercise.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                if (exercise.sets > 1) {
                    Text(
                        "Serie $currentSet de ${exercise.sets}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Exercise-specific inputs
                when (exercise.exerciseType) {
                    "STRENGTH" -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = repsCompleted.toString(),
                                onValueChange = { onRepsChanged(it.toIntOrNull() ?: 0) },
                                label = { Text("Repeticiones") },
                                modifier = Modifier.weight(1f)
                            )
                            
                            OutlinedTextField(
                                value = weightUsed,
                                onValueChange = onWeightChanged,
                                label = { Text("Peso (kg)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    "CARDIO" -> {
                        OutlinedTextField(
                            value = distanceCompleted,
                            onValueChange = onDistanceChanged,
                            label = { Text("Distancia (km)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "HIIT" -> {
                        Text(
                            "Tiempo objetivo: ${exercise.durationSeconds}s",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Button(
                    onClick = onSetCompleted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        if (currentSet < exercise.sets) "Completar Serie" else "Completar Ejercicio",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    
    @Composable
    fun WorkoutSummary(
        routine: WorkoutRoutine,
        completedExercises: List<CompletedExercise>,
        sessionDuration: Int,
        onFinish: () -> Unit
    ) {
        val totalCalories = completedExercises.sumOf { it.caloriesBurned }
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF4CAF50),
                                        Color(0xFF8BC34A)
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(20.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "¡Entrenamiento Completado!",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Excelente trabajo, sigue así",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard(
                        title = "Duración",
                        value = formatTime(sessionDuration),
                        icon = Icons.Filled.Timer,
                        modifier = Modifier.weight(1f)
                    )
                    
                    SummaryCard(
                        title = "Calorías",
                        value = "${totalCalories.roundToInt()}",
                        icon = Icons.Filled.LocalFireDepartment,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            item {
                Text(
                    "Ejercicios completados:",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            itemsIndexed(completedExercises) { index, exercise ->
                CompletedExerciseCard(exercise = exercise, index = index + 1)
            }
            
            item {
                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Finalizar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    
    @Composable
    fun SummaryCard(
        title: String,
        value: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        modifier: Modifier = Modifier
    ) {
        Card(
            modifier = modifier,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    @Composable
    fun CompletedExerciseCard(exercise: CompletedExercise, index: Int) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Color(0xFF4CAF50),
                            RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        exercise.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val details = buildString {
                        if (exercise.setsCompleted > 0) append("${exercise.setsCompleted} series")
                        if (exercise.repsCompleted > 0) {
                            if (isNotEmpty()) append(" • ")
                            append("${exercise.repsCompleted} reps")
                        }
                        if (exercise.weightUsed > 0) {
                            if (isNotEmpty()) append(" • ")
                            append("${exercise.weightUsed}kg")
                        }
                        if (exercise.distanceCompleted > 0) {
                            if (isNotEmpty()) append(" • ")
                            append("${exercise.distanceCompleted}km")
                        }
                    }
                    
                    Text(
                        details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    "${exercise.caloriesBurned.roundToInt()} cal",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    
    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }
    
    private fun calculateCaloriesBurned(exercise: Exercise, reps: Int, weight: Float): Double {
        // Simple calorie calculation based on exercise type
        return when (exercise.exerciseType) {
            "STRENGTH" -> (reps * 0.5 + weight * 0.1) * exercise.sets
            "CARDIO" -> exercise.durationSeconds * 0.1 + exercise.distance * 50
            "HIIT" -> exercise.durationSeconds * 0.15 * exercise.sets
            else -> 50.0
        }
    }
    
    private fun saveWorkoutSession(
        routine: WorkoutRoutine,
        completedExercises: List<CompletedExercise>,
        startTime: Long
    ) {
        lifecycleScope.launch {
            val userEmail = sessionManager.getUserSession()
            if (userEmail != null) {
                val session = WorkoutSession(
                    routineId = routine.id,
                    routineName = routine.name,
                    userEmail = userEmail,
                    startTime = startTime,
                    endTime = System.currentTimeMillis(),
                    durationMinutes = ((System.currentTimeMillis() - startTime) / 60000).toInt(),
                    completedExercises = completedExercises,
                    totalCaloriesBurned = completedExercises.sumOf { it.caloriesBurned }
                )
                database.workoutSessionDao().insert(session)
            }
        }
    }
}
