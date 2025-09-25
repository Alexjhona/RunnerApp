package com.example.runnerapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class HydrationActivity : ComponentActivity() {
    
    private lateinit var database: AppDatabase
    private lateinit var sessionManager: SessionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        database = AppDatabase.getDatabase(this)
        sessionManager = SessionManager(this)
        
        setContent {
            HydrationScreen()
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun HydrationScreen() {
        var todayRecord by remember { mutableStateOf<HydrationRecord?>(null) }
        var hydrationSettings by remember { mutableStateOf<HydrationSettings?>(null) }
        var recentRecords by remember { mutableStateOf(listOf<HydrationRecord>()) }
        var showSettingsDialog by remember { mutableStateOf(false) }
        var showAddWaterDialog by remember { mutableStateOf(false) }
        
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        LaunchedEffect(Unit) {
            lifecycleScope.launch {
                val userEmail = sessionManager.getUserSession()
                if (userEmail != null) {
                    // Load today's record
                    todayRecord = database.hydrationDao().getRecordForDate(userEmail, today)
                    
                    // Load settings
                    hydrationSettings = database.hydrationDao().getSettings(userEmail)
                    if (hydrationSettings == null) {
                        // Create default settings
                        val defaultSettings = HydrationSettings(userEmail = userEmail)
                        database.hydrationDao().insertSettings(defaultSettings)
                        hydrationSettings = defaultSettings
                    }
                    
                    // Create today's record if it doesn't exist
                    if (todayRecord == null) {
                        val newRecord = HydrationRecord(
                            userEmail = userEmail,
                            date = today,
                            waterIntakeMl = 0,
                            targetIntakeMl = hydrationSettings?.dailyTargetMl ?: 2000
                        )
                        database.hydrationDao().insertRecord(newRecord)
                        todayRecord = newRecord
                    }
                    
                    // Load recent records
                    recentRecords = database.hydrationDao().getRecentRecords(userEmail)
                }
            }
        }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            "Hidratación",
                            fontWeight = FontWeight.Bold
                        ) 
                    },
                    actions = {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Configuración")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddWaterDialog = true },
                    containerColor = Color(0xFF2196F3)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Agregar agua", tint = Color.White)
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    todayRecord?.let { record ->
                        HydrationProgressCard(record = record)
                    }
                }
                
                item {
                    QuickAddWaterCard(
                        onAddWater = { amount ->
                            addWaterIntake(amount, today) { updatedRecord ->
                                todayRecord = updatedRecord
                            }
                        }
                    )
                }
                
                item {
                    HydrationTipsCard()
                }
                
                item {
                    Text(
                        "Historial reciente:",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(recentRecords.take(7)) { record ->
                    HydrationHistoryCard(record = record)
                }
                
                if (recentRecords.isEmpty()) {
                    item {
                        EmptyHistoryCard()
                    }
                }
            }
        }
        
        if (showSettingsDialog) {
            hydrationSettings?.let { settings ->
                HydrationSettingsDialog(
                    settings = settings,
                    onDismiss = { showSettingsDialog = false },
                    onSettingsUpdated = { updatedSettings ->
                        lifecycleScope.launch {
                            database.hydrationDao().updateSettings(updatedSettings)
                            hydrationSettings = updatedSettings
                            setupHydrationReminders(updatedSettings)
                        }
                        showSettingsDialog = false
                    }
                )
            }
        }
        
        if (showAddWaterDialog) {
            AddWaterDialog(
                onDismiss = { showAddWaterDialog = false },
                onWaterAdded = { amount ->
                    addWaterIntake(amount, today) { updatedRecord ->
                        todayRecord = updatedRecord
                    }
                    showAddWaterDialog = false
                }
            )
        }
    }
    
    @Composable
    fun HydrationProgressCard(record: HydrationRecord) {
        val progress = (record.waterIntakeMl.toFloat() / record.targetIntakeMl.toFloat()).coerceIn(0f, 1f)
        val progressColor = when {
            progress >= 1f -> Color(0xFF4CAF50)
            progress >= 0.7f -> Color(0xFF2196F3)
            progress >= 0.4f -> Color(0xFFFF9800)
            else -> Color(0xFFF44336)
        }
        
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
                                Color(0xFF2196F3),
                                Color(0xFF03DAC6)
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
                        Icons.Filled.WaterDrop,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Hidratación de hoy",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "${record.waterIntakeMl} ml de ${record.targetIntakeMl} ml",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        "${(progress * 100).roundToInt()}% completado",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
    
    @Composable
    fun QuickAddWaterCard(onAddWater: (Int) -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Agregar agua rápido:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickAddButton(
                        amount = 250,
                        label = "Vaso",
                        icon = Icons.Filled.LocalDrink,
                        onClick = { onAddWater(250) },
                        modifier = Modifier.weight(1f)
                    )
                    
                    QuickAddButton(
                        amount = 500,
                        label = "Botella",
                        icon = Icons.Filled.WaterDrop,
                        onClick = { onAddWater(500) },
                        modifier = Modifier.weight(1f)
                    )
                    
                    QuickAddButton(
                        amount = 1000,
                        label = "Litro",
                        icon = Icons.Filled.LocalBar,
                        onClick = { onAddWater(1000) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
    
    @Composable
    fun QuickAddButton(
        amount: Int,
        label: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Card(
            modifier = modifier.clickable { onClick() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${amount}ml",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
    
    @Composable
    fun HydrationTipsCard() {
        val tips = listOf(
            "💧 Bebe agua al despertar para rehidratar tu cuerpo",
            "🏃 Aumenta la ingesta durante el ejercicio",
            "🍎 Las frutas y verduras también aportan hidratación",
            "⏰ Establece recordatorios regulares para beber agua",
            "🌡️ Bebe más agua en días calurosos",
            "💤 Evita exceso de agua antes de dormir"
        )
        
        val randomTip = remember { tips.random() }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        "Consejo de hidratación",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        randomTip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
    
    @Composable
    fun HydrationHistoryCard(record: HydrationRecord) {
        val progress = (record.waterIntakeMl.toFloat() / record.targetIntakeMl.toFloat()).coerceIn(0f, 1f)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val date = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(record.date)
        } catch (e: Exception) {
            Date()
        }
        
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
                            if (progress >= 1f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (progress >= 1f) Icons.Filled.CheckCircle else Icons.Filled.WaterDrop,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dateFormat.format(date ?: Date()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${record.waterIntakeMl} ml de ${record.targetIntakeMl} ml",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    "${(progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (progress >= 1f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    
    @Composable
    fun EmptyHistoryCard() {
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
                    Icons.Filled.WaterDrop,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "¡Comienza tu viaje de hidratación!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Registra tu consumo de agua diario para mantener una hidratación óptima",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
    
    @Composable
    fun HydrationSettingsDialog(
        settings: HydrationSettings,
        onDismiss: () -> Unit,
        onSettingsUpdated: (HydrationSettings) -> Unit
    ) {
        var dailyTarget by remember { mutableStateOf(settings.dailyTargetMl.toString()) }
        var reminderInterval by remember { mutableStateOf(settings.reminderIntervalMinutes.toString()) }
        var reminderEnabled by remember { mutableStateOf(settings.reminderEnabled) }
        var startHour by remember { mutableStateOf(settings.reminderStartHour.toString()) }
        var endHour by remember { mutableStateOf(settings.reminderEndHour.toString()) }
        
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Configuración de Hidratación") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = dailyTarget,
                        onValueChange = { dailyTarget = it.filter { char -> char.isDigit() } },
                        label = { Text("Meta diaria (ml)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = reminderEnabled,
                            onCheckedChange = { reminderEnabled = it }
                        )
                        Text("Activar recordatorios")
                    }
                    
                    if (reminderEnabled) {
                        OutlinedTextField(
                            value = reminderInterval,
                            onValueChange = { reminderInterval = it.filter { char -> char.isDigit() } },
                            label = { Text("Intervalo de recordatorio (minutos)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = startHour,
                                onValueChange = { startHour = it.filter { char -> char.isDigit() } },
                                label = { Text("Hora inicio") },
                                modifier = Modifier.weight(1f)
                            )
                            
                            OutlinedTextField(
                                value = endHour,
                                onValueChange = { endHour = it.filter { char -> char.isDigit() } },
                                label = { Text("Hora fin") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updatedSettings = settings.copy(
                            dailyTargetMl = dailyTarget.toIntOrNull() ?: 2000,
                            reminderIntervalMinutes = reminderInterval.toIntOrNull() ?: 60,
                            reminderEnabled = reminderEnabled,
                            reminderStartHour = startHour.toIntOrNull()?.coerceIn(0, 23) ?: 8,
                            reminderEndHour = endHour.toIntOrNull()?.coerceIn(0, 23) ?: 22
                        )
                        onSettingsUpdated(updatedSettings)
                    }
                ) {
                    Text("Guardar")
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
    fun AddWaterDialog(
        onDismiss: () -> Unit,
        onWaterAdded: (Int) -> Unit
    ) {
        var amount by remember { mutableStateOf("250") }
        
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Agregar Agua") },
            text = {
                Column {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { char -> char.isDigit() } },
                        label = { Text("Cantidad (ml)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        "Cantidades comunes:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("250", "500", "750", "1000").forEach { commonAmount ->
                            FilterChip(
                                onClick = { amount = commonAmount },
                                label = { Text("${commonAmount}ml") },
                                selected = amount == commonAmount
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val amountInt = amount.toIntOrNull() ?: 0
                        if (amountInt > 0) {
                            onWaterAdded(amountInt)
                        }
                    }
                ) {
                    Text("Agregar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    private fun addWaterIntake(amount: Int, date: String, onUpdated: (HydrationRecord) -> Unit) {
        lifecycleScope.launch {
            val userEmail = sessionManager.getUserSession()
            if (userEmail != null) {
                database.hydrationDao().addWaterIntake(userEmail, date, amount)
                val updatedRecord = database.hydrationDao().getRecordForDate(userEmail, date)
                updatedRecord?.let { onUpdated(it) }
            }
        }
    }
    
    private fun setupHydrationReminders(settings: HydrationSettings) {
        if (!settings.reminderEnabled) return
        
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, HydrationReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Cancel existing alarms
        alarmManager.cancel(pendingIntent)
        
        // Set up new repeating alarm
        val intervalMillis = settings.reminderIntervalMinutes * 60 * 1000L
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, settings.reminderStartHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            intervalMillis,
            pendingIntent
        )
    }
}
