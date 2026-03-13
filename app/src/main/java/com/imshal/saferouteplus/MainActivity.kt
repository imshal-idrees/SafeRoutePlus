package com.imshal.saferouteplus

import com.google.firebase.firestore.FirebaseFirestore
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import android.widget.Button
import android.widget.Toast
import android.app.AlertDialog
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.android.gms.maps.model.TileOverlayOptions
import java.util.ArrayList
import android.graphics.Color
import com.google.android.gms.maps.model.PolylineOptions
private var reportMode = false
class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private val db = FirebaseFirestore.getInstance()
    private val heatmapPoints = ArrayList<LatLng>()
    private val reportLocations = ArrayList<LatLng>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        val reportButton = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.reportButton)

        reportButton.setOnClickListener {
            reportMode = true
            Toast.makeText(this, "Tap the map to report an issue", Toast.LENGTH_SHORT).show()
        }
        val routeButton = findViewById<Button>(R.id.routeButton)
        val destinationInput = findViewById<EditText>(R.id.destinationInput)

        routeButton.setOnClickListener {

            val destination = destinationInput.text.toString()

            if (destination.isEmpty()) {
                Toast.makeText(this, "Enter a destination", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            getRoute(destination)
        }
    }

    private fun showReportDialog(latLng: LatLng) {

        val dialogView = layoutInflater.inflate(R.layout.dialog_report, null)

        val spinner = dialogView.findViewById<Spinner>(R.id.issueSpinner)
        val description = dialogView.findViewById<EditText>(R.id.descriptionInput)
        val submitButton = dialogView.findViewById<Button>(R.id.submitReport)

        val issues = arrayOf(
            "Poor Lighting",
            "Suspicious Activity",
            "Unsafe Path",
            "Harassment",
            "Other"
        )

        val adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, issues)

        spinner.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        submitButton.setOnClickListener {

            val issueType = spinner.selectedItem.toString()
            val descriptionText = description.text.toString()

            if (descriptionText.isEmpty()) {
                Toast.makeText(this, "Please add a description", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val report = Report(
                latitude = latLng.latitude,
                longitude = latLng.longitude,
                issueType = issueType,
                description = descriptionText
            )

            db.collection("reports")
                .add(report)
                .addOnSuccessListener {
                        documentReference ->

                    val marker = mMap.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title(issueType)
                            .snippet(descriptionText)
                            .icon(BitmapDescriptorFactory.defaultMarker(getMarkerColor(issueType)))
                    )

                    marker?.tag = documentReference.id

                    Toast.makeText(this, "Report saved to database", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error saving report", Toast.LENGTH_SHORT).show()
                }

            dialog.dismiss()

            Toast.makeText(this, "Report submitted", Toast.LENGTH_SHORT).show()


        }

        dialog.show()
    }
    private fun loadReports() {

        db.collection("reports")
            .get()
            .addOnSuccessListener { documents ->

                for (doc in documents) {

                    val lat = doc.getDouble("latitude") ?: continue
                    val lng = doc.getDouble("longitude") ?: continue
                    val issue = doc.getString("issueType")
                    val description = doc.getString("description")

                    val position = LatLng(lat, lng)
                    heatmapPoints.add(position)
                    reportLocations.add(position)

                    val marker = mMap.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title(issue)
                            .snippet(description)
                            .icon(BitmapDescriptorFactory.defaultMarker(getMarkerColor(issue ?: "Other")))
                    )

                    marker?.tag = doc.id
                }
                val provider = HeatmapTileProvider.Builder()
                    .data(heatmapPoints)
                    .radius(50)
                    .build()

                mMap.addTileOverlay(TileOverlayOptions().tileProvider(provider))
            }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        loadReports()
        val london = LatLng(51.5074, -0.1278)
        mMap.addMarker(MarkerOptions().position(london).title("Marker in London"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(london, 8f))
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isZoomGesturesEnabled = true
        mMap.uiSettings.isCompassEnabled = true
        mMap.uiSettings.isMapToolbarEnabled = true
        mMap.setOnMapClickListener { latLng ->

            if (reportMode) {

                showReportDialog(latLng)
                reportMode = false

            } else {

                val score = calculateSafetyScore(latLng)

                val safetyLevel = when {
                    score == 0 -> "Safe"
                    score <= 3 -> "Moderate"
                    score <= 6 -> "Risky"
                    else -> "Dangerous"
                }

                Toast.makeText(
                    this,
                    "Safety level: $safetyLevel ($score reports nearby)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        mMap.setOnMarkerClickListener { marker ->

            AlertDialog.Builder(this)
                .setTitle("Delete Report")
                .setMessage("Do you want to delete this report?")
                .setPositiveButton("Delete") { _, _ ->

                    val docId = marker.tag as? String

                    if (docId != null) {

                        db.collection("reports")
                            .document(docId)
                            .delete()

                        marker.remove()

                        Toast.makeText(this, "Report deleted", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()

            true
        }
    }

    private fun getMarkerColor(issueType: String): Float {
        return when (issueType) {
            "Poor Lighting" -> BitmapDescriptorFactory.HUE_YELLOW
            "Suspicious Activity" -> BitmapDescriptorFactory.HUE_ORANGE
            "Unsafe Path" -> BitmapDescriptorFactory.HUE_BLUE
            "Harassment" -> BitmapDescriptorFactory.HUE_RED
            else -> BitmapDescriptorFactory.HUE_VIOLET
        }
    }
    private fun calculateSafetyScore(location: LatLng): Int {

        var nearbyReports = 0

        for (report in reportLocations) {

            val distance = FloatArray(1)

            android.location.Location.distanceBetween(
                location.latitude,
                location.longitude,
                report.latitude,
                report.longitude,
                distance
            )

            if (distance[0] < 500) {  // 500 meters radius
                nearbyReports++
            }
        }

        return nearbyReports
    }

    private fun getRoute(destination: String) {
        mMap.clear()
        loadReports()
        val geocoder = android.location.Geocoder(this)
        val addresses = geocoder.getFromLocationName(destination, 1)

        if (addresses.isNullOrEmpty()) {
            Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show()
            return
        }

        val destinationLatLng = LatLng(
            addresses[0].latitude,
            addresses[0].longitude
        )

        val origin = "${mMap.cameraPosition.target.latitude},${mMap.cameraPosition.target.longitude}"
        val dest = "${destinationLatLng.latitude},${destinationLatLng.longitude}"

        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()

        val service = retrofit.create(DirectionsService::class.java)

        val call = service.getRoute(
            origin = origin,
            destination = dest,
            apiKey = "AIzaSyDfzRlcnbnqk_p19g1bdnYk__VA9DCwLoA"
        )

        call.enqueue(object : retrofit2.Callback<DirectionsResponse> {

            override fun onResponse(
                call: retrofit2.Call<DirectionsResponse>,
                response: retrofit2.Response<DirectionsResponse>
            ) {

                if (!response.isSuccessful || response.body() == null) {
                    Toast.makeText(this@MainActivity, "Route error", Toast.LENGTH_SHORT).show()
                    return
                }

                val routes = response.body()?.routes

                if (routes.isNullOrEmpty()) {
                    Toast.makeText(this@MainActivity, "No route found", Toast.LENGTH_SHORT).show()
                    return
                }

                val polyline = routes[0].overview_polyline.points

                val decodedPath = decodePolyline(polyline)

                mMap.addPolyline(
                    PolylineOptions()
                        .addAll(decodedPath)
                        .width(10f)
                        .color(android.graphics.Color.BLUE)
                )

                mMap.addMarker(
                    MarkerOptions()
                        .position(destinationLatLng)
                        .title("Destination")
                )
            }

            override fun onFailure(call: retrofit2.Call<DirectionsResponse>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Network error: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }
    private fun decodePolyline(encoded: String): List<LatLng> {

        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {

            var b: Int
            var shift = 0
            var result = 0

            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)

            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0

            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)

            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            poly.add(
                LatLng(
                    lat.toDouble() / 1E5,
                    lng.toDouble() / 1E5
                )
            )
        }

        return poly
    }

}
