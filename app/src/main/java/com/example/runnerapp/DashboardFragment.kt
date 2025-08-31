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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.util.Locale

class DashboardFragment : Fragment(R.layout.content_dashboard) {

    // ===== ViewModel compartido (mapa) =====
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
    private var stepDetector: Sensor? = null
    private var stepListener: SensorEventListener? = null
    private var stepsThisRun = 0

    // ===== Cronómetro =====
    private var isRunning = false
    private var seconds = 0
    private var runStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                seconds++
                updateChronoText()
                handler.postDelayed(this, 1000)
            }
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

    // ===== Refs UI =====
    private lateinit var lyBike: LinearLayout
    private lateinit var lySkate: LinearLayout
    private lateinit var lyRun: LinearLayout
    private lateinit var ivBike: ImageView
    private lateinit var ivSkate: ImageView
    private lateinit var ivRun: ImageView

    private lateinit var tvChrono: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvCurrentDistance: TextView
    private lateinit var tvDistanceRecord: TextView
    private lateinit var tvCurrentAvgSpeed: TextView
    private lateinit var tvAvgSpeedRecord: TextView
    private lateinit var tvCurrentSpeed: TextView
    private lateinit var tvMaxSpeedRecord: TextView

    private lateinit var btStart: LinearLayout
    private lateinit var btStartLabel: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Servicios
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        // Refs UI
        lyBike  = view.findViewById(R.id.lySportBike)
        lySkate = view.findViewById(R.id.lySportSkate)
        lyRun   = view.findViewById(R.id.lySportRun)
        ivBike  = view.findViewById(R.id.ivSportBike)
        ivSkate = view.findViewById(R.id.ivSportSkate)
        ivRun   = view.findViewById(R.id.ivSportRun)

        tvChrono           = view.findViewById(R.id.tvChrono)
        tvSteps            = view.findViewById(R.id.tvSteps)
        tvCurrentDistance  = view.findViewById(R.id.tvCurrentDistance)
        tvDistanceRecord   = view.findViewById(R.id.tvDistanceRecord)
        tvCurrentAvgSpeed  = view.findViewById(R.id.tvCurrentAvgSpeed)
        tvAvgSpeedRecord   = view.findViewById(R.id.tvAvgSpeedRecord)
        tvCurrentSpeed     = view.findViewById(R.id.tvCurrentSpeed)
        tvMaxSpeedRecord   = view.findViewById(R.id.tvMaxSpeedRecord)

        btStart      = view.findViewById(R.id.btStart)
        btStartLabel = view.findViewById(R.id.btStartLabel)

        // Botón mapa (BottomSheet)
        view.findViewById<TextView>(R.id.btnMap).setOnClickListener {
            MapBottomSheetFragment().show(parentFragmentManager, "map")
        }

        // Start/Stop + modos
        btStart.setOnClickListener { toggleStartStop() }
        lyBike.setOnClickListener { setSport(Sport.BIKE) }
        lySkate.setOnClickListener { setSport(Sport.SKATE) }
        lyRun.setOnClickListener { setSport(Sport.RUN) }

        // Estado inicial
        currentSport = intToSport(prefs.getInt(KEY_SPORT, SPORT_RUN))
        applySportUI()

        // Mock inicial (si quieres)
        updateMetrics(
            distanceKm       = 0.0,
            recordDistanceKm = 0.5,
            avgSpeed         = 0.0,
            recordAvgSpeed   = 65.9,
            currentSpeed     = 0.0,
            currentMaxInRun  = 0.0,
            recordMaxSpeed   = 106.7
        )
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
            handler.post(timerRunnable)

            // tracking ON
            routeVM.clearRoute()
            lastLoc = null
            stepsThisRun = 0
            tvSteps.text = "PASOS: 0"
            startStepCounting()

            routeVM.isTracking.postValue(true)
            ensureLocationPermissionAndStart()
        } else {
            btStart.setBackgroundResource(R.drawable.circle_background_toplay)
            btStartLabel.text = getString(R.string.start)
            handler.removeCallbacks(timerRunnable)

            // tracking OFF
            stopLocationUpdates()
            stopStepCounting()
            routeVM.isTracking.postValue(false)
            lastLoc = null

            // Guardar sesión
            val pts = routeVM.points.value ?: emptyList()
            val distance = distanceMetersOf(pts)
            val run = RunSession(
                startTime = runStartTime,
                endTime = System.currentTimeMillis(),
                durationSec = seconds,
                steps = stepsThisRun,
                sport = currentSport.name,
                distanceMeters = distance,
                route = pts.map { TrackPoint(it.latitude, it.longitude) }
            )
            viewLifecycleOwner.lifecycleScope.launch {
                val db = AppDatabase.getDatabase(requireContext())
                withContext(Dispatchers.IO) { db.runSessionDao().insert(run) }
                Toast.makeText(requireContext(), "Sesión guardada en Historial", Toast.LENGTH_SHORT).show()
            }

            // (opcional) refrescar métricas demo
            updateMetrics(
                distanceKm       = distance / 1000.0,
                recordDistanceKm = 20.0,
                avgSpeed         = 5.4,
                recordAvgSpeed   = 12.0,
                currentSpeed     = 0.0,
                currentMaxInRun  = 8.7,
                recordMaxSpeed   = 20.0
            )
        }
    }

    private fun updateChronoText() {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        tvChrono.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
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
        val gray  = ContextCompat.getColor(requireContext(), R.color.text_secondary)

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
        tvDistanceRecord.text  = fmt2(recordDistanceKm)
        tvCurrentAvgSpeed.text = fmt2(avgSpeed)
        tvAvgSpeedRecord.text  = fmt2(recordAvgSpeed)
        tvCurrentSpeed.text    = fmt2(currentSpeed)
        tvMaxSpeedRecord.text  = fmt2(recordMaxSpeed)
    }

    // ===== Steps =====
    private fun startStepCounting() {
        if (stepDetector == null) return
        stepListener?.let { sensorManager.unregisterListener(it) }
        stepListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!isRunning) return
                if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                    stepsThisRun += event.values.firstOrNull()?.toInt() ?: 1
                    tvSteps.text = "PASOS: $stepsThisRun"
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(stepListener, stepDetector, SensorManager.SENSOR_DELAY_NORMAL)
    }
    private fun stopStepCounting() {
        stepListener?.let { sensorManager.unregisterListener(it) }
        stepListener = null
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
        else locPermLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    private fun startLocationUpdates() {
        stopLocationUpdates()

        val minDist = when (currentSport) {
            Sport.RUN   -> MIN_DIST_M_RUN
            Sport.SKATE -> MIN_DIST_M_SKATE
            Sport.BIKE  -> MIN_DIST_M_BIKE
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
        }
    }
}
