package it.dii.unipi.myapplication.ui.screens.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.maps.android.heatmaps.HeatmapTileProvider
import it.dii.unipi.myapplication.R
import kotlinx.coroutines.withContext
import it.dii.unipi.myapplication.model.HeatmapRepository
import androidx.lifecycle.Lifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MapScreen : Fragment() {

    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        mapView = view.findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            googleMap = map
            setupHeatmap()
        }
        return view
    }

    private val heatmapRepo = HeatmapRepository()

    private fun setupHeatmap() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    val weightedData = withContext(Dispatchers.IO) {
                        heatmapRepo.fetchHeatmap(
                            latitude   = 43.721597073404126,
                            longitude  = 10.391555608835489,
                            radiusKm   = 1
                        )
                    }

                    val provider = HeatmapTileProvider.Builder()
                        .weightedData(weightedData)
                        .radius(5)
                        .opacity(0.6)
                        .build()

                    googleMap?.apply {
                        addTileOverlay(TileOverlayOptions().tileProvider(provider))
                        moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                com.google.android.gms.maps.model.LatLng(43.7215, 10.3915),
                                10f
                            )
                        )
                    }
                } catch (e: Exception) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Error")
                        .setMessage(e.message)
                        .show()
                }
            }
        }
    }


    override fun onResume()    { super.onResume(); mapView.onResume() }
    override fun onPause()     { mapView.onPause(); super.onPause() }
    override fun onDestroy()   { mapView.onDestroy(); super.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        mapView.onSaveInstanceState(out)
    }
}
