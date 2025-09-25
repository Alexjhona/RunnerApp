package com.example.runnerapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapBottomSheetFragment : BottomSheetDialogFragment() {

    private var map: MapView? = null
    private var myLoc: MyLocationNewOverlay? = null
    private var polyline: Polyline? = null
    private val routeVM: RouteViewModel by activityViewModels()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { enableMyLocationIfPermitted() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_map_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // user-agent requerido por osmdroid
        Configuration.getInstance().userAgentValue = requireContext().packageName

        map = view.findViewById(R.id.osmMap)
        map?.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
        }

        // Polyline (más suave)
        polyline = Polyline().apply {
            outlinePaint.color =
                ContextCompat.getColor(requireContext(), android.R.color.holo_blue_light)
            outlinePaint.strokeWidth = 10f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.isAntiAlias = true
        }
        map?.overlays?.add(polyline)

        // Mi ubicación
        myLoc = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), map).apply {
            enableMyLocation()
            enableFollowLocation()
        }
        map?.overlays?.add(myLoc)

        enableMyLocationIfPermitted()

        // Observa puntos y redibuja
        routeVM.points.observe(viewLifecycleOwner) { pts ->
            polyline?.setPoints(pts)
            pts.lastOrNull()?.let { map?.controller?.animateTo(it) }
            map?.invalidate()
        }
    }

    private fun enableMyLocationIfPermitted() {
        val fine = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            myLoc?.enableMyLocation()
        }
    }

    override fun onResume() {
        super.onResume()
        map?.onResume()
    }

    override fun onPause() {
        map?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        map?.onDetach()
        map = null
        myLoc = null
        polyline = null
        super.onDestroyView()
    }
}
