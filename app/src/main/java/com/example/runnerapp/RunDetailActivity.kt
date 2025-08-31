package com.example.runnerapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.runnerapp.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

class RunDetailActivity : AppCompatActivity() {

    private lateinit var map: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_run_detail)

        // Requerido por osmdroid
        Configuration.getInstance().userAgentValue = packageName

        map = findViewById(R.id.osmMapDetail)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(16.0)

        val runId = intent.getIntExtra("run_id", -1)
        if (runId == -1) {
            Toast.makeText(this, "Id de carrera inválido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            val run = withContext(Dispatchers.IO) { db.runSessionDao().getById(runId) }
            if (run == null) {
                Toast.makeText(this@RunDetailActivity, "Carrera no encontrada", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            val pts = run.route.map { GeoPoint(it.lat, it.lon) }
            if (pts.isEmpty()) {
                Toast.makeText(this@RunDetailActivity, "Esta carrera no tiene ruta guardada", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Dibuja la ruta
            val poly = Polyline().apply {
                setPoints(pts)
                outlinePaint.strokeWidth = 10f
                // color por defecto de osmdroid; si quieres usa ContextCompat.getColor(...)
            }
            map.overlays.add(poly)
            map.controller.setCenter(pts.last())
            map.invalidate()
        }
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { map.onPause(); super.onPause() }
}
