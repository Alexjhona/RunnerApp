package com.example.runnerapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.runnerapp.data.AppDatabase
import com.example.runnerapp.data.RunSession
import com.example.runnerapp.data.TrackPoint
import com.example.runnerapp.utils.CalorieCalculator
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class DashboardFragment : Fragment(R.layout.content_dashboard) {

    // ===== Ruta / mapa =====
    private val routeVM: RouteViewModel by activityViewModels()

    // ===== Location =====
    private lateinit var locationManager: LocationManager
    private var locationListener: LocationListener? = null
    private var lastLoc: Location? = null

    private val MAX_ACCURACY_M = 30f
    private val MAX_SPEED_MPS = 8.0
    private val MIN_DIST_M_RUN = 6f
    private val MIN_DIST_M_SKATE = 8f
    private val MIN_DIST_M_BIKE = 10f

    // ===== Pasos =====
    private lateinit var sensorManager: SensorManager
    private var stepCounter: Sensor? = null
    private var stepDetector: Sensor? = null
    private var accel: Sensor? = null

    private enum class StepMode { COUNTER, DETECTOR, ACCEL_FALLBACK, NONE }
    private var stepMode: StepMode = StepMode.NONE

    private var baseSteps = -1
    private var stepsThisRun = 0

    private var counterListener: SensorEventListener? = null
    private var detectorListener: SensorEventListener? = null
    private var accelListener: SensorEventListener? = null

    // Fallback acelerómetro
    private var lastAccelMag = 0f
    private var highPass = 0f
    private var lastStepTimeMs = 0L
    private val STEP_DEBOUNCE_MS = 350L
    private var dynamicThresh = 1.2f

    // ===== Cronómetro general =====
    private var isRunning = false
    private var seconds = 0
    private var runStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())

    // ===== Intervalos =====
    private enum class IntervalPhase { WORK, REST }
    private var intervalsEnabled = false
    private var workMin = 15
    private var restMin = 5
    private var phase: IntervalPhase = IntervalPhase.WORK
    private var intervalSecLeft = 0
    private var currentRound = 1
    private var totalRounds = 0
    private var workTimeCompleted = 0
    private var restTimeCompleted = 0

    private lateinit var swIntervals: MaterialSwitch
    private lateinit var npWorkMin: NumberPicker
    private lateinit var npRestMin: NumberPicker
    private lateinit var tvIntervalState: TextView
    private lateinit var tvRoundCounter: TextView

    // ===== Deporte / prefs =====
    private enum class Sport { BIKE, SKATE, RUN }
    private var currentSport: Sport = Sport.RUN

    private val prefs by lazy {
        requireContext().getSharedPreferences("runner_prefs", Context.MODE_PRIVATE)
    }

    private companion object {
        const val KEY_SPORT = "key_sport"
        const val SPORT_BIKE = 0
        const val SPORT_SKATE = 1
        const val SPORT_RUN = 2

        const val KEY_INT_ENABLED = "key_int_enabled"
        const val KEY_INT_WORK = "key_int_work"
        const val KEY_INT_REST = "key_int_rest"
        const val NOTIFICATION_CHANNEL_ID = "interval_notifications"
    }

    private fun sportToInt(s: Sport) = when (s) {
        Sport.BIKE -> SPORT_BIKE
        Sport.SKATE -> SPORT_SKATE
        else -> SPORT_RUN
    }

    private fun intToSport(i: Int) = when (i) {
        SPORT_BIKE -> Sport.BIKE
        SPORT_SKATE -> Sport.SKATE
        else -> Sport.RUN
    }

    // ===== Permisos =====
    private val locPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (granted && isRunning) startLocationUpdates()
    }

    private val activityRecPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && isRunning) startStepCounting()
        if (!granted) tvSteps.text = getString(R.string.steps_perm_denied)
    }

    private val notificationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(requireContext(), "Permisos de notificación denegados. No se mostrarán alertas de intervalos.", Toast.LENGTH_LONG).show()
        }
    }

    // ===== Refs UI =====
    private lateinit var lyBike: LinearLayout
    private lateinit var lySkate: LinearLayout
    private lateinit var lyRun: LinearLayout
    private lateinit var ivBike: ImageView
    private lateinit var ivSkate: ImageView
    private lateinit var ivRun: ImageView

    private lateinit var tvChrono: TextView
    private lateinit var tvRound: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvCurrentDistance: TextView
    private lateinit var tvDistanceRecord: TextView
    private lateinit var tvCurrentAvgSpeed: TextView
    private lateinit var tvAvgSpeedRecord: TextView
    private lateinit var tvCurrentSpeed: TextView
    private lateinit var tvMaxSpeedRecord: TextView
    private lateinit var tvCaloriesBurned: TextView
    private lateinit var tvCaloriesPerMinute: TextView
    private lateinit var tvUserProfile: TextView

    private lateinit var btStart: LinearLayout
    private lateinit var btStartLabel: TextView

    private var mediaPlayer: MediaPlayer? = null

    // ===== Timer 1 Hz =====
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            seconds++
            updateChronoText()

            if (swIntervals.isChecked && intervalSecLeft > 0) {
                intervalSecLeft--
                updateIntervalUI()

                when (intervalSecLeft) {
                    10 -> {
                        showIntervalWarning(10)
                        playWarningSound()
                    }
                    5 -> {
                        showIntervalWarning(5)
                        playWarningSound()
                    }
                    3, 2, 1 -> {
                        playCountdownSound()
                    }
                }

                if (intervalSecLeft == 0) {
                    switchPhase()
                }
            }

            updateRealTimeCalories()
            updateLiveMetrics()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        createNotificationChannel()
        requestNotificationPermission()

        // Servicios
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Refs
        lyBike = view.findViewById(R.id.lySportBike)
        lySkate = view.findViewById(R.id.lySportSkate)
        lyRun = view.findViewById(R.id.lySportRun)
        ivBike = view.findViewById(R.id.ivSportBike)
        ivSkate = view.findViewById(R.id.ivSportSkate)
        ivRun = view.findViewById(R.id.ivSportRun)

        tvChrono = view.findViewById(R.id.tvChrono)
        tvRound = view.findViewById(R.id.tvRound)
        tvSteps = view.findViewById(R.id.tvSteps)
        tvCurrentDistance = view.findViewById(R.id.tvCurrentDistance)
        tvDistanceRecord = view.findViewById(R.id.tvDistanceRecord)
        tvCurrentAvgSpeed = view.findViewById(R.id.tvCurrentAvgSpeed)
        tvAvgSpeedRecord = view.findViewById(R.id.tvAvgSpeedRecord)
        tvCurrentSpeed = view.findViewById(R.id.tvCurrentSpeed)
        tvMaxSpeedRecord = view.findViewById(R.id.tvMaxSpeedRecord)
        btStart = view.findViewById(R.id.btStart)
        btStartLabel = view.findViewById(R.id.btStartLabel)

        tvCaloriesBurned = view.findViewById(R.id.tvCaloriesBurned)
        tvCaloriesPerMinute = view.findViewById(R.id.tvCaloriesPerMinute)
        tvUserProfile = view.findViewById(R.id.tvUserProfile)

        // Intervalos
        swIntervals = view.findViewById(R.id.swIntervals)
        npWorkMin = view.findViewById(R.id.npWorkMin)
        npRestMin = view.findViewById(R.id.npRestMin)
        tvIntervalState = view.findViewById(R.id.tvIntervalState)
        tvRoundCounter = view.findViewById(R.id.tvRoundCounter)

        configurePicker(npWorkMin)
        configurePicker(npRestMin)

        // Cargar prefs
        intervalsEnabled = prefs.getBoolean(KEY_INT_ENABLED, false)
        workMin = prefs.getInt(KEY_INT_WORK, 15).coerceIn(1, 180)
        restMin = prefs.getInt(KEY_INT_REST, 5).coerceIn(1, 180)

        swIntervals.isChecked = intervalsEnabled
        npWorkMin.value = workMin
        npRestMin.value = restMin
        phase = IntervalPhase.WORK
        intervalSecLeft = if (intervalsEnabled) workMin * 60 else 0
        currentRound = 1
        totalRounds = 0

        swIntervals.setOnCheckedChangeListener { _, checked ->
            intervalsEnabled = checked
            prefs.edit().putBoolean(KEY_INT_ENABLED, checked).apply()
            if (checked) {
                resetIntervalState()
            } else {
                intervalSecLeft = 0
            }
            updateIntervalUI()
        }

        npWorkMin.setOnValueChangedListener { _, _, new ->
            workMin = new
            prefs.edit().putInt(KEY_INT_WORK, new).apply()
            if (!isRunning) {
                resetIntervalState()
            } else if (phase == IntervalPhase.WORK) {
                intervalSecLeft = workMin * 60
            }
            updateIntervalUI()
        }

        npRestMin.setOnValueChangedListener { _, _, new ->
            restMin = new
            prefs.edit().putInt(KEY_INT_REST, new).apply()
            if (!isRunning) {
                resetIntervalState()
            } else if (phase == IntervalPhase.REST) {
                intervalSecLeft = restMin * 60
            }
            updateIntervalUI()
        }

        // Mapa
        view.findViewById<TextView>(R.id.btnMap).setOnClickListener {
            MapBottomSheetFragment().show(parentFragmentManager, "map")
        }

        // Start/Stop y modos
        btStart.setOnClickListener { toggleStartStop() }
        lyBike.setOnClickListener { setSport(Sport.BIKE) }
        lySkate.setOnClickListener { setSport(Sport.SKATE) }
        lyRun.setOnClickListener { setSport(Sport.RUN) }

        // Deporte inicial
        currentSport = intToSport(prefs.getInt(KEY_SPORT, SPORT_RUN))
        applySportUI()

        loadUserProfile()

        // Pinta estado inicial de intervalos
        updateIntervalUI()

        // Métricas demo
        updateMetrics(0.0, 0.5, 0.0, 65.9, 0.0, 0.0, 106.7)
        updateCalorieDisplay()
        updateUserProfileDisplay()
    }

    // ===== Start / Stop =====
    private fun toggleStartStop() {
        isRunning = !isRunning
        if (isRunning) {
            btStart.setBackgroundResource(R.drawable.circle_background_topause)
            btStartLabel.text = getString(R.string.stop)

            seconds = 0
            runStartTime = System.currentTimeMillis()
            updateChronoText()

            currentCaloriesBurned = 0.0
            updateCalorieDisplay()

            // Ruta
            routeVM.clearRoute()
            lastLoc = null

            // Pasos
            stepsThisRun = 0
            baseSteps = -1
            tvSteps.text = getString(R.string.steps_label, 0)
            ensureActivityRecPermissionAndStartSteps()

            // Intervalos
            if (swIntervals.isChecked) {
                resetIntervalState()
            } else {
                intervalSecLeft = 0
            }
            updateIntervalUI()

            // GPS
            routeVM.isTracking.postValue(true)
            ensureLocationPermissionAndStart()

            // Reloj
            handler.post(timerRunnable)
        } else {
            btStart.setBackgroundResource(R.drawable.circle_background_toplay)
            btStartLabel.text = getString(R.string.start)
            handler.removeCallbacks(timerRunnable)

            stopLocationUpdates()
            stopStepCounting()
            routeVM.isTracking.postValue(false)
            lastLoc = null

            // Guardar sesión
            val points = routeVM.points.value ?: emptyList()
            val distance = distanceMetersOf(points)

            val finalCalories = CalorieCalculator.calculateCaloriesBurned(
                sport = currentSport.name,
                durationMinutes = seconds / 60.0,
                distanceKm = distance / 1000.0,
                steps = stepsThisRun,
                userWeight = userWeight,
                userAge = userAge,
                userGender = userGender,
                userHeight = userHeight
            )

            val run = RunSession(
                startTime = runStartTime,
                endTime = System.currentTimeMillis(),
                durationSec = seconds,
                steps = stepsThisRun,
                sport = currentSport.name,
                distanceMeters = distance,
                route = points.map { TrackPoint(it.latitude, it.longitude) },
                caloriesBurned = finalCalories // Store calculated calories
            )

            viewLifecycleOwner.lifecycleScope.launch {
                val db = AppDatabase.getDatabase(requireContext())
                withContext(Dispatchers.IO) { db.runSessionDao().insert(run) }
                Toast.makeText(requireContext(),
                    "Sesión guardada: ${String.format("%.0f", finalCalories)} calorías quemadas",
                    Toast.LENGTH_LONG).show()
            }

            // UI coherente al parar
            if (swIntervals.isChecked) {
                resetIntervalState()
            }
            updateIntervalUI()

            updateMetrics(
                distance / 1000.0, 20.0,
                5.4, 12.0,
                0.0, 8.7,
                20.0
            )
        }
    }

    // ===== Calorie tracking =====
    private var currentCaloriesBurned = 0.0
    private var userWeight: Float = 70.0f
    private var userAge: Int = 30
    private var userGender: String = "Masculino"
    private var userHeight: Float = 170.0f
    private var userName: String = "Usuario"

    private fun loadUserProfile() {
        val sessionManager = SessionManager(requireContext())
        val userEmail = sessionManager.getUserSession()

        if (userEmail != null && userEmail != "guest") {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val db = AppDatabase.getDatabase(requireContext())
                    val usuario = withContext(Dispatchers.IO) {
                        db.usuarioDao().getUsuarioByEmail(userEmail)
                    }

                    usuario?.let {
                        userName = it.nombre ?: "Usuario"
                        userAge = it.edad ?: 30
                        userGender = it.genero ?: "Masculino"
                        userHeight = it.estatura ?: 170.0f
                        userWeight = it.peso ?: 70.0f
                        updateUserProfileDisplay()
                    }
                } catch (e: Exception) {
                    // Use default values if error loading profile
                }
            }
        }
    }

    private fun updateRealTimeCalories() {
        if (seconds < 30) return // Don't calculate for very short durations

        val points = routeVM.points.value ?: emptyList()
        val currentDistance = if (points.isNotEmpty()) distanceMetersOf(points) / 1000.0 else 0.0

        currentCaloriesBurned = CalorieCalculator.calculateRealTimeCalories(
            sport = currentSport.name,
            elapsedSeconds = seconds,
            currentDistanceKm = currentDistance,
            currentSteps = stepsThisRun,
            userWeight = userWeight,
            userAge = userAge,
            userGender = userGender,
            userHeight = userHeight
        )

        updateCalorieDisplay()
    }

    private fun updateCalorieDisplay() {
        tvCaloriesBurned.text = String.format("%.0f", currentCaloriesBurned)

        // Calculate calories per minute for current activity
        val currentSpeed = getCurrentSpeed()
        val caloriesPerMin = CalorieCalculator.getCaloriesPerMinute(
            sport = currentSport.name,
            userWeight = userWeight,
            userAge = userAge,
            userGender = userGender,
            userHeight = userHeight,
            currentSpeedKmh = currentSpeed
        )

        tvCaloriesPerMinute.text = String.format("%.1f/min", caloriesPerMin)
    }

    private fun updateUserProfileDisplay() {
        tvUserProfile.text = "$userName - ${userWeight.toInt()}kg"
    }

    private fun getCurrentSpeed(): Double {
        val points = routeVM.points.value ?: return 0.0
        if (points.size < 2) return 0.0

        val last = points.last()
        val prev = points[points.lastIndex - 1]
        val distanceMeters = prev.distanceToAsDouble(last)

        return distanceMeters * 3.6 // Convert m/s to km/h
    }

    private fun updateChronoText() {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        tvChrono.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }

    private fun resetIntervalState() {
        phase = IntervalPhase.WORK
        intervalSecLeft = workMin * 60
        currentRound = 1
        totalRounds = 0
        workTimeCompleted = 0
        restTimeCompleted = 0
    }

    private fun updateIntervalUI() {
        if (!intervalsEnabled || !swIntervals.isChecked) {
            tvRound.text = ""
            tvIntervalState.text = getString(R.string.intervals_off)
            tvRoundCounter.text = ""
            return
        }

        val phaseText = if (phase == IntervalPhase.WORK) "Trabajo" else "Descanso"
        tvRound.text = phaseText

        val timeStr = fmtMinSec(intervalSecLeft)
        val res = if (phase == IntervalPhase.WORK) R.string.intervals_working else R.string.intervals_resting
        tvIntervalState.text = getString(res, timeStr)

        if (totalRounds > 0) {
            tvRoundCounter.text = "Ronda $currentRound de $totalRounds"
        } else {
            tvRoundCounter.text = "Ronda $currentRound"
        }
    }

    private fun switchPhase() {
        if (!swIntervals.isChecked) return

        when (phase) {
            IntervalPhase.WORK -> {
                workTimeCompleted += workMin
                phase = IntervalPhase.REST
                intervalSecLeft = restMin * 60
                showIntervalNotification("🛑 ¡Tiempo de descanso!", "Descansa por ${restMin} minutos. ¡Bien hecho!")
                playPhaseChangeSound(false) // Rest sound
            }
            IntervalPhase.REST -> {
                restTimeCompleted += restMin
                currentRound++
                totalRounds = maxOf(totalRounds, currentRound - 1)
                phase = IntervalPhase.WORK
                intervalSecLeft = workMin * 60
                showIntervalNotification("🔥 ¡Tiempo de trabajo!", "¡Vamos! Trabaja por ${workMin} minutos")
                playPhaseChangeSound(true) // Work sound
            }
        }

        vibrateForPhaseChange()
        updateIntervalUI()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                notificationPermLauncher.launch(permission)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notificaciones de Intervalos"
            val descriptionText = "Notificaciones para cambios de fase en entrenamientos por intervalos"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(soundUri, AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build())
            }

            val notificationManager: NotificationManager =
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showIntervalNotification(title: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(requireContext(), NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_run)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 200, 250))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun showIntervalWarning(secondsLeft: Int) {
        val phaseText = if (phase == IntervalPhase.WORK) "trabajo" else "descanso"
        val emoji = if (secondsLeft <= 5) "⚠️" else "⏰"
        showIntervalNotification(
            "$emoji ¡Atención!",
            "Quedan $secondsLeft segundos de $phaseText"
        )
    }

    private fun playPhaseChangeSound(isWorkPhase: Boolean) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                val soundUri = if (isWorkPhase) {
                    // Work phase - energetic sound
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                } else {
                    // Rest phase - calmer sound
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
                setDataSource(requireContext(), soundUri)
                setAudioAttributes(AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build())
                prepare()
                start()
                setOnCompletionListener { release() }
            }
        } catch (e: Exception) {
            // Fallback to system notification sound
            try {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(requireContext(), soundUri)
                ringtone.play()
            } catch (ex: Exception) {
                // Silent fallback
            }
        }
    }

    private fun playWarningSound() {
        try {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(requireContext(), soundUri)
            ringtone.play()
        } catch (e: Exception) {
            // Silent fallback
        }
    }

    private fun playCountdownSound() {
        try {
            // Short beep for countdown
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(requireContext(), soundUri)
            ringtone.play()
        } catch (e: Exception) {
            // Silent fallback
        }
    }

    // ===== Steps =====
    private fun ensureActivityRecPermissionAndStartSteps() {
        if (Build.VERSION.SDK_INT >= 29) {
            val p = Manifest.permission.ACTIVITY_RECOGNITION
            val granted = ContextCompat.checkSelfPermission(requireContext(), p) == PackageManager.PERMISSION_GRANTED
            if (!granted) { activityRecPermLauncher.launch(p); return }
        }
        startStepCounting()
    }

    private fun startStepCounting() {
        stopStepCounting()

        when {
            stepCounter != null -> {
                stepMode = StepMode.COUNTER
                counterListener = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        if (!isRunning) return
                        val total = e.values.firstOrNull()?.toInt() ?: return
                        if (baseSteps == -1) baseSteps = total
                        stepsThisRun = (total - baseSteps).coerceAtLeast(0)
                        tvSteps.text = getString(R.string.steps_label, stepsThisRun)
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager.registerListener(
                    counterListener, stepCounter, SensorManager.SENSOR_DELAY_UI
                )
            }
            stepDetector != null -> {
                stepMode = StepMode.DETECTOR
                detectorListener = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        if (!isRunning) return
                        stepsThisRun += e.values.size
                        tvSteps.text = getString(R.string.steps_label, stepsThisRun)
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager.registerListener(
                    detectorListener, stepDetector, SensorManager.SENSOR_DELAY_UI
                )
            }
            accel != null -> {
                stepMode = StepMode.ACCEL_FALLBACK
                accelListener = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        if (!isRunning) return
                        val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
                        val mag = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                        val alpha = 0.8f
                        highPass = alpha * (highPass + mag - lastAccelMag)
                        lastAccelMag = mag

                        val now = System.currentTimeMillis()
                        if (abs(highPass) > dynamicThresh && (now - lastStepTimeMs) > STEP_DEBOUNCE_MS) {
                            lastStepTimeMs = now
                            stepsThisRun++
                            tvSteps.text = getString(R.string.steps_label, stepsThisRun)
                            dynamicThresh = (dynamicThresh * 0.9f) + (abs(highPass) * 0.1f)
                            dynamicThresh = dynamicThresh.coerceIn(1.1f, 2.0f)
                        }
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager.registerListener(
                    accelListener, accel, SensorManager.SENSOR_DELAY_GAME
                )
            }
            else -> {
                stepMode = StepMode.NONE
                tvSteps.text = getString(R.string.steps_sensor_unavailable)
            }
        }
    }

    private fun stopStepCounting() {
        counterListener?.let { sensorManager.unregisterListener(it) }
        detectorListener?.let { sensorManager.unregisterListener(it) }
        accelListener?.let { sensorManager.unregisterListener(it) }
        counterListener = null
        detectorListener = null
        accelListener = null
    }

    // ===== Distancia ruta =====
    private fun distanceMetersOf(points: List<GeoPoint>): Double {
        var d = 0.0
        for (i in 1 until points.size) d += points[i - 1].distanceToAsDouble(points[i])
        return d
    }

    // ===== Location =====
    private fun ensureLocationPermissionAndStart() {
        val fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) startLocationUpdates()
        else locPermLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun startLocationUpdates() {
        stopLocationUpdates()

        val minDist = when (currentSport) {
            Sport.RUN -> MIN_DIST_M_RUN
            Sport.SKATE -> MIN_DIST_M_SKATE
            Sport.BIKE -> MIN_DIST_M_BIKE
        }

        locationListener = LocationListener { loc ->
            if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return@LocationListener

            val prev = lastLoc
            if (prev != null) {
                val dist = loc.distanceTo(prev)
                if (dist < minDist) return@LocationListener

                val dt = (loc.time - prev.time) / 1000.0
                if (dt > 0) {
                    val speed = dist / dt
                    if (speed > MAX_SPEED_MPS) return@LocationListener
                }
            }

            routeVM.addPoint(GeoPoint(loc.latitude, loc.longitude))
            lastLoc = loc
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1500L, 0f, locationListener!!
                )
            } else {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 3000L, 0f, locationListener!!
                )
                Toast.makeText(requireContext(), "GPS desactivado: usando red (menos preciso)", Toast.LENGTH_SHORT).show()
            }
        } catch (_: SecurityException) {}
    }

    private fun stopLocationUpdates() {
        locationListener?.let { locationManager.removeUpdates(it) }
        locationListener = null
    }

    override fun onStop() {
        super.onStop()
        if (!isRunning) {
            stopLocationUpdates()
            stopStepCounting()
            handler.removeCallbacks(timerRunnable)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(timerRunnable)
        stopStepCounting()
        stopLocationUpdates()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // ===== Deporte =====
    private fun setSport(s: Sport) {
        currentSport = s
        prefs.edit().putInt(KEY_SPORT, sportToInt(s)).apply()
        applySportUI()
    }

    private fun applySportUI() {
        lyBike.setBackgroundResource(
            if (currentSport == Sport.BIKE) R.drawable.mode_bg_selected else R.drawable.mode_bg_unselected
        )
        lySkate.setBackgroundResource(
            if (currentSport == Sport.SKATE) R.drawable.mode_bg_selected else R.drawable.mode_bg_unselected
        )
        lyRun.setBackgroundResource(
            if (currentSport == Sport.RUN) R.drawable.mode_bg_selected else R.drawable.mode_bg_unselected
        )

        val white = ContextCompat.getColor(requireContext(), R.color.white)
        val gray = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        ivBike.setColorFilter(if (currentSport == Sport.BIKE) white else gray)
        ivSkate.setColorFilter(if (currentSport == Sport.SKATE) white else gray)
        ivRun.setColorFilter(if (currentSport == Sport.RUN) white else gray)
    }

    // ===== Métricas =====
    private fun fmt2(v: Double) = String.format(Locale.getDefault(), "%.2f", v)

    private fun updateMetrics(
        distanceKm: Double,
        recordDistanceKm: Double,
        avgSpeed: Double,
        recordAvgSpeed: Double,
        currentSpeed: Double,
        currentMaxInRun: Double,
        recordMaxSpeed: Double
    ) {
        tvCurrentDistance.text = fmt2(distanceKm)
        tvDistanceRecord.text = fmt2(recordDistanceKm)
        tvCurrentAvgSpeed.text = fmt2(avgSpeed)
        tvAvgSpeedRecord.text = fmt2(recordAvgSpeed)
        tvCurrentSpeed.text = fmt2(currentSpeed)
        tvMaxSpeedRecord.text = fmt2(recordMaxSpeed)
    }

    /** Métricas "live" muy simples: distancia total y vel. instantánea aprox. */
    private fun updateLiveMetrics() {
        val pts = routeVM.points.value ?: return
        if (pts.size >= 2) {
            val dMeters = distanceMetersOf(pts)
            val km = dMeters / 1000.0
            val last = pts.last()
            val prev = pts[pts.lastIndex - 1]
            val v = prev.distanceToAsDouble(last) // metros ~entre 2 fixes consecutivos
            updateMetrics(
                distanceKm = km,
                recordDistanceKm = 20.0,
                avgSpeed = 0.0,           // calcula real si quieres (d/tiempo)
                recordAvgSpeed = 12.0,
                currentSpeed = v * 3.6,   // m/s -> km/h
                currentMaxInRun = 0.0,
                recordMaxSpeed = 20.0
            )
        }
    }

    private fun vibrateForPhaseChange() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = requireContext().getSystemService(android.os.VibratorManager::class.java)
                val pattern = if (phase == IntervalPhase.WORK) {
                    // Work phase - energetic pattern
                    longArrayOf(0, 100, 50, 100, 50, 100, 50, 200)
                } else {
                    // Rest phase - calmer pattern
                    longArrayOf(0, 300, 100, 300)
                }
                vm?.defaultVibrator?.vibrate(
                    android.os.VibrationEffect.createWaveform(pattern, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                val pattern = if (phase == IntervalPhase.WORK) {
                    longArrayOf(0, 100, 50, 100, 50, 100, 50, 200)
                } else {
                    longArrayOf(0, 300, 100, 300)
                }
                vibrator.vibrate(pattern, -1)
            }
        } catch (_: Exception) { }
    }

    private fun configurePicker(p: NumberPicker) {
        p.minValue = 1
        p.maxValue = 60 // More reasonable max for intervals
        p.wrapSelectorWheel = false
        p.setFormatter { v -> "$v min" }
    }

    private fun fmtMinSec(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
