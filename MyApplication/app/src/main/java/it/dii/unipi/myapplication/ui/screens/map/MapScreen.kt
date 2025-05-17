package it.dii.unipi.myapplication.ui.screens.map

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.android.gms.maps.model.VisibleRegion
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.maps.android.heatmaps.HeatmapTileProvider
import it.dii.unipi.myapplication.R
import it.dii.unipi.myapplication.model.HeatmapRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import android.app.TimePickerDialog
import android.util.Log
import java.lang.Double

class MapScreen : Fragment() {

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1001
    }

    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null

    private lateinit var etStart: TextInputEditText
    private lateinit var etEnd:   TextInputEditText
    private lateinit var btnApply: Button
    private lateinit var btnEditFilters: ImageButton
    private lateinit var filterForm: View
    private lateinit var btnCenterLocation: ImageButton

    private var startTimeMillis: Long? = null
    private var endTimeMillis:   Long? = null

    private val heatmapRepo = HeatmapRepository()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private var deviceLocationCallback: LocationCallback? = null
    private var lastDeviceLocation: Location? = null

    // Per il panning manuale
    private var lastCenterLocation: Location? = null
    private var currentRadiusMeters: Float = 1000f  // raggio corrente, inizialmente 1km

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_map, container, false)

        // Inizializza view
        mapView = view.findViewById(R.id.mapView)
        etStart = view.findViewById(R.id.etStart)
        etEnd = view.findViewById(R.id.etEnd)
        btnApply = view.findViewById(R.id.btnApply)
        btnEditFilters = view.findViewById(R.id.btnEditFilters)
        filterForm = view.findViewById(R.id.filterFormCard)
        btnCenterLocation = view.findViewById(R.id.btnCenterLocation)

        // Location API
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        locationRequest = LocationRequest.create().apply {
            interval = 10_000
            fastestInterval = 5_000
            priority = Priority.PRIORITY_HIGH_ACCURACY
            smallestDisplacement = 0f // gestito manualmente
        }

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            googleMap = map
            ensureLocationPermission()

            // Listener panning/zoom
            map.setOnCameraIdleListener {
                handleCameraIdle()
            }
        }

        // Date/time picker
        etStart.setOnClickListener {
            openDateTimePicker { millis ->
                startTimeMillis = millis
                etStart.setText(DateFormat.getDateTimeInstance().format(Date(millis)))
                lastCenterLocation = null // reset
            }
        }
        etEnd.setOnClickListener {
            openDateTimePicker { millis ->
                endTimeMillis = millis
                etEnd.setText(DateFormat.getDateTimeInstance().format(Date(millis)))
                lastCenterLocation = null
            }
        }

        // Applica filtri
        btnApply.setOnClickListener {
            if (startTimeMillis == null || endTimeMillis == null) {
                Toast.makeText(requireContext(), "Please, fill all the fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            filterForm.visibility = View.GONE
            btnEditFilters.visibility = View.VISIBLE
            btnCenterLocation.visibility = View.VISIBLE
            // Centrati sulla location utente
            lastDeviceLocation?.let {
                googleMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(it.latitude, it.longitude), 13f
                    )
                )
            }
            // Forza fetch iniziale con 1km
            googleMap?.let { map ->
                currentRadiusMeters = 1000f
                val center = map.cameraPosition.target
                val centerLoc = Location("init").apply {
                    latitude = center.latitude
                    longitude = center.longitude
                }
                lastCenterLocation = centerLoc
                fetchHeatmap(centerLoc, currentRadiusMeters.toDouble())
            }
        }

        // Modifica filtri
        btnEditFilters.setOnClickListener {
            filterForm.visibility = View.VISIBLE
            btnEditFilters.visibility = View.GONE
            btnCenterLocation.visibility = View.GONE
        }

        // Ricentra su posizione utente
        btnCenterLocation.setOnClickListener {
            lastDeviceLocation?.let {
                googleMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(it.latitude, it.longitude), 13f
                    )
                )
            }
        }

        return view
    }

    private fun ensureLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        } else {
            startLocationUpdates()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startLocationUpdates() {
        try {
            deviceLocationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    lastDeviceLocation = loc
                }
            }
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                deviceLocationCallback as LocationCallback,
                Looper.getMainLooper()
            )
        } catch (se: SecurityException) {
            Toast.makeText(requireContext(), "Permission error: ${se.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun computeRadiusFromMap(): Float {
        googleMap?.let { map ->
            val vr: VisibleRegion = map.projection.visibleRegion
            val ne = vr.latLngBounds.northeast
            val sw = vr.latLngBounds.southwest
            val results = FloatArray(1)
            Location.distanceBetween(
                ne.latitude, ne.longitude,
                sw.latitude, sw.longitude,
                results
            )
            return results[0] / 2f
        }
        return currentRadiusMeters
    }

    private fun handleCameraIdle() {
        if (startTimeMillis == null || endTimeMillis == null) return
        googleMap?.let { map ->
            val centerLatLng = map.cameraPosition.target
            val centerLoc = Location("camera").apply {
                latitude = centerLatLng.latitude
                longitude = centerLatLng.longitude
            }
            val newRadius = computeRadiusFromMap()
            val dist = lastCenterLocation?.distanceTo(centerLoc) ?: newRadius + 1
            if (dist >= (lastCenterLocation?.let { computeRadiusFromMap() } ?: newRadius)) {
                lastCenterLocation = centerLoc
                currentRadiusMeters = newRadius
                fetchHeatmap(centerLoc, newRadius.toDouble())
            }
        }
    }

    private fun fetchHeatmap(location: Location, radiusMeters: kotlin.Double) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    val weightedData = withContext(Dispatchers.IO) {
                        heatmapRepo.fetchHeatmap(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            radiusKm = radiusMeters / 1000f,
                            startTimeMillis = startTimeMillis!!,
                            endTimeMillis = endTimeMillis!!
                        )
                    }
                    val provider = HeatmapTileProvider.Builder()
                        .weightedData(weightedData)
                        .radius(20)
                        .opacity(0.6)
                        .build()
                    googleMap?.apply {
                        clear()
                        addTileOverlay(TileOverlayOptions().tileProvider(provider))
                    }
                } catch (e: Exception) {
                    Log.d("[ERROR FETCH DATA]", "error: $e")
                }
            }
        }
    }


    private fun openDateTimePicker(onPicked: (Long) -> Unit) {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Choose date")
            .build()
        datePicker.addOnPositiveButtonClickListener { dateMillis ->
            val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
            TimePickerDialog(
                requireContext(), { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    onPicked(calendar.timeInMillis)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }
        datePicker.show(childFragmentManager, "DATE_PICKER")
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        deviceLocationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        super.onPause()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        mapView.onSaveInstanceState(out)
    }
}
