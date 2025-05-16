package it.dii.unipi.myapplication.ui.screens.map

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.TileOverlayOptions
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

class MapScreen : Fragment() {

    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null

    private lateinit var etStart: TextInputEditText
    private lateinit var etEnd:   TextInputEditText
    private lateinit var btnApply: Button
    private lateinit var btnEditFilters: ImageButton
    private lateinit var filterForm:     View

    private var startTimeMillis: Long? = null
    private var endTimeMillis:   Long? = null

    private val heatmapRepo = HeatmapRepository()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_map, container, false)

        mapView         = view.findViewById(R.id.mapView)
        etStart         = view.findViewById(R.id.etStart)
        etEnd           = view.findViewById(R.id.etEnd)
        btnApply        = view.findViewById(R.id.btnApply)
        btnEditFilters  = view.findViewById(R.id.btnEditFilters)
        filterForm      = view.findViewById(R.id.filterFormCard)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            googleMap = map
        }

        etStart.setOnClickListener { openDateTimePicker { millis ->
            startTimeMillis = millis
            etStart.setText(DateFormat.getDateTimeInstance().format(Date(millis)))
        } }
        etEnd.setOnClickListener   { openDateTimePicker { millis ->
            endTimeMillis = millis
            etEnd.setText(DateFormat.getDateTimeInstance().format(Date(millis)))
        } }

        btnApply.setOnClickListener {
            if (startTimeMillis == null || endTimeMillis == null) {
                Toast.makeText(requireContext(), "Please, fill all the fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            filterForm.visibility = View.GONE
            btnEditFilters.visibility = View.VISIBLE
            setupHeatmap(startTimeMillis!!, endTimeMillis!!)
        }

        btnEditFilters.setOnClickListener {
            filterForm.visibility = View.VISIBLE
            btnEditFilters.visibility = View.GONE
        }

        return view
    }

    private fun openDateTimePicker(onPicked: (Long) -> Unit) {
        // date picker
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Choose date")
            .build()
        datePicker.addOnPositiveButtonClickListener { dateMillis ->
            // poi time picker
            val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
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

    private fun setupHeatmap(start: Long, end: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    val weightedData = withContext(Dispatchers.IO) {
                        heatmapRepo.fetchHeatmap(
                            latitude   = 43.721597073404126,
                            longitude  = 10.391555608835489,
                            radiusKm   = 1,
                            startTimeMillis = start,
                            endTimeMillis   = end
                        )
                    }

                    val provider = HeatmapTileProvider.Builder()
                        .weightedData(weightedData)
                        .radius(15)
                        .opacity(0.6)
                        .build()

                    googleMap?.apply {
                        clear()
                        addTileOverlay(TileOverlayOptions().tileProvider(provider))
                        moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(43.7215, 10.3915),
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

    // On lifecycle delegate
    override fun onResume()    { super.onResume(); mapView.onResume() }
    override fun onPause()     { mapView.onPause(); super.onPause() }
    override fun onDestroy()   { mapView.onDestroy(); super.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        mapView.onSaveInstanceState(out)
    }
}
