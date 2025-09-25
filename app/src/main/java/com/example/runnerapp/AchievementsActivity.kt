package com.example.runnerapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

data class AchievementUi(
    val id: Int,
    val name: String,
    val timeMinutes: Int,
    val imageUri: String?,
    val motivationalTitle: String = "",
    val description: String = "",
    val category: AchievementCategory = AchievementCategory.GENERAL
)

enum class AchievementCategory {
    GENERAL, DISTANCE, TIME, SPEED, CONSISTENCY, SPECIAL
}

class AchievementsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AchievementsScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen() {
    val items = remember { 
        mutableStateListOf<AchievementUi>().apply {
            addAll(getSampleAchievements())
        }
    }
    var autoId by remember { mutableIntStateOf(100) }

    var editing by remember { mutableStateOf<AchievementUi?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    fun upsert(a: AchievementUi) {
        val idx = items.indexOfFirst { it.id == a.id }
        if (idx >= 0) items[idx] = a else items.add(a)
    }
    fun delete(id: Int) {
        items.removeAll { it.id == id }
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { 
                    Text(
                        "Logros y Reconocimientos",
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
                onClick = {
                    editing = AchievementUi(
                        id = autoId, 
                        name = "", 
                        timeMinutes = 0, 
                        imageUri = null,
                        motivationalTitle = "",
                        description = ""
                    )
                    showDialog = true
                    autoId++
                },
                containerColor = MaterialTheme.colorScheme.secondary
            ) { 
                Icon(Icons.Filled.Add, contentDescription = "Agregar Logro") 
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
            item {
                MotivationalAchievementHeader(totalAchievements = items.size)
            }
            
            val groupedAchievements = items.groupBy { it.category }
            
            groupedAchievements.forEach { (category, achievements) ->
                item {
                    CategoryHeader(category = category)
                }
                
                items(achievements, key = { it.id }) { ach ->
                    EnhancedAchievementCard(
                        ach = ach,
                        onEdit = { editing = ach; showDialog = true },
                        onDelete = { delete(ach.id) }
                    )
                }
            }
            
            if (items.isEmpty()) {
                item { 
                    EmptyAchievementsState()
                }
            }
        }
    }

    if (showDialog && editing != null) {
        EnhancedAchievementDialog(
            initial = editing!!,
            onDismiss = { showDialog = false },
            onSave = { updated ->
                upsert(updated)
                showDialog = false
            }
        )
    }
}

@Composable
private fun MotivationalAchievementHeader(totalAchievements: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFFD700), // Gold
                            Color(0xFFFFA500)  // Orange
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
                    Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    getAchievementMotivationalMessage(totalAchievements),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Logros desbloqueados: $totalAchievements",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: AchievementCategory) {
    val (title, icon) = when (category) {
        AchievementCategory.GENERAL -> "Logros Generales" to Icons.Filled.Star
        AchievementCategory.DISTANCE -> "Maestro de Distancias" to Icons.Filled.EmojiEvents
        AchievementCategory.TIME -> "Guerrero del Tiempo" to Icons.Filled.EmojiEvents
        AchievementCategory.SPEED -> "Velocista Supremo" to Icons.Filled.EmojiEvents
        AchievementCategory.CONSISTENCY -> "Constancia Inquebrantable" to Icons.Filled.EmojiEvents
        AchievementCategory.SPECIAL -> "Logros Especiales" to Icons.Filled.EmojiEvents
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun EnhancedAchievementCard(
    ach: AchievementUi,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Motivational title
            if (ach.motivationalTitle.isNotBlank()) {
                Text(
                    ach.motivationalTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Achievement image or icon
                if (!ach.imageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(ach.imageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Imagen logro",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🏆", 
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ach.name.ifBlank { "Logro sin nombre" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    if (ach.description.isNotBlank()) {
                        Text(
                            text = ach.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    
                    Text(
                        text = "Tiempo: ${ach.timeMinutes} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Column {
                    IconButton(onClick = onEdit) { 
                        Icon(
                            Icons.Filled.Edit, 
                            contentDescription = "Editar",
                            tint = MaterialTheme.colorScheme.primary
                        ) 
                    }
                    IconButton(onClick = onDelete) { 
                        Icon(
                            Icons.Filled.Delete, 
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error
                        ) 
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhancedAchievementDialog(
    initial: AchievementUi,
    onDismiss: () -> Unit,
    onSave: (AchievementUi) -> Unit
) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(TextFieldValue(initial.name)) }
    var motivationalTitle by remember { mutableStateOf(TextFieldValue(initial.motivationalTitle)) }
    var description by remember { mutableStateOf(TextFieldValue(initial.description)) }
    var time by remember { mutableStateOf(TextFieldValue(initial.timeMinutes.takeIf { it > 0 }?.toString() ?: "")) }
    var imageUri by remember { mutableStateOf(initial.imageUri) }
    var selectedCategory by remember { mutableStateOf(initial.category) }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            imageUri = it.toString()
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                if (initial.name.isBlank()) "Nuevo Logro Épico" else "Editar Logro",
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = motivationalTitle,
                        onValueChange = { motivationalTitle = it },
                        label = { Text("Título Motivacional") },
                        placeholder = { Text("ej: ¡Conquistador Imparable!") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre del Logro") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Descripción") },
                        placeholder = { Text("Describe este increíble logro...") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                item {
                    OutlinedTextField(
                        value = time,
                        onValueChange = { t -> time = t.copy(text = t.text.filter(Char::isDigit)) },
                        label = { Text("Tiempo (min)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                item {
                    Text("Categoría:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AchievementCategory.values().take(3).forEach { category ->
                            FilterChip(
                                onClick = { selectedCategory = category },
                                label = { Text(getCategoryLabel(category)) },
                                selected = selectedCategory == category
                            )
                        }
                    }
                }
                
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {
                            try {
                                pickMedia.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } catch (_: Exception) { }
                        }) { 
                            Text(if (imageUri == null) "Elegir Imagen Épica" else "Cambiar Imagen") 
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        if (!imageUri.isNullOrBlank()) {
                            AsyncImage(
                                model = imageUri,
                                contentDescription = "Preview",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val minutes = time.text.toIntOrNull() ?: 0
                val finalMotivationalTitle = if (motivationalTitle.text.isBlank()) {
                    generateRandomMotivationalTitle()
                } else {
                    motivationalTitle.text.trim()
                }
                
                onSave(initial.copy(
                    name = name.text.trim(), 
                    timeMinutes = minutes, 
                    imageUri = imageUri,
                    motivationalTitle = finalMotivationalTitle,
                    description = description.text.trim(),
                    category = selectedCategory
                ))
            }) { 
                Text("Guardar Logro") 
            }
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text("Cancelar") 
            } 
        }
    )
}

@Composable
private fun EmptyAchievementsState() {
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
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "¡Tu salón de la fama te espera!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "Crea tu primer logro y comienza a construir tu legado de campeón",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun getAchievementMotivationalMessage(totalAchievements: Int): String {
    return when {
        totalAchievements == 0 -> "¡Tu leyenda comienza aquí!"
        totalAchievements < 5 -> "¡Construyendo tu imperio de logros!"
        totalAchievements < 10 -> "¡Coleccionista de victorias épicas!"
        totalAchievements < 20 -> "¡Maestro de los desafíos!"
        else -> "¡LEYENDA ABSOLUTA DEL FITNESS!"
    }
}

private fun getCategoryLabel(category: AchievementCategory): String {
    return when (category) {
        AchievementCategory.GENERAL -> "General"
        AchievementCategory.DISTANCE -> "Distancia"
        AchievementCategory.TIME -> "Tiempo"
        AchievementCategory.SPEED -> "Velocidad"
        AchievementCategory.CONSISTENCY -> "Constancia"
        AchievementCategory.SPECIAL -> "Especial"
    }
}

private fun generateRandomMotivationalTitle(): String {
    val titles = listOf(
        "¡Conquistador Imparable!",
        "¡Guerrero de Elite!",
        "¡Campeón Inquebrantable!",
        "¡Leyenda en Acción!",
        "¡Maestro del Desafío!",
        "¡Héroe del Fitness!",
        "¡Titán Incansable!",
        "¡Gladiador Moderno!"
    )
    return titles.random()
}

private fun getSampleAchievements(): List<AchievementUi> {
    return listOf(
        AchievementUi(
            id = 1,
            name = "Primera carrera de 5K",
            timeMinutes = 30,
            imageUri = null,
            motivationalTitle = "¡Conquistador de Kilómetros!",
            description = "Completaste tu primera carrera de 5 kilómetros. ¡El comienzo de una gran aventura!",
            category = AchievementCategory.DISTANCE
        ),
        AchievementUi(
            id = 2,
            name = "Corredor constante",
            timeMinutes = 45,
            imageUri = null,
            motivationalTitle = "¡Guerrero de la Disciplina!",
            description = "7 días consecutivos de actividad física. Tu constancia es admirable.",
            category = AchievementCategory.CONSISTENCY
        ),
        AchievementUi(
            id = 3,
            name = "Velocista urbano",
            timeMinutes = 20,
            imageUri = null,
            motivationalTitle = "¡Rayo de Velocidad!",
            description = "Alcanzaste una velocidad promedio de 15 km/h. ¡Eres pura velocidad!",
            category = AchievementCategory.SPEED
        )
    )
}
