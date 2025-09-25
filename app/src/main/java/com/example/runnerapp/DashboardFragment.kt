package com.example.runnerapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.SeekBar
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.runnerapp.data.AppDatabase
import com.example.runnerapp.data.RunSession
import com.example.runnerapp.data.TrackPoint
import com.example.runnerapp.utils.CalorieCalculator
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

class DashboardFragment : Fragment(), SensorEventListener {

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

    // ===== Cronómetro general =====
    private var isRunning = false
    private var seconds = 0
    private var runStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())

    private var fitnessService: FitnessTrackingService? = null
    private var fitnessServiceBound = false
    private val fitnessServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            fitnessService = (service as FitnessTrackingService.LocalBinder).getService()
            fitnessServiceBound = true
            syncWithFitnessService()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            fitnessService = null
            fitnessServiceBound = false
        }
    }

    private var musicService: MusicService? = null
    private var musicServiceBound = false
    private val musicServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            musicService = (service as MusicService.LocalBinder).getService()
            musicServiceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            musicServiceBound = false
        }
    }

    private val fitnessServiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                FitnessTrackingService.BROADCAST_STEP_UPDATE -> {
                    val steps = intent.getIntExtra(FitnessTrackingService.EXTRA_STEPS, 0)
                    stepsThisRun = steps
                    tvSteps.text = getString(R.string.steps_label, stepsThisRun)
                }
                FitnessTrackingService.BROADCAST_LOCATION_UPDATE -> {
                    val lat = intent.getDoubleExtra(FitnessTrackingService.EXTRA_LATITUDE, 0.0)
                    val lng = intent.getDoubleExtra(FitnessTrackingService.EXTRA_LONGITUDE, 0.0)
                    routeVM.addPoint(GeoPoint(lat, lng))
                }
                FitnessTrackingService.BROADCAST_TRACKING_STATE -> {
                    val isTracking = intent.getBooleanExtra(FitnessTrackingService.EXTRA_IS_TRACKING, false)
                    val isPaused = intent.getBooleanExtra(FitnessTrackingService.EXTRA_IS_PAUSED, false)

                    if (isTracking && !isPaused && !isRunning) {
                        // Widget started tracking, sync main app
                        startMainAppTracking()
                    } else if (!isTracking && isRunning) {
                        // Widget stopped tracking, sync main app
                        stopMainAppTracking()
                    }
                }
            }
        }
    }

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

    private lateinit var swIntervals: SwitchMaterial
    private lateinit var intervalConfigGroup: View
    private var intervalsEnabled: Boolean = false

    private var warningShownThisPhase = false
    private var intRunning = false
    private val intervalHandler = Handler(Looper.getMainLooper())

    // ===== Goal Tracking =====
    private lateinit var swGoal: SwitchMaterial
    private lateinit var goalConfigGroup: View
    private lateinit var npGoalDuration: NumberPicker
    private lateinit var npGoalDistance: NumberPicker
    private lateinit var cbGoalNotify: MaterialCheckBox
    private lateinit var cbGoalAutoFinish: MaterialCheckBox

    private var goalEnabled = false
    private var goalDurationMin = 30
    private var goalDistanceKm = 5
    private var goalNotify = true
    private var goalAutoFinish = false
    private var goalReached = false
    private var lastDistanceKm = 0.0

    // ===== Audio settings =====
    private lateinit var audioManager: AudioManager
    private lateinit var swAudio: SwitchMaterial
    private lateinit var contentAudio: LinearLayout
    private lateinit var seekMusic: SeekBar
    private lateinit var seekNotif: SeekBar
    private lateinit var cbAuto: CheckBox
    private var audioEnabled = false

    private val audioPrefs by lazy {
        requireContext().getSharedPreferences("audio_prefs", Context.MODE_PRIVATE)
    }
    private var lastAppliedBucket: Int? = null

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

        const val KEY_GOAL_ENABLED = "key_goal_enabled"
        const val KEY_GOAL_DURATION = "key_goal_duration"
        const val KEY_GOAL_DISTANCE = "key_goal_distance"
        const val KEY_GOAL_NOTIFY = "key_goal_notify"
        const val KEY_GOAL_AUTOFINISH = "key_goal_autofinish"

        const val NOTIFICATION_CHANNEL_ID = "interval_notifications"
        const val NOTIF_ID_PHASE = 2221
        const val NOTIF_ID_WARNING = 2222
        const val NOTIF_ID_GOAL = 2223

        private const val PREWARN_SECONDS = 5
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
    private lateinit var tvCurrentAvgSpeed: TextView   // pace
    private lateinit var tvAvgSpeedRecord: TextView    // PB pace
    private lateinit var tvCurrentSpeed: TextView
    private lateinit var tvMaxSpeedRecord: TextView
    private lateinit var tvCaloriesBurned: TextView
    private lateinit var tvCaloriesPerMinute: TextView
    private lateinit var tvUserProfile: TextView

    private lateinit var btStart: LinearLayout
    private lateinit var btStartLabel: TextView
    private lateinit var tvGoalHud: TextView

    private var mediaPlayer: MediaPlayer? = null

    private var avgSpeedKmh: Double = 0.0

    // ===== Timer 1 Hz =====
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            seconds++
            updateChronoText()
            updateRealTimeCalories()
            updateLiveMetrics()
            checkGoalConditions()
            updateGoalHud()
            handler.postDelayed(this, 1000)
        }
    }

    // ===== Interval tick (1s) =====
    private val intervalTick = object : Runnable {
        override fun run() {
            if (!intRunning) return
            if (intervalSecLeft > 0) {
                intervalSecLeft--
                if (!warningShownThisPhase && intervalSecLeft == PREWARN_SECONDS) {
                    showIntervalWarning()
                    playWarningSound()
                    warningShownThisPhase = true
                }
            } else {
                switchPhase()
            }
            updateIntervalUI()
            intervalHandler.postDelayed(this, 1000)
        }
    }

    private lateinit var stepCounterManager: StepCounterManager
    private var stepSyncReceiver: BroadcastReceiver? = null

    // ====== onViewCreated ======
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.content_dashboard, container, false)

        stepCounterManager = StepCounterManager.getInstance(requireContext())

        createNotificationChannel()
        requestNotificationPermission()

        // Servicios
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        bindToServices()

        registerBroadcastReceivers()

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
        tvGoalHud = view.findViewById(R.id.tvGoalHud)

        // ===== Intervalos =====
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

        intervalsEnabled = prefs.getBoolean(KEY_INT_ENABLED, false)
        workMin = prefs.getInt(KEY_INT_WORK, 15).coerceIn(1, 60)
        restMin = prefs.getInt(KEY_INT_REST, 5).coerceIn(1, 60)

        swIntervals.isChecked = intervalsEnabled
        intervalConfigGroup.isVisible = intervalsEnabled
        npWorkMin.value = workMin
        npRestMin.value = restMin

        resetIntervals()
        updateIntervalUI()

        swIntervals.setOnCheckedChangeListener { _, checked ->
            intervalsEnabled = checked
            prefs.edit { putBoolean(KEY_INT_ENABLED, checked) }
            intervalConfigGroup.isVisible = checked
            if (!checked) {
                if (intRunning) pauseIntervals()
                setPickersEnabled(false)
            } else {
                requestNotificationPermission()
                setPickersEnabled(true)
            }
        }
        npWorkMin.setOnValueChangedListener { _, _, newVal ->
            workMin = newVal; prefs.edit { putInt(KEY_INT_WORK, newVal) }
            if (!intRunning) { phase = IntervalPhase.WORK; intervalSecLeft = workMin * 60; updateIntervalUI() }
        }
        npRestMin.setOnValueChangedListener { _, _, newVal ->
            restMin = newVal; prefs.edit { putInt(KEY_INT_REST, newVal) }
            if (!intRunning && phase == IntervalPhase.REST) { intervalSecLeft = restMin * 60; updateIntervalUI() }
        }
        btnIntervalStartPause.setOnClickListener {
            if (!swIntervals.isChecked) swIntervals.isChecked = true
            if (intRunning) pauseIntervals() else startIntervals()
        }
        btnIntervalReset.setOnClickListener { resetIntervals() }

        // ===== Goal Tracking =====
        swGoal = view.findViewById(R.id.swGoal)
        goalConfigGroup = view.findViewById(R.id.goalConfigGroup)
        npGoalDuration = view.findViewById(R.id.npGoalDuration)
        npGoalDistance = view.findViewById(R.id.npGoalDistance)
        cbGoalNotify = view.findViewById(R.id.cbGoalNotify)
        cbGoalAutoFinish = view.findViewById(R.id.cbGoalAutoFinish)

        goalEnabled = prefs.getBoolean(KEY_GOAL_ENABLED, false)
        goalDurationMin = prefs.getInt(KEY_GOAL_DURATION, 30)
        goalDistanceKm = prefs.getInt(KEY_GOAL_DISTANCE, 5)
        goalNotify = prefs.getBoolean(KEY_GOAL_NOTIFY, true)
        goalAutoFinish = prefs.getBoolean(KEY_GOAL_AUTOFINISH, false)

        configurePicker(npGoalDuration)
        configureKmPicker(npGoalDistance)

        swGoal.isChecked = goalEnabled
        goalConfigGroup.isVisible = goalEnabled
        npGoalDuration.value = goalDurationMin.coerceIn(1, 60)
        npGoalDistance.value = goalDistanceKm.coerceIn(1, 50)
        cbGoalNotify.isChecked = goalNotify
        cbGoalAutoFinish.isChecked = goalAutoFinish

        swGoal.setOnCheckedChangeListener { _, checked ->
            goalEnabled = checked
            prefs.edit { putBoolean(KEY_GOAL_ENABLED, checked) }
            goalConfigGroup.isVisible = checked
            if (checked) requestNotificationPermission()
            if (!checked) goalReached = false
            updateGoalHud()
        }
        npGoalDuration.setOnValueChangedListener { _, _, newVal ->
            goalDurationMin = newVal; prefs.edit { putInt(KEY_GOAL_DURATION, newVal) }; updateGoalHud()
        }
        npGoalDistance.setOnValueChangedListener { _, _, newVal ->
            goalDistanceKm = newVal; prefs.edit { putInt(KEY_GOAL_DISTANCE, newVal) }; updateGoalHud()
        }
        cbGoalNotify.setOnCheckedChangeListener { _, isChecked ->
            goalNotify = isChecked; prefs.edit { putBoolean(KEY_GOAL_NOTIFY, isChecked) }
        }
        cbGoalAutoFinish.setOnCheckedChangeListener { _, isChecked ->
            goalAutoFinish = isChecked; prefs.edit { putBoolean(KEY_GOAL_AUTOFINISH, isChecked) }
        }

        // ===== Tarjeta: Ajustes de audio =====
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        swAudio = view.findViewById(R.id.swAudio)
        contentAudio = view.findViewById(R.id.contentAudio)
        seekMusic = view.findViewById(R.id.seekMusicVolume)
        seekNotif = view.findViewById(R.id.seekNotifVolume)
        cbAuto = view.findViewById(R.id.cbAutoVolume)

        audioEnabled = audioPrefs.getBoolean("audio_enabled", false)
        swAudio.isChecked = audioEnabled
        contentAudio.isVisible = audioEnabled

        cbAuto.isChecked = audioPrefs.getBoolean("auto_volume_enabled", false)
        updateManualEnabledState(audioEnabled && !cbAuto.isChecked)

        swAudio.setOnCheckedChangeListener { _, checked ->
            audioEnabled = checked
            audioPrefs.edit { putBoolean("audio_enabled", checked) }
            contentAudio.isVisible = checked
            updateManualEnabledState(checked && !cbAuto.isChecked)
        }
        cbAuto.setOnCheckedChangeListener { _, checked ->
            audioPrefs.edit { putBoolean("auto_volume_enabled", checked) }
            updateManualEnabledState(audioEnabled && !checked)
        }

        // Volúmenes actuales del sistema
        val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        seekMusic.max = maxMusic
        seekMusic.progress = curMusic

        val maxNotif = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
        val curNotif = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        seekNotif.max = maxNotif
        seekNotif.progress = curNotif

        seekMusic.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && audioEnabled && !cbAuto.isChecked) {
                    try { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0) } catch (_: Exception) {}
                }
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
        seekNotif.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && audioEnabled && !cbAuto.isChecked) {
                    try {
                        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, progress, 0)
                        val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                        audioManager.setStreamVolume(AudioManager.STREAM_RING, progress.coerceIn(0, maxRing), 0)
                    } catch (_: Exception) {}
                }
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        // Mapa
        view.findViewById<TextView>(R.id.btnMap).setOnClickListener {
            MapBottomSheetFragment().show(parentFragmentManager, "map")
        }

        // Start/Stop y modos
        btStart.setOnClickListener { requestNotificationPermission(); toggleStartStop() }
        lyBike.setOnClickListener { setSport(Sport.BIKE) }
        lySkate.setOnClickListener { setSport(Sport.SKATE) }
        lyRun.setOnClickListener { setSport(Sport.RUN) }

        // Deporte inicial
        currentSport = intToSport(prefs.getInt(KEY_SPORT, SPORT_RUN))
        applySportUI()

        loadUserProfile()

        // Métricas iniciales
        updateMetrics(distanceKm = 0.0, recordDistanceKm = 0.5, currentSpeed = 0.0, recordMaxSpeed = 106.7)
        // CAMBIO: pace inicial en 0:00
        updatePaceLabels(avgPaceMinPerKm = 0.0, recordPaceMinPerKm = 4.5)
        updateCalorieDisplay()
        updateUserProfileDisplay()
        updateGoalHud()

        return view
    }

    override fun onResume() {
        super.onResume()

        stepSyncReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == StepCounterManager.BROADCAST_STEP_SYNC) {
                    val totalSteps = intent.getIntExtra(StepCounterManager.EXTRA_TOTAL_STEPS, 0)
                    val sessionSteps = intent.getIntExtra(StepCounterManager.EXTRA_SESSION_STEPS, 0)

                    // Update UI with synchronized steps
                    activity?.runOnUiThread {
                        tvSteps?.text = if (isRunning) sessionSteps.toString() else totalSteps.toString()
                    }
                }
            }
        }

        val filter = IntentFilter(StepCounterManager.BROADCAST_STEP_SYNC)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(stepSyncReceiver!!, filter)

        // Servicios
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onPause() {
        super.onPause()

        stepSyncReceiver?.let {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(it)
        }

        // Servicios
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun bindToServices() {
        // Bind to fitness tracking service
        val fitnessIntent = Intent(requireContext(), FitnessTrackingService::class.java)
        requireContext().bindService(fitnessIntent, fitnessServiceConnection, Context.BIND_AUTO_CREATE)

        // Bind to music service
        val musicIntent = Intent(requireContext(), MusicService::class.java)
        requireContext().bindService(musicIntent, musicServiceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun registerBroadcastReceivers() {
        val filter = IntentFilter().apply {
            addAction(FitnessTrackingService.BROADCAST_STEP_UPDATE)
            addAction(FitnessTrackingService.BROADCAST_LOCATION_UPDATE)
            addAction(FitnessTrackingService.BROADCAST_TRACKING_STATE)
        }
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(fitnessServiceReceiver, filter)
    }

    private fun syncWithFitnessService() {
        fitnessService?.let { service ->
            if (service.isCurrentlyTracking() && !service.isCurrentlyPaused()) {
                if (!isRunning) {
                    startMainAppTracking()
                }
                stepsThisRun = service.getCurrentSteps()
                tvSteps.text = getString(R.string.steps_label, stepsThisRun)
            }
        }
    }

    private fun startMainAppTracking() {
        if (isRunning) return

        isRunning = true
        btStart.setBackgroundResource(R.drawable.circle_background_topause)
        btStartLabel.text = getString(R.string.stop)

        seconds = 0
        runStartTime = System.currentTimeMillis()
        updateChronoText()

        currentCaloriesBurned = 0.0
        updateCalorieDisplay()

        routeVM.clearRoute()
        lastLoc = null

        goalReached = false
        updateGoalHud()

        // Start random music playback
        startRandomMusicPlayback()

        handler.post(timerRunnable)
    }

    private fun stopMainAppTracking() {
        if (!isRunning) return

        isRunning = false
        btStart.setBackgroundResource(R.drawable.circle_background_toplay)
        btStartLabel.text = getString(R.string.start)
        handler.removeCallbacks(timerRunnable)

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
                getString(R.string.session_saved_cal_fmt, String.format(Locale.getDefault(), "%.0f", finalCalories)),
                Toast.LENGTH_LONG
            ).show()
        }

        updateMetrics(distance / 1000.0, 20.0, 0.0, 20.0)
        updatePaceLabels(0.0, 4.5) // vuelve a 0:00
        tvGoalHud.isVisible = false
    }

    private fun startRandomMusicPlayback() {
        val musicPrefs = requireContext().getSharedPreferences("music_prefs", Context.MODE_PRIVATE)
        val savedUris = musicPrefs.getStringSet("music_uris", emptySet()) ?: emptySet()

        if (savedUris.isNotEmpty()) {
            val randomUri = savedUris.random()
            val uri = Uri.parse(randomUri)
            val fileName = uri.lastPathSegment ?: "Random Track"

            // Start music service with random track
            val musicIntent = Intent(requireContext(), MusicService::class.java).apply {
                action = MusicService.ACTION_PLAY_URI
                putExtra("uri", uri)
                putExtra("title", fileName)
            }
            ContextCompat.startForegroundService(requireContext(), musicIntent)

            Toast.makeText(
                requireContext(),
                "Reproduciendo música aleatoria: $fileName",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                requireContext(),
                "No hay música disponible. Agrega canciones desde el menú de música.",
                Toast.LENGTH_LONG
            ).show()
        }
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
            val fitnessIntent = Intent(requireContext(), FitnessTrackingService::class.java).apply {
                action = FitnessTrackingService.ACTION_START_TRACKING
            }
            ContextCompat.startForegroundService(requireContext(), fitnessIntent)

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

            goalReached = false
            updateGoalHud()

            // Start random music
            startRandomMusicPlayback()

            handler.post(timerRunnable)
        } else {
            val fitnessIntent = Intent(requireContext(), FitnessTrackingService::class.java).apply {
                action = FitnessTrackingService.ACTION_STOP_TRACKING
            }
            requireContext().startService(fitnessIntent)

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
                    getString(R.string.session_saved_cal_fmt, String.format(Locale.getDefault(), "%.0f", finalCalories)),
                    Toast.LENGTH_LONG
                ).show()
            }

            updateMetrics(distance / 1000.0, 20.0, 0.0, 20.0)
            updatePaceLabels(0.0, 4.5) // vuelve a 0:00
            tvGoalHud.isVisible = false
        }
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
        val caloriesPerMin = CalorieCalculator.getCaloriesPerMinute(
            sport = currentSport.name,
            userWeight = userWeight,
            userAge = userAge,
            userGender = userGender,
            userHeight = userHeight,
            currentSpeedKmh = avgSpeedKmh
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

    // ===== Intervalos helpers =====
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

    private fun showIntervalWarning() {
        val phaseWord = getString(
            if (phase == IntervalPhase.WORK) R.string.interval_phase_work else R.string.interval_phase_rest
        )
        showIntervalNotification(
            NOTIF_ID_WARNING,
            getString(R.string.notif_attention_title),
            getString(R.string.notif_attention_text, PREWARN_SECONDS, phaseWord)
        )
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

    // ===== Goal Tracking =====
    private fun configureKmPicker(p: NumberPicker) {
        p.minValue = 1
        p.maxValue = 50
        p.wrapSelectorWheel = false
        p.setFormatter { v -> "$v km" }
    }

    private fun checkGoalConditions() {
        if (!goalEnabled || !isRunning) return
        val timeReached = seconds >= goalDurationMin * 60
        val distanceReached = lastDistanceKm >= goalDistanceKm
        if ((timeReached || distanceReached) && !goalReached) {
            goalReached = true
            if (goalNotify) {
                showIntervalNotification(
                    NOTIF_ID_GOAL,
                    getString(R.string.goal_reached_title),
                    getString(R.string.goal_reached_text, goalDurationMin, goalDistanceKm)
                )
            } else {
                Toast.makeText(requireContext(), getString(R.string.goal_reached_title), Toast.LENGTH_LONG).show()
            }
            vibrateForPhaseChange()
            updateGoalHud()
            if (goalAutoFinish && isRunning) toggleStartStop()
        }
    }

    private fun updateGoalHud() {
        if (!goalEnabled) { tvGoalHud.isVisible = false; return }
        if (!isRunning && !goalReached) {
            tvGoalHud.text = getString(R.string.goal_hud_label, goalDurationMin, goalDistanceKm)
            tvGoalHud.isVisible = true
            return
        }
        if (goalReached) {
            tvGoalHud.text = getString(R.string.goal_hud_reached)
            tvGoalHud.isVisible = true
            return
        }
        val remSec = (goalDurationMin * 60 - seconds).coerceAtLeast(0)
        val remTime = String.format(Locale.getDefault(), "%02d:%02d", remSec / 60, remSec % 60)
        val remKm = (goalDistanceKm - lastDistanceKm).coerceAtLeast(0.0)
        tvGoalHud.text = getString(R.string.goal_hud_remaining, remTime, remKm)
        tvGoalHud.isVisible = true
    }

    // ===== Notificaciones / sonidos =====
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val p = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(requireContext(), p) != PackageManager.PERMISSION_GRANTED) {
                notificationPermLauncher.launch(p)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notificaciones de Intervalos"
            val descriptionText = "Cambios de fase y metas"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(
                    soundUri,
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }
            val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun showIntervalNotification(id: Int, title: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return
        }
        val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

    private fun playWarningSound() {
        try {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(requireContext(), soundUri)
            ringtone.play()
        } catch (_: Exception) { }
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
                setOnPreparedListener { start() }
                prepareAsync()
            }
        } catch (_: Exception) {
            try {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ring = RingtoneManager.getRingtone(requireContext(), soundUri)
                ring.play()
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
                vm?.defaultVibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            } else {
                val vibrator =
                    if (Build.VERSION.SDK_INT >= 23) requireContext().getSystemService(android.os.Vibrator::class.java)
                    else @Suppress("DEPRECATION") requireContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator

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
                sensorManager.registerListener(counterListener, stepCounter, SensorManager.SENSOR_DELAY_UI)
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
                sensorManager.registerListener(detectorListener, stepDetector, SensorManager.SENSOR_DELAY_UI)
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
                sensorManager.registerListener(accelListener, accel, SensorManager.SENSOR_DELAY_GAME)
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
        counterListener = null; detectorListener = null; accelListener = null
    }

    // ===== Distancia y métricas =====
    private fun distanceMetersOf(points: List<GeoPoint>): Double {
        var d = 0.0
        for (i in 1 until points.size) d += points[i - 1].distanceToAsDouble(points[i])
        return d
    }

    private fun updateLiveMetrics() {
        val points = routeVM.points.value ?: emptyList()
        val currentDistance = if (points.isNotEmpty()) distanceMetersOf(points) / 1000.0 else 0.0
        lastDistanceKm = currentDistance

        val currentSpeed = getCurrentSpeed()
        avgSpeedKmh = if (seconds > 0) (currentDistance / (seconds / 3600.0)) else 0.0

        val avgPaceMinPerKm = if (avgSpeedKmh > 0) 60.0 / avgSpeedKmh else 0.0

        updateMetrics(currentDistance, 20.0, currentSpeed, 106.7)
        updatePaceLabels(avgPaceMinPerKm, 4.5)

        maybeApplyAutoVolume(currentSpeed)
    }

    private fun updateMetrics(distanceKm: Double, recordDistanceKm: Double, currentSpeed: Double, recordMaxSpeed: Double) {
        tvCurrentDistance.text = String.format(Locale.getDefault(), "%.2f km", distanceKm)
        tvDistanceRecord.text = String.format(Locale.getDefault(), "%.1f km", recordDistanceKm)
        tvCurrentSpeed.text = String.format(Locale.getDefault(), "%.1f km/h", currentSpeed)
        tvMaxSpeedRecord.text = String.format(Locale.getDefault(), "%.1f km/h", recordMaxSpeed)
    }

    private fun updatePaceLabels(avgPaceMinPerKm: Double, recordPaceMinPerKm: Double) {
        tvCurrentAvgSpeed.text = if (avgPaceMinPerKm > 0) {
            val min = avgPaceMinPerKm.toInt()
            val sec = ((avgPaceMinPerKm - min) * 60).roundToInt()
            String.format(Locale.getDefault(), "%d:%02d /km", min, sec)
        } else {
            "0:00 /km"
        }

        val recMin = recordPaceMinPerKm.toInt()
        val recSec = ((recordPaceMinPerKm - recMin) * 60).roundToInt()
        tvAvgSpeedRecord.text = String.format(Locale.getDefault(), "%d:%02d /km", recMin, recSec)
    }

    private fun setSport(sport: Sport) {
        currentSport = sport
        prefs.edit { putInt(KEY_SPORT, sportToInt(sport)) }
        applySportUI()
    }

    // ===== AQUI ESTA EL CAMBIO =====
    private fun applySportUI() {
        // Colores por estado (seleccionado vs no seleccionado)
        val selectedTint   = ContextCompat.getColorStateList(requireContext(), R.color.white)            // o @color/on_primary
        val unselectedTint = ContextCompat.getColorStateList(requireContext(), R.color.text_secondary)   // gris

        // Siempre el mismo vector base (SVG) para cada deporte
        ivBike.setImageResource(R.drawable.ic_bike)
        ivSkate.setImageResource(R.drawable.ic_skate)
        ivRun.setImageResource(R.drawable.ic_run)

        // Tinte según el deporte seleccionado
        ivBike.imageTintList  = if (currentSport == Sport.BIKE)  selectedTint else unselectedTint
        ivSkate.imageTintList = if (currentSport == Sport.SKATE) selectedTint else unselectedTint
        ivRun.imageTintList   = if (currentSport == Sport.RUN)   selectedTint else unselectedTint

        // Fondo de la “pastilla” según estado
        lyBike.setBackgroundResource(
            if (currentSport == Sport.BIKE) R.drawable.mode_bg_selected else R.drawable.mode_bg_unselected
        )
        lySkate.setBackgroundResource(
            if (currentSport == Sport.SKATE) R.drawable.mode_bg_selected else R.drawable.mode_bg_unselected
        )
        lyRun.setBackgroundResource(
            if (currentSport == Sport.RUN) R.drawable.mode_bg_selected else R.drawable.mode_bg_unselected
        )
    }
    // ===== FIN DEL CAMBIO =====

    private fun configurePicker(p: NumberPicker) {
        p.minValue = 1
        p.maxValue = 60
        p.wrapSelectorWheel = false
        p.setFormatter { v -> "$v min" }
    }

    private fun fmtMinSec(totalSec: Int): String {
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    // ===== Location =====
    private fun ensureLocationPermissionAndStart() {
        val fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) startLocationUpdates()
        else locPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
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
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500L, 0f, locationListener!!)
            } else {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f, locationListener!!)
                Toast.makeText(requireContext(), getString(R.string.gps_disabled_using_network), Toast.LENGTH_SHORT).show()
            }
        } catch (_: SecurityException) { }
    }

    private fun stopLocationUpdates() {
        locationListener?.let { locationManager.removeUpdates(it) }
        locationListener = null
    }

    // ===== Audio helpers =====
    private fun updateManualEnabledState(enabled: Boolean) {
        seekMusic.isEnabled = enabled
        seekNotif.isEnabled = enabled
    }

    private fun maybeApplyAutoVolume(speedKmh: Double) {
        if (!audioEnabled || !cbAuto.isChecked) return
        val bucket = when {
            speedKmh < 3.0 -> 0   // parado/camino lento
            speedKmh < 8.0 -> 1   // trote/ciclo suave
            else -> 2             // rápido
        }
        if (bucket == lastAppliedBucket) return
        lastAppliedBucket = bucket

        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = when (bucket) {
            0 -> (max * 0.25).toInt()
            1 -> (max * 0.55).toInt()
            else -> (max * 0.80).toInt()
        }.coerceIn(0, max)

        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            seekMusic.progress = target
        } catch (_: Exception) {}
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

        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(fitnessServiceReceiver)

        if (fitnessServiceBound) {
            requireContext().unbindService(fitnessServiceConnection)
            fitnessServiceBound = false
        }

        if (musicServiceBound) {
            requireContext().unbindService(musicServiceConnection)
            musicServiceBound = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val totalSteps = event.values[0].toInt()

                stepCounterManager.updateTotalSteps(totalSteps)

                if (isRunning) {
                    if (baseSteps == -1) baseSteps = totalSteps
                    stepsThisRun = (totalSteps - baseSteps).coerceAtLeast(0)
                    stepCounterManager.updateSessionSteps(stepsThisRun)
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (!isRunning) return
                val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
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
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }
}
