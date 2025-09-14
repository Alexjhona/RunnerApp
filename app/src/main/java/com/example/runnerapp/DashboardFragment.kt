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
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.runnerapp.data.AppDatabase
import com.example.runnerapp.data.RunSession
import com.example.runnerapp.data.TrackPoint
import com.example.runnerapp.utils.CalorieCalculator
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class DashboardFragment : Fragment(R.layout.content_dashboard) {

    // ===== Ruta / VM =====
    private val routeVM: RouteViewModel by activityViewModels()

    // ===== Location =====
    private lateinit var locationManager: LocationManager
    private var locationListener: LocationListener? = null
    private var lastLoc: Location? = null

    private val maxAccuracyM = 30f
    private val maxSpeedMps = 8.0
    private val minDistMRun = 6f
    private val minDistMSkate = 8f
    private val minDistMBike = 10f

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
    private val stepDebounceMs = 350L
    private var dynamicThresh = 1.2f

    // ===== Cronómetro general (botón redondo) =====
    private var isRunning = false
    private var seconds = 0
    private var runStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())

    // ===== Intervalos =====
    private enum class IntervalPhase { WORK, REST }
    private var workMin = 15
    private var restMin = 5
    private var phase: IntervalPhase = IntervalPhase.WORK
    private var intervalSecLeft = 0
    private var currentRound = 1

    private lateinit var npWorkMin: NumberPicker
    private lateinit var npRestMin: NumberPicker
    private lateinit var tvIntervalTimer: TextView
    private lateinit var tvIntervalPhase: TextView
    private lateinit var tvIntervalRound: TextView
    private lateinit var btnIntervalStartPause: MaterialButton
    private lateinit var btnIntervalReset: MaterialButton

    // Switch y contenedor colapsable
    private lateinit var swIntervals: SwitchMaterial
    private lateinit var intervalConfigGroup: View
    private var intervalsEnabled: Boolean = false

    private var warningShownThisPhase = false
    private var intRunning = false
    private val intervalHandler = Handler(Looper.getMainLooper())
    private val intervalTick = object : Runnable {
        override fun run() {
            if (!intRunning) return
            if (intervalSecLeft > 0) {
                intervalSecLeft--
                if (!warningShownThisPhase && intervalSecLeft == 5) {
                    showIntervalWarning(5)
                    warningShownThisPhase = true
                    playWarningSound()
                }
            } else {
                switchPhase()
            }
            updateIntervalUI()
            intervalHandler.postDelayed(this, 1000)
        }
    }

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
        const val NOTIF_ID_PHASE = 2221
        const val NOTIF_ID_WARNING = 2222
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
            Toast.makeText(
                requireContext(),
                getString(R.string.notif_permission_denied_intervals),
                Toast.LENGTH_LONG
            ).show()
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

    // ===== Timer 1 Hz del cronómetro general =====
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            seconds++
            updateChronoText()
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
        tvCaloriesBurned = view.findViewById(R.id.tvCaloriesBurned)
        tvCaloriesPerMinute = view.findViewById(R.id.tvCaloriesPerMinute)
        tvUserProfile = view.findViewById(R.id.tvUserProfile)

        btStart = view.findViewById(R.id.btStart)
        btStartLabel = view.findViewById(R.id.btStartLabel)

        // ====== INTERVALOS ======
        swIntervals = view.findViewById(R.id.swIntervals)
        intervalConfigGroup = view.findViewById(R.id.intervalConfigGroup)

        npWorkMin = view.findViewById(R.id.npWorkMin)
        npRestMin = view.findViewById(R.id.npRestMin)
        tvIntervalTimer = view.findViewById(R.id.tvIntervalTimer)
        tvIntervalPhase = view.findViewById(R.id.tvIntervalPhase)
        tvIntervalRound = view.findViewById(R.id.tvIntervalRound)
        btnIntervalStartPause = view.findViewById(R.id.btnIntervalStartPause)
        btnIntervalReset = view.findViewById(R.id.btnIntervalReset)

        configurePicker(npWorkMin)
        configurePicker(npRestMin)

        // Cargar prefs
        intervalsEnabled = prefs.getBoolean(KEY_INT_ENABLED, false)
        workMin = prefs.getInt(KEY_INT_WORK, 15).coerceIn(1, 60)
        restMin = prefs.getInt(KEY_INT_REST, 5).coerceIn(1, 60)

        swIntervals.isChecked = intervalsEnabled
        intervalConfigGroup.isVisible = intervalsEnabled

        npWorkMin.value = workMin
        npRestMin.value = restMin

        resetIntervals()
        updateIntervalUI()

        // Listener del switch (colapsable)
        swIntervals.setOnCheckedChangeListener { _, checked ->
            intervalsEnabled = checked
            prefs.edit { putBoolean(KEY_INT_ENABLED, checked) }
            intervalConfigGroup.isVisible = checked
            if (!checked) {
                if (intRunning) pauseIntervals()
                setPickersEnabled(false)
            } else {
                setPickersEnabled(true)
            }
        }

        // Listeners de intervalos
        npWorkMin.setOnValueChangedListener { _, _, newVal ->
            workMin = newVal
            prefs.edit { putInt(KEY_INT_WORK, newVal) }
            if (!intRunning) {
                phase = IntervalPhase.WORK
                intervalSecLeft = workMin * 60
                updateIntervalUI()
            }
        }
        npRestMin.setOnValueChangedListener { _, _, newVal ->
            restMin = newVal
            prefs.edit { putInt(KEY_INT_REST, newVal) }
            if (!intRunning && phase == IntervalPhase.REST) {
                intervalSecLeft = restMin * 60
                updateIntervalUI()
            }
        }
        btnIntervalStartPause.setOnClickListener {
            if (!swIntervals.isChecked) swIntervals.isChecked = true
            if (intRunning) pauseIntervals() else startIntervals()
        }
        btnIntervalReset.setOnClickListener { resetIntervals() }

        // Mapa
        view.findViewById<TextView>(R.id.btnMap).setOnClickListener {
            MapBottomSheetFragment().show(parentFragmentManager, "map")
        }

        // Start/Stop general y modos
        btStart.setOnClickListener { toggleStartStop() }
        lyBike.setOnClickListener { setSport(Sport.BIKE) }
        lySkate.setOnClickListener { setSport(Sport.SKATE) }
        lyRun.setOnClickListener { setSport(Sport.RUN) }

        // Deporte inicial
        currentSport = intToSport(prefs.getInt(KEY_SPORT, SPORT_RUN))
        applySportUI()

        loadUserProfile()

        // Métricas demo iniciales
        updateMetrics(0.0, 0.5, 0.0, 65.9, 0.0, 106.7)
        updateCalorieDisplay()
        updateUserProfileDisplay()
    }

    // ===== Start / Stop general =====
    private var currentCaloriesBurned = 0.0
    private var userWeight: Float = 70.0f
    private var userAge: Int = 30
    private var userGender: String = "Masculino"
    private var userHeight: Float = 170.0f
    private var userName: String = "Usuario"

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

            routeVM.clearRoute()
            lastLoc = null

            stepsThisRun = 0
            baseSteps = -1
            tvSteps.text = getString(R.string.steps_label, 0)
            ensureActivityRecPermissionAndStartSteps()

            routeVM.isTracking.postValue(true)
            ensureLocationPermissionAndStart()

            handler.post(timerRunnable)
        } else {
            btStart.setBackgroundResource(R.drawable.circle_background_toplay)
            btStartLabel.text = getString(R.string.start)
            handler.removeCallbacks(timerRunnable)

            stopLocationUpdates()
            stopStepCounting()
            routeVM.isTracking.postValue(false)
            lastLoc = null

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
                caloriesBurned = finalCalories
            )

            viewLifecycleOwner.lifecycleScope.launch {
                val db = AppDatabase.getDatabase(requireContext())
                withContext(Dispatchers.IO) { db.runSessionDao().insert(run) }
                Toast.makeText(
                    requireContext(),
                    getString(
                        R.string.session_saved_cal_fmt,
                        String.format(Locale.getDefault(), "%.0f", finalCalories)
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }

            updateMetrics(
                distance / 1000.0, 20.0,
                5.4, 12.0,
                0.0, 20.0
            )
        }
    }

    private fun playWarningSound() {
        try {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(requireContext(), soundUri)
            ringtone.play()
        } catch (_: Exception) { }
    }

    // ===== Perfil / Calorías =====
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
                } catch (_: Exception) { }
            }
        }
    }

    private fun updateRealTimeCalories() {
        if (seconds < 30) return
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
        tvCaloriesBurned.text = String.format(Locale.getDefault(), "%.0f", currentCaloriesBurned)
        val currentSpeed = getCurrentSpeed()
        val caloriesPerMin = CalorieCalculator.getCaloriesPerMinute(
            sport = currentSport.name,
            userWeight = userWeight,
            userAge = userAge,
            userGender = userGender,
            userHeight = userHeight,
            currentSpeedKmh = currentSpeed
        )
        tvCaloriesPerMinute.text = String.format(Locale.getDefault(), "%.1f/min", caloriesPerMin)
    }

    private fun updateUserProfileDisplay() {
        tvUserProfile.text = getString(R.string.user_profile_format, userName, userWeight.toInt())
    }

    private fun getCurrentSpeed(): Double {
        val points = routeVM.points.value ?: return 0.0
        if (points.size < 2) return 0.0
        val last = points.last()
        val prev = points[points.lastIndex - 1]
        val distanceMeters = prev.distanceToAsDouble(last)
        return distanceMeters * 3.6 // m/s -> km/h
    }

    private fun updateChronoText() {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        tvChrono.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }

    // ===== Intervalos: helpers =====
    private fun startIntervals() {
        if (intervalSecLeft <= 0) {
            phase = IntervalPhase.WORK
            intervalSecLeft = workMin * 60
            currentRound = 1
        }
        warningShownThisPhase = false
        intRunning = true
        btnIntervalStartPause.text = getString(R.string.intervals_pause)
        setPickersEnabled(false)
        updateIntervalUI()
        intervalHandler.removeCallbacks(intervalTick)
        intervalHandler.post(intervalTick)
    }

    private fun pauseIntervals() {
        intRunning = false
        btnIntervalStartPause.text = getString(R.string.intervals_resume)
        intervalHandler.removeCallbacks(intervalTick)
        setPickersEnabled(true)
    }

    private fun resetIntervals() {
        intRunning = false
        intervalHandler.removeCallbacks(intervalTick)
        phase = IntervalPhase.WORK
        intervalSecLeft = workMin * 60
        currentRound = 1
        warningShownThisPhase = false
        btnIntervalStartPause.text = getString(R.string.intervals_start)
        setPickersEnabled(true)
        updateIntervalUI()
    }

    private fun setPickersEnabled(enabled: Boolean) {
        npWorkMin.isEnabled = enabled
        npRestMin.isEnabled = enabled
    }

    private fun updateIntervalUI() {
        tvIntervalTimer.text = fmtMinSec(intervalSecLeft)
        tvIntervalPhase.text = getString(
            if (phase == IntervalPhase.WORK) R.string.interval_phase_work else R.string.interval_phase_rest
        )
        tvIntervalRound.text = getString(R.string.interval_round_format, currentRound)
    }

    private fun switchPhase() {
        when (phase) {
            IntervalPhase.WORK -> {
                phase = IntervalPhase.REST
                intervalSecLeft = restMin * 60
                warningShownThisPhase = false
                showIntervalNotification(
                    NOTIF_ID_PHASE,
                    getString(R.string.notif_rest_title),
                    getString(R.string.notif_rest_text, restMin)
                )
                playPhaseChangeSound(false)
            }
            IntervalPhase.REST -> {
                currentRound++
                phase = IntervalPhase.WORK
                intervalSecLeft = workMin * 60
                warningShownThisPhase = false
                showIntervalNotification(
                    NOTIF_ID_PHASE,
                    getString(R.string.notif_work_title),
                    getString(R.string.notif_work_text, workMin)
                )
                playPhaseChangeSound(true)
            }
        }
        vibrateForPhaseChange()
    }

    // ===== Notificaciones / sonidos =====
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermLauncher.launch(permission)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notificaciones de Intervalos"
            val descriptionText = "Cambios de fase en entrenamientos por intervalos"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel =
                NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableVibration(true)
                    val soundUri =
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    setSound(
                        soundUri, AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                }
            val nm =
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun showIntervalNotification(id: Int, title: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                )
                != PackageManager.PERMISSION_GRANTED
            ) return
        }
        val nm =
            requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(requireContext(), NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_run)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 200, 250))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        nm.notify(id, n)
    }

    private fun showIntervalWarning(secondsLeft: Int) {
        val phaseWord = getString(
            if (phase == IntervalPhase.WORK) R.string.interval_phase_work else R.string.interval_phase_rest
        )
        showIntervalNotification(
            NOTIF_ID_WARNING,
            getString(R.string.notif_attention_title),
            getString(R.string.notif_attention_text, secondsLeft, phaseWord)
        )
    }

    private fun playPhaseChangeSound(isWorkPhase: Boolean) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                val soundUri = if (isWorkPhase)
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                else
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setDataSource(requireContext(), soundUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                prepare()
                start()
                setOnCompletionListener { release() }
            }
        } catch (_: Exception) {
            try {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(requireContext(), soundUri)
                ringtone.play()
            } catch (_: Exception) { }
        }
    }

    private fun vibrateForPhaseChange() {
        try {
            val pattern = if (phase == IntervalPhase.WORK) {
                longArrayOf(0, 100, 50, 100, 50, 100, 50, 200)
            } else {
                longArrayOf(0, 300, 100, 300)
            }

            if (Build.VERSION.SDK_INT >= 31) {
                val vm = requireContext().getSystemService(android.os.VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(
                    android.os.VibrationEffect.createWaveform(pattern, -1)
                )
            } else {
                val vibrator = if (Build.VERSION.SDK_INT >= 23) {
                    requireContext().getSystemService(android.os.Vibrator::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    requireContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                }

                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (_: Exception) { }
    }

    // ===== Steps =====
    private fun ensureActivityRecPermissionAndStartSteps() {
        if (Build.VERSION.SDK_INT >= 29) {
            val p = Manifest.permission.ACTIVITY_RECOGNITION
            val granted =
                ContextCompat.checkSelfPermission(requireContext(), p) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                activityRecPermLauncher.launch(p); return
            }
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
                        if (abs(highPass) > dynamicThresh && (now - lastStepTimeMs) > stepDebounceMs) {
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
        val fine =
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse =
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
            Sport.RUN -> minDistMRun
            Sport.SKATE -> minDistMSkate
            Sport.BIKE -> minDistMBike
        }

        locationListener = LocationListener { loc ->
            if (loc.hasAccuracy() && loc.accuracy > maxAccuracyM) return@LocationListener

            val prev = lastLoc
            if (prev != null) {
                val dist = loc.distanceTo(prev)
                if (dist < minDist) return@LocationListener

                val dt = (loc.time - prev.time) / 1000.0
                if (dt > 0) {
                    val speed = dist / dt
                    if (speed > maxSpeedMps) return@LocationListener
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
                Toast.makeText(
                    requireContext(),
                    getString(R.string.gps_disabled_using_network),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (_: SecurityException) { }
    }

    private fun stopLocationUpdates() {
        locationListener?.let { locationManager.removeUpdates(it) }
        locationListener = null
    }

    // ===== Deporte =====
    private fun setSport(s: Sport) {
        currentSport = s
        prefs.edit { putInt(KEY_SPORT, sportToInt(s)) }
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
        recordMaxSpeed: Double
    ) {
        tvCurrentDistance.text = fmt2(distanceKm)
        tvDistanceRecord.text = fmt2(recordDistanceKm)
        tvCurrentAvgSpeed.text = fmt2(avgSpeed)
        tvAvgSpeedRecord.text = fmt2(recordAvgSpeed)
        tvCurrentSpeed.text = fmt2(currentSpeed)
        tvMaxSpeedRecord.text = fmt2(recordMaxSpeed)
    }

    private fun updateLiveMetrics() {
        val pts = routeVM.points.value ?: return
        if (pts.size >= 2) {
            val dMeters = distanceMetersOf(pts)
            val km = dMeters / 1000.0
            val last = pts.last()
            val prev = pts[pts.lastIndex - 1]
            val v = prev.distanceToAsDouble(last)
            updateMetrics(
                distanceKm = km,
                recordDistanceKm = 20.0,
                avgSpeed = 0.0,
                recordAvgSpeed = 12.0,
                currentSpeed = v * 3.6,
                recordMaxSpeed = 20.0
            )
        }
    }

    // ===== Util =====
    private fun configurePicker(p: NumberPicker) {
        p.minValue = 1
        p.maxValue = 60
        p.wrapSelectorWheel = false
        p.setFormatter { v -> "$v min" }
    }

    private fun fmtMinSec(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    // ===== Ciclo de vida =====
    override fun onStop() {
        super.onStop()
        if (!isRunning) {
            stopLocationUpdates()
            stopStepCounting()
            handler.removeCallbacks(timerRunnable)
        }
        intervalHandler.removeCallbacks(intervalTick)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(timerRunnable)
        intervalHandler.removeCallbacks(intervalTick)
        stopStepCounting()
        stopLocationUpdates()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
