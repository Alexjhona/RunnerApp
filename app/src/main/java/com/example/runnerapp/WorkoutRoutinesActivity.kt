package com.example.runnerapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.runnerapp.data.*
import kotlinx.coroutines.launch

class WorkoutRoutinesActivity : ComponentActivity() {
    
    private lateinit var database: AppDatabase
    private lateinit var sessionManager: SessionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        database = AppDatabase.getDatabase(this)
        sessionManager = SessionManager(this)
        
        // Initialize predefined routines on first launch
        lifecycleScope.launch {
            initializePredefinedRoutines()
        }
        
        setContent {
            WorkoutRoutinesScreen()
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun WorkoutRoutinesScreen() {
        var routines by remember { mutableStateOf(listOf<WorkoutRoutine>()) }
        var selectedTab by remember { mutableStateOf(0) }
        var showCreateDialog by remember { mutableStateOf(false) }
        
        LaunchedEffect(selectedTab) {
            lifecycleScope.launch {
                routines = when (selectedTab) {
                    0 -> database.workoutRoutineDao().getPredefinedRoutines()
                    1 -> {
                        val userEmail = sessionManager.getUserSession()
                        if (userEmail != null) {
                            database.workoutRoutineDao().getUserCustomRoutines(userEmail)
                        } else emptyList()
                    }
                    else -> emptyList()
                }
            }
        }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            "Rutinas de Entrenamiento",
                            fontWeight = FontWeight.Bold
                        ) 
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                if (selectedTab == 1) {
                    FloatingActionButton(
                        onClick = { showCreateDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Crear Rutina")
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Tab Row
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Predefinidas") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Mis Rutinas") }
                    )
                }
                
