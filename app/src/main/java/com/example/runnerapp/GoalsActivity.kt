package com.example.runnerapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
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
import com.example.runnerapp.data.AppDatabase
import kotlinx.coroutines.launch

data class Goal(
    val id: Int,
    val title: String,
    val description: String,
    val targetValue: Float,
    val currentValue: Float,
    val unit: String,
    val type: GoalType,
    val isCompleted: Boolean = false,
    val motivationalTitle: String
)

enum class GoalType {
    DISTANCE, TIME, CALORIES, STEPS, FREQUENCY
}

class GoalsActivity : ComponentActivity() {
    
    private lateinit var database: AppDatabase
    private lateinit var sessionManager: SessionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        database = AppDatabase.getDatabase(this)
        sessionManager = SessionManager(this)
        
        setContent {
            GoalsScreen()
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun GoalsScreen() {
        var goals by remember { mutableStateOf(listOf<Goal>()) }
        var showAddDialog by remember { mutableStateOf(false) }
        
        // Load goals from database
        LaunchedEffect(Unit) {
            lifecycleScope.launch {
                goals = loadGoalsFromDatabase()
            }
        }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            "Sistema de Metas y Logros",
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
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Agregar Meta")
                }
            }
        ) { padding ->
            LazyColumn(
                contentPadding = padding,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Motivational Header
                item {
                    MotivationalHeader(completedGoals = goals.count { it.isCompleted })
                }
                
                // Active Goals
                item {
                    Text(
                        "Metas Activas",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(goals.filter { !it.isCompleted }) { goal ->
                    GoalCard(
                        goal = goal,
                        onGoalUpdate = { updatedGoal ->
                            goals = goals.map { if (it.id == updatedGoal.id) updatedGoal else it }
                        }
                    )
                }
                
                // Completed Goals
                val completedGoals = goals.filter { it.isCompleted }
                if (completedGoals.isNotEmpty()) {
                    item {
                        Text(
                            "Metas Completadas",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(completedGoals) { goal ->
                        CompletedGoalCard(goal = goal)
                    }
                }
                
                if (goals.isEmpty()) {
                    item {
                        EmptyStateCard()
                    }
                }
            }
        }
        
        if (showAddDialog) {
            AddGoalDialog(
                onDismiss = { showAddDialog = false },
                onGoalAdded = { newGoal ->
                    goals = goals + newGoal
                    showAddDialog = false
                }
            )
        }
    }
    
    @Composable
    fun MotivationalHeader(completedGoals: Int) {
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
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        getMotivationalMessage(completedGoals),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Metas completadas: $completedGoals",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    
    @Composable
    fun GoalCard(goal: Goal, onGoalUpdate: (Goal) -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                            goal.motivationalTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            goal.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            goal.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            if (goal.currentValue >= goal.targetValue) {
                                onGoalUpdate(goal.copy(isCompleted = true))
                            }
                        }
                    ) {
                        Icon(
                            if (goal.currentValue >= goal.targetValue) Icons.Filled.CheckCircle 
                            else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = "Completar meta",
                            tint = if (goal.currentValue >= goal.targetValue) 
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Progress bar
                val progress = (goal.currentValue / goal.targetValue).coerceIn(0f, 1f)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${goal.currentValue.toInt()} ${goal.unit}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "${goal.targetValue.toInt()} ${goal.unit}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = when {
                            progress >= 1f -> Color(0xFF4CAF50)
                            progress >= 0.7f -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    
                    Text(
                        "${(progress * 100).toInt()}% completado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
    
    @Composable
    fun CompletedGoalCard(goal: Goal) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Completado",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        goal.motivationalTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        goal.title,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Text(
                    "¡COMPLETADO!",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    
    @Composable
    fun EmptyStateCard() {
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
                    Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "¡Comienza tu viaje hacia el éxito!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Crea tu primera meta y comienza a alcanzar tus objetivos de fitness",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
    
    @Composable
    fun AddGoalDialog(onDismiss: () -> Unit, onGoalAdded: (Goal) -> Unit) {
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var targetValue by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(GoalType.DISTANCE) }
        
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Nueva Meta") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título de la meta") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Descripción") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = targetValue,
                        onValueChange = { targetValue = it.filter { char -> char.isDigit() || char == '.' } },
                        label = { Text("Valor objetivo") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Goal type selector
                    Text("Tipo de meta:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GoalType.values().forEach { type ->
                            FilterChip(
                                onClick = { selectedType = type },
                                label = { Text(getGoalTypeLabel(type)) },
                                selected = selectedType == type
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = targetValue.toFloatOrNull() ?: 0f
                        if (title.isNotBlank() && target > 0) {
                            val newGoal = Goal(
                                id = System.currentTimeMillis().toInt(),
                                title = title,
                                description = description,
                                targetValue = target,
                                currentValue = 0f,
                                unit = getGoalUnit(selectedType),
                                type = selectedType,
                                motivationalTitle = generateMotivationalTitle(selectedType, target)
                            )
                            onGoalAdded(newGoal)
                        }
                    }
                ) {
                    Text("Crear Meta")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    private fun getGoalTypeLabel(type: GoalType): String {
        return when (type) {
            GoalType.DISTANCE -> "Distancia"
            GoalType.TIME -> "Tiempo"
            GoalType.CALORIES -> "Calorías"
            GoalType.STEPS -> "Pasos"
            GoalType.FREQUENCY -> "Frecuencia"
        }
    }
    
    private fun getGoalUnit(type: GoalType): String {
        return when (type) {
            GoalType.DISTANCE -> "km"
            GoalType.TIME -> "min"
            GoalType.CALORIES -> "kcal"
            GoalType.STEPS -> "pasos"
            GoalType.FREQUENCY -> "días"
        }
    }
    
    private fun generateMotivationalTitle(type: GoalType, target: Float): String {
        return when (type) {
            GoalType.DISTANCE -> when {
                target >= 100 -> "¡Conquistador de Kilómetros!"
                target >= 50 -> "¡Explorador Incansable!"
                target >= 20 -> "¡Corredor Determinado!"
                else -> "¡Primer Paso al Éxito!"
            }
            GoalType.TIME -> when {
                target >= 300 -> "¡Maestro de la Resistencia!"
                target >= 120 -> "¡Guerrero del Tiempo!"
                target >= 60 -> "¡Atleta Perseverante!"
                else -> "¡Iniciando la Aventura!"
            }
            GoalType.CALORIES -> when {
                target >= 5000 -> "¡Incinerador de Calorías!"
                target >= 2000 -> "¡Quemador Imparable!"
                target >= 1000 -> "¡Energía en Movimiento!"
                else -> "¡Activando el Motor!"
            }
            GoalType.STEPS -> when {
                target >= 100000 -> "¡Caminante Legendario!"
                target >= 50000 -> "¡Explorador de Pasos!"
                target >= 20000 -> "¡Marchador Incansable!"
                else -> "¡Cada Paso Cuenta!"
            }
            GoalType.FREQUENCY -> when {
                target >= 30 -> "¡Hábito de Campeón!"
                target >= 14 -> "¡Constancia Inquebrantable!"
                target >= 7 -> "¡Semana de Poder!"
                else -> "¡Construyendo Disciplina!"
            }
        }
    }
    
    private fun getMotivationalMessage(completedGoals: Int): String {
        return when {
            completedGoals == 0 -> "¡Tu viaje hacia la grandeza comienza aquí!"
            completedGoals < 5 -> "¡Excelente progreso! Sigue así, campeón."
            completedGoals < 10 -> "¡Increíble dedicación! Eres imparable."
            completedGoals < 20 -> "¡Leyenda en construcción! Tu disciplina inspira."
            else -> "¡MAESTRO ABSOLUTO! Has alcanzado la excelencia."
        }
    }
    
    private suspend fun loadGoalsFromDatabase(): List<Goal> {
        // For now, return sample goals. In a real implementation, 
        // you would load from Room database
        return listOf(
            Goal(
                id = 1,
                title = "Correr 5km sin parar",
                description = "Completar una carrera de 5 kilómetros de forma continua",
                targetValue = 5f,
                currentValue = 3.2f,
                unit = "km",
                type = GoalType.DISTANCE,
                motivationalTitle = "¡Corredor Determinado!"
            ),
            Goal(
                id = 2,
                title = "Quemar 2000 calorías esta semana",
                description = "Alcanzar el objetivo semanal de calorías quemadas",
                targetValue = 2000f,
                currentValue = 1450f,
                unit = "kcal",
                type = GoalType.CALORIES,
                motivationalTitle = "¡Quemador Imparable!"
            )
        )
    }
}
