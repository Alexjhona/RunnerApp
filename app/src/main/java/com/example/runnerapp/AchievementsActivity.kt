package com.example.runnerapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

// Modelo UI simple (persistiremos con Room mañana)
data class AchievementUi(
    val id: Int,
    val name: String,
    val timeMinutes: Int,
    val imageUri: String? // Uri.toString()
)

class AchievementsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AchievementsScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen() {
    // Lista en memoria (mutableStateListOf para evitar líos de tipos)
    val items = remember { mutableStateListOf<AchievementUi>() }
    var autoId by remember { mutableIntStateOf(1) }

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
        topBar = { TopAppBar(title = { Text("Logros") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = AchievementUi(id = autoId, name = "", timeMinutes = 0, imageUri = null)
                showDialog = true
                autoId++
            }) { Icon(Icons.Filled.Add, contentDescription = "Agregar") }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            items(items, key = { it.id }) { ach ->
                AchievementCard(
                    ach = ach,
                    onEdit = { editing = ach; showDialog = true },
                    onDelete = { delete(ach.id) }
                )
                Spacer(Modifier.height(12.dp))
            }
            if (items.isEmpty()) {
                item { Text("Aún no hay logros. Toca + para crear el primero.") }
            }
        }
    }

    if (showDialog && editing != null) {
        AchievementDialog(
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
private fun AchievementCard(
    ach: AchievementUi,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!ach.imageUri.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(ach.imageUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Imagen logro",
                    modifier = Modifier.size(56.dp)
                )
            } else {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Text("🏅", style = MaterialTheme.typography.titleLarge)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = ach.name.ifBlank { "Sin nombre" },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Tiempo: ${ach.timeMinutes} min",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
        }
    }
}

@Composable
private fun AchievementDialog(
    initial: AchievementUi,
    onDismiss: () -> Unit,
    onSave: (AchievementUi) -> Unit
) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(TextFieldValue(initial.name)) }
    var time by remember { mutableStateOf(TextFieldValue(initial.timeMinutes.takeIf { it > 0 }?.toString() ?: "")) }
    var imageUri by remember { mutableStateOf(initial.imageUri) }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            imageUri = it.toString()
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* puede no aplicar */ }
        }
    }
    val pickFallback = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
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
        title = { Text(if (initial.name.isBlank()) "Nuevo logro" else "Editar logro") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { t -> time = t.copy(text = t.text.filter(Char::isDigit)) },
                    label = { Text("Tiempo (min)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        try {
                            pickMedia.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        } catch (_: Exception) {
                            pickFallback.launch("image/*")
                        }
                    }) { Text(if (imageUri == null) "Elegir imagen" else "Cambiar imagen") }

                    Spacer(Modifier.width(12.dp))

                    if (!imageUri.isNullOrBlank()) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "Preview",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val minutes = time.text.toIntOrNull() ?: 0
                onSave(initial.copy(name = name.text.trim(), timeMinutes = minutes, imageUri = imageUri))
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