                // Content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (routines.isEmpty()) {
                        item {
                            EmptyStateCard(selectedTab == 0)
                        }
                    } else {
                        items(routines) { routine ->
                            RoutineCard(
                                routine = routine,
                                onClick = { startWorkoutSession(routine) }
                            )
                        }
                    }
                }
            }
        }
        
        if (showCreateDialog) {
            CreateRoutineDialog(
                onDismiss = { showCreateDialog = false },
                onRoutineCreated = { newRoutine ->
                    lifecycleScope.launch {
                        database.workoutRoutineDao().insert(newRoutine)
                        // Refresh custom routines
                        val userEmail = sessionManager.getUserSession()
                        if (userEmail != null) {
                            routines = database.workoutRoutineDao().getUserCustomRoutines(userEmail)
                        }
                    }
                    showCreateDialog = false
                }
            )
        }
    }
    
    @Composable
    fun RoutineCard(routine: WorkoutRoutine, onClick: () -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            routine.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            routine.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Icon(
                        getRoutineTypeIcon(routine.type),
                        contentDescription = routine.type,
                        tint = getRoutineTypeColor(routine.type),
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AssistChip(
                        onClick = { },
                        label = { Text(routine.type) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = getRoutineTypeColor(routine.type).copy(alpha = 0.1f),
                            labelColor = getRoutineTypeColor(routine.type)
                        )
                    )
                    
                    AssistChip(
                        onClick = { },
                        label = { Text(routine.difficulty) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = getDifficultyColor(routine.difficulty).copy(alpha = 0.1f),
                            labelColor = getDifficultyColor(routine.difficulty)
                        )
                    )
                    
                    Text(
                        "${routine.durationMinutes} min",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "${routine.exercises.size} ejercicios",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    @Composable
    fun EmptyStateCard(isPredefined: Boolean) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    if (isPredefined) Icons.Filled.FitnessCenter else Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    if (isPredefined) "Cargando rutinas..." else "¡Crea tu primera rutina!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (isPredefined) "Las rutinas predefinidas se están cargando" 
                    else "Diseña rutinas personalizadas para tus objetivos",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
    
    @Composable
    fun CreateRoutineDialog(
        onDismiss: () -> Unit,
        onRoutineCreated: (WorkoutRoutine) -> Unit
    ) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf("STRENGTH") }
        var selectedDifficulty by remember { mutableStateOf("BEGINNER") }
        var duration by remember { mutableStateOf("30") }
        
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Nueva Rutina Personalizada") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre de la rutina") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Descripción") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = duration,
                        onValueChange = { duration = it.filter { char -> char.isDigit() } },
                        label = { Text("Duración (minutos)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text("Tipo de rutina:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("STRENGTH", "CARDIO", "HIIT").forEach { type ->
                            FilterChip(
                                onClick = { selectedType = type },
                                label = { Text(type) },
                                selected = selectedType == type
                            )
                        }
                    }
                    
                    Text("Dificultad:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("BEGINNER", "INTERMEDIATE", "ADVANCED").forEach { difficulty ->
                            FilterChip(
                                onClick = { selectedDifficulty = difficulty },
                                label = { Text(difficulty) },
                                selected = selectedDifficulty == difficulty
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val durationInt = duration.toIntOrNull() ?: 30
                        val userEmail = sessionManager.getUserSession()
                        if (name.isNotBlank() && userEmail != null) {
                            val newRoutine = WorkoutRoutine(
                                name = name,
                                description = description,
                                type = selectedType,
                                difficulty = selectedDifficulty,
                                durationMinutes = durationInt,
                                exercises = getDefaultExercisesForType(selectedType),
                                isPredefined = false,
                                createdBy = userEmail
                            )
                            onRoutineCreated(newRoutine)
                        }
                    }
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    private fun getRoutineTypeIcon(type: String): ImageVector {
        return when (type) {
            "STRENGTH" -> Icons.Filled.FitnessCenter
            "CARDIO" -> Icons.Filled.DirectionsRun
            "HIIT" -> Icons.Filled.Timer
            else -> Icons.Filled.FitnessCenter
        }
    }
    
    private fun getRoutineTypeColor(type: String): Color {
        return when (type) {
            "STRENGTH" -> Color(0xFF2196F3) // Blue
            "CARDIO" -> Color(0xFF4CAF50) // Green
            "HIIT" -> Color(0xFFFF9800) // Orange
            else -> Color(0xFF9C27B0) // Purple
        }
    }
    
    private fun getDifficultyColor(difficulty: String): Color {
        return when (difficulty) {
            "BEGINNER" -> Color(0xFF4CAF50) // Green
            "INTERMEDIATE" -> Color(0xFFFF9800) // Orange
            "ADVANCED" -> Color(0xFFF44336) // Red
            else -> Color(0xFF9E9E9E) // Gray
        }
    }
    
    private fun startWorkoutSession(routine: WorkoutRoutine) {
        val intent = Intent(this, WorkoutSessionActivity::class.java)
        intent.putExtra("routine_id", routine.id)
        intent.putExtra("routine_name", routine.name)
        startActivity(intent)
    }
    
    private fun getDefaultExercisesForType(type: String): List<Exercise> {
        return when (type) {
            "STRENGTH" -> listOf(
                Exercise("Push-ups", "Flexiones de pecho", 3, 12, 0, 60, 0f, 0f, 0f, "STRENGTH"),
                Exercise("Squats", "Sentadillas", 3, 15, 0, 60, 0f, 0f, 0f, "STRENGTH"),
                Exercise("Plank", "Plancha", 3, 0, 30, 60, 0f, 0f, 0f, "STRENGTH")
            )
            "CARDIO" -> listOf(
                Exercise("Running", "Correr", 1, 0, 1200, 0, 0f, 2f, 8f, "CARDIO"),
                Exercise("Jumping Jacks", "Saltos de tijera", 3, 20, 0, 30, 0f, 0f, 0f, "CARDIO"),
                Exercise("Mountain Climbers", "Escaladores", 3, 15, 0, 30, 0f, 0f, 0f, "CARDIO")
            )
            "HIIT" -> listOf(
                Exercise("Burpees", "Burpees", 4, 10, 0, 30, 0f, 0f, 0f, "HIIT"),
                Exercise("High Knees", "Rodillas altas", 4, 0, 30, 15, 0f, 0f, 0f, "HIIT"),
                Exercise("Jump Squats", "Sentadillas con salto", 4, 12, 0, 30, 0f, 0f, 0f, "HIIT")
            )
            else -> emptyList()
        }
    }
    
    private suspend fun initializePredefinedRoutines() {
        val existingRoutines = database.workoutRoutineDao().getPredefinedRoutines()
        if (existingRoutines.isEmpty()) {
            val predefinedRoutines = listOf(
                // Strength Routines
                WorkoutRoutine(
                    name = "Fuerza para Principiantes",
                    description = "Rutina básica de fuerza con ejercicios corporales",
                    type = "STRENGTH",
                    difficulty = "BEGINNER",
                    durationMinutes = 30,
                    exercises = listOf(
                        Exercise("Push-ups", "Flexiones de pecho", 3, 8, 0, 60, 0f, 0f, 0f, "STRENGTH"),
                        Exercise("Squats", "Sentadillas", 3, 12, 0, 60, 0f, 0f, 0f, "STRENGTH"),
                        Exercise("Plank", "Plancha", 3, 0, 20, 60, 0f, 0f, 0f, "STRENGTH"),
                        Exercise("Lunges", "Zancadas", 3, 10, 0, 60, 0f, 0f, 0f, "STRENGTH")
                    ),
                    isPredefined = true
                ),
                WorkoutRoutine(
                    name = "Fuerza Intermedia",
                    description = "Rutina de fuerza con mayor intensidad",
                    type = "STRENGTH",
                    difficulty = "INTERMEDIATE",
                    durationMinutes = 45,
                    exercises = listOf(
                        Exercise("Push-ups", "Flexiones de pecho", 4, 12, 0, 60, 0f, 0f, 0f, "STRENGTH"),
                        Exercise("Squats", "Sentadillas", 4, 15, 0, 60, 0f, 0f, 0f, "STRENGTH"),
                        Exercise("Plank", "Plancha", 4, 0, 30, 60, 0f, 0f, 0f, "STRENGTH"),
                        Exercise("Pull-ups", "Dominadas", 4, 8, 0, 90, 0f, 0f, 0f, "STRENGTH"),
                        Exercise("Dips", "Fondos", 4, 10, 0, 60, 0f, 0f, 0f, "STRENGTH")
                    ),
                    isPredefined = true
                ),
                
                // Cardio Routines
                WorkoutRoutine(
                    name = "Cardio Básico",
                    description = "Rutina cardiovascular para principiantes",
                    type = "CARDIO",
                    difficulty = "BEGINNER",
                    durationMinutes = 25,
                    exercises = listOf(
                        Exercise("Walking", "Caminar", 1, 0, 600, 0, 0f, 1.5f, 5f, "CARDIO"),
                        Exercise("Jumping Jacks", "Saltos de tijera", 3, 15, 0, 30, 0f, 0f, 0f, "CARDIO"),
                        Exercise("Step-ups", "Subir escalón", 3, 12, 0, 30, 0f, 0f, 0f, "CARDIO")
                    ),
                    isPredefined = true
                ),
                WorkoutRoutine(
                    name = "Cardio Intenso",
                    description = "Rutina cardiovascular de alta intensidad",
                    type = "CARDIO",
                    difficulty = "ADVANCED",
                    durationMinutes = 40,
                    exercises = listOf(
                        Exercise("Running", "Correr", 1, 0, 1800, 0, 0f, 3f, 10f, "CARDIO"),
                        Exercise("Jumping Jacks", "Saltos de tijera", 4, 25, 0, 30, 0f, 0f, 0f, "CARDIO"),
                        Exercise("Mountain Climbers", "Escaladores", 4, 20, 0, 30, 0f, 0f, 0f, "CARDIO"),
                        Exercise("Burpees", "Burpees", 3, 8, 0, 60, 0f, 0f, 0f, "CARDIO")
                    ),
                    isPredefined = true
                ),
                
                // HIIT Routines
                WorkoutRoutine(
                    name = "HIIT Principiante",
                    description = "Entrenamiento intervalado de alta intensidad básico",
                    type = "HIIT",
                    difficulty = "BEGINNER",
                    durationMinutes = 20,
                    exercises = listOf(
                        Exercise("Burpees", "Burpees", 4, 5, 0, 45, 0f, 0f, 0f, "HIIT"),
                        Exercise("High Knees", "Rodillas altas", 4, 0, 20, 40, 0f, 0f, 0f, "HIIT"),
                        Exercise("Jump Squats", "Sentadillas con salto", 4, 8, 0, 45, 0f, 0f, 0f, "HIIT")
                    ),
                    isPredefined = true
                ),
                WorkoutRoutine(
                    name = "HIIT Avanzado",
                    description = "Entrenamiento intervalado de máxima intensidad",
                    type = "HIIT",
                    difficulty = "ADVANCED",
                    durationMinutes = 30,
                    exercises = listOf(
                        Exercise("Burpees", "Burpees", 6, 12, 0, 30, 0f, 0f, 0f, "HIIT"),
                        Exercise("High Knees", "Rodillas altas", 6, 0, 30, 20, 0f, 0f, 0f, "HIIT"),
                        Exercise("Jump Squats", "Sentadillas con salto", 6, 15, 0, 30, 0f, 0f, 0f, "HIIT"),
                        Exercise("Mountain Climbers", "Escaladores", 6, 20, 0, 20, 0f, 0f, 0f, "HIIT"),
                        Exercise("Plank Jacks", "Plancha con saltos", 6, 12, 0, 30, 0f, 0f, 0f, "HIIT")
                    ),
                    isPredefined = true
                )
            )
            
            predefinedRoutines.forEach { routine ->
                database.workoutRoutineDao().insert(routine)
            }
        }
    }
}
