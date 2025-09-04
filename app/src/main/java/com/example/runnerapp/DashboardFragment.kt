    package com.example.runnerapp

    import android.Manifest
    import android.content.Context
    import android.content.pm.PackageManager
    import android.hardware.Sensor
    import android.hardware.SensorEvent
    import android.hardware.SensorEventListener
    import android.hardware.SensorManager
    import android.location.Location
    import android.location.LocationListener
    import android.location.LocationManager
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
    import androidx.core.content.ContextCompat
    import androidx.fragment.app.Fragment
    import androidx.fragment.app.activityViewModels
    import androidx.lifecycle.lifecycleScope
    import com.example.runnerapp.data.AppDatabase
    import com.example.runnerapp.data.RunSession
    import com.example.runnerapp.data.TrackPoint
    import com.google.android.material.materialswitch.MaterialSwitch
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.withContext
    import org.osmdroid.util.GeoPoint
    import java.util.Locale
    import kotlin.math.abs
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

        private lateinit var swIntervals: MaterialSwitch
        private lateinit var npWorkMin: NumberPicker
        private lateinit var npRestMin: NumberPicker
        private lateinit var tvIntervalState: TextView

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
        }

        private fun sportToInt(s: Sport) = when (s) {
            Sport.BIKE -> SPORT_BIKE
            Sport.SKATE -> SPORT_SKATE
            Sport.RUN -> SPORT_RUN
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

        private lateinit var btStart: LinearLayout
        private lateinit var btStartLabel: TextView

        // ===== Timer 1 Hz =====
        private val timerRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                seconds++
                updateChronoText()

                // Intervalos
                if (swIntervals.isChecked) { // <— sincronizado con el switch
                    if (intervalSecLeft > 0) {
                        intervalSecLeft--
                        updateIntervalUI()
                    }
                    if (intervalSecLeft == 0) switchPhase()
                }

                // Métricas “live”
                updateLiveMetrics()

                handler.postDelayed(this, 1000)
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

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

            // Intervalos
            swIntervals = view.findViewById(R.id.swIntervals)
            npWorkMin = view.findViewById(R.id.npWorkMin)
            npRestMin = view.findViewById(R.id.npRestMin)
            tvIntervalState = view.findViewById(R.id.tvIntervalState)

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

            // Listeners intervalos
            swIntervals.setOnCheckedChangeListener { _, checked ->
                intervalsEnabled = checked
                prefs.edit().putBoolean(KEY_INT_ENABLED, checked).apply()
                if (checked) {
                    phase = IntervalPhase.WORK
                    intervalSecLeft = workMin * 60
                }
                updateIntervalUI()
            }
            npWorkMin.setOnValueChangedListener { _, _, new ->
                workMin = new
                prefs.edit().putInt(KEY_INT_WORK, new).apply()
                if (!isRunning || phase == IntervalPhase.WORK) intervalSecLeft = workMin * 60
                updateIntervalUI()
            }
            npRestMin.setOnValueChangedListener { _, _, new ->
                restMin = new
                prefs.edit().putInt(KEY_INT_REST, new).apply()
                if (!isRunning || phase == IntervalPhase.REST) intervalSecLeft = restMin * 60
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

            // Pinta estado inicial de intervalos
            updateIntervalUI()

            // Métricas demo
            updateMetrics(0.0, 0.5, 0.0, 65.9, 0.0, 0.0, 106.7)
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
                    phase = IntervalPhase.WORK
                    intervalSecLeft = workMin * 60
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
                val run = RunSession(
                    startTime = runStartTime,
                    endTime = System.currentTimeMillis(),
                    durationSec = seconds,
                    steps = stepsThisRun,
                    sport = currentSport.name,
                    distanceMeters = distance,
                    route = points.map { TrackPoint(it.latitude, it.longitude) }
                )
                viewLifecycleOwner.lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(requireContext())
                    withContext(Dispatchers.IO) { db.runSessionDao().insert(run) }
                    Toast.makeText(requireContext(), "Sesión guardada en Historial", Toast.LENGTH_SHORT).show()
                }

                // UI coherente al parar
                phase = IntervalPhase.WORK
                intervalSecLeft = if (swIntervals.isChecked) workMin * 60 else 0
                updateIntervalUI()

                updateMetrics(
                    distance / 1000.0, 20.0,
                    5.4, 12.0,
                    0.0, 8.7,
                    20.0
                )
            }
        }

        private fun updateChronoText() {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            tvChrono.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
        }

        // ===== Intervalos =====
        private fun updateIntervalUI() {
            // Fuerza sincronía con el switch (evita “siempre desactivados”)
            intervalsEnabled = swIntervals.isChecked

            if (!intervalsEnabled) {
                tvRound.text = ""
                tvIntervalState.text = getString(R.string.intervals_off)
                return
            }
            tvRound.text = if (phase == IntervalPhase.WORK) "Trabajo" else "Descanso"
            val timeStr = fmtMinSec(intervalSecLeft)
            val res = if (phase == IntervalPhase.WORK) R.string.intervals_working else R.string.intervals_resting
            tvIntervalState.text = getString(res, timeStr)
        }

        private fun switchPhase() {
            if (!swIntervals.isChecked) return
            vibrateShort()
            phase = if (phase == IntervalPhase.WORK) IntervalPhase.REST else IntervalPhase.WORK
            intervalSecLeft = if (phase == IntervalPhase.WORK) workMin * 60 else restMin * 60
            updateIntervalUI()
        }

        private fun fmtMinSec(sec: Int): String {
            val m = sec / 60
            val s = sec % 60
            return String.format(Locale.getDefault(), "%02d:%02d", m, s)
        }

        private fun vibrateShort() {
            try {
                if (Build.VERSION.SDK_INT >= 31) {
                    val vm = requireContext().getSystemService(android.os.VibratorManager::class.java)
                    vm?.defaultVibrator?.vibrate(
                        android.os.VibrationEffect.createOneShot(250, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    (requireContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator)
                        .vibrate(250)
                }
            } catch (_: Exception) { }
        }

        private fun configurePicker(p: NumberPicker) {
            p.minValue = 1
            p.maxValue = 180
            p.wrapSelectorWheel = false
            p.setFormatter { v -> "$v min" }
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

        /** Métricas “live” muy simples: distancia total y vel. instantánea aprox. */
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
        }
    }
