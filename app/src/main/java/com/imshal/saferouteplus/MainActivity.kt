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
import com.google.maps.android.heatmaps.WeightedLatLng
import com.google.android.gms.maps.model.TileOverlay
import com.google.android.gms.tasks.Tasks
private var reportMode = false
class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private val db = FirebaseFirestore.getInstance()
    private val heatmapPoints = ArrayList<LatLng>()
    private val reportLocations = ArrayList<LatLng>()
    private var heatmapOverlay: TileOverlay? = null

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
        val heatmapButton = findViewById<Button>(R.id.heatmapButton)

        heatmapButton.setOnClickListener {
            showHeatmap()
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
        heatmapPoints.clear()
        reportLocations.clear()
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

    private fun calculateRouteRisk(routePoints: List<LatLng>) {

        db.collection("reports")
            .get()
            .addOnSuccessListener { documents ->

                var riskScore = 0

                for (doc in documents) {

                    val lat = doc.getDouble("latitude") ?: continue
                    val lng = doc.getDouble("longitude") ?: continue

                    val reportLocation = LatLng(lat, lng)

                    for (point in routePoints) {

                        val results = FloatArray(1)

                        android.location.Location.distanceBetween(
                            point.latitude, point.longitude,
                            reportLocation.latitude, reportLocation.longitude,
                            results
                        )

                        val distance = results[0]

                        if (distance < 50) {
                            riskScore += 3
                        } else if (distance < 100) {
                            riskScore += 2
                        } else if (distance < 200) {
                            riskScore += 1
                        }
                    }
                }

                showRiskLevel(riskScore)
            }
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

                var safestRoute: List<LatLng>? = null
                var lowestRisk = Int.MAX_VALUE

                for (route in routes) {

                    val polyline = route.overview_polyline.points
                    val decodedPath = decodePolyline(polyline)

                    val risk = calculateRouteRiskValue(decodedPath)

                    if (risk < lowestRisk) {
                        lowestRisk = risk
                        safestRoute = decodedPath
                    }
                }

                safestRoute?.let {

                    mMap.clear()
                    loadReports()

                    mMap.addPolyline(
                        PolylineOptions()
                            .addAll(it)
                            .width(12f)
                            .color(android.graphics.Color.GREEN)
                    )

                    mMap.addMarker(
                        MarkerOptions()
                            .position(it.last())
                            .title("Destination")
                    )

                    Toast.makeText(
                        this@MainActivity,
                        "Safest Route Selected (Score: $lowestRisk)",
                        Toast.LENGTH_LONG
                    ).show()
                }
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

    private fun showRiskLevel(score: Int) {

        val riskLevel = when {
            score < 10 -> "Low"
            score < 25 -> "Moderate"
            else -> "High"
        }

        Toast.makeText(
            this,
            "Route Safety: $riskLevel (Score: $score)",
            Toast.LENGTH_LONG
        ).show()
    }
    private fun showHeatmap() {

        db.collection("reports")
            .get()
            .addOnSuccessListener { documents ->

                val heatmapData = ArrayList<WeightedLatLng>()

                for (doc in documents) {

                    val lat = doc.getDouble("latitude") ?: continue
                    val lng = doc.getDouble("longitude") ?: continue

                    val issue = doc.getString("issueType") ?: "Other"

                    val weight = when (issue) {
                        "Harassment" -> 4.0
                        "Suspicious Activity" -> 3.0
                        "Poor Lighting" -> 2.0
                        else -> 1.0
                    }

                    heatmapData.add(
                        WeightedLatLng(LatLng(lat, lng), weight)
                    )
                }

                if (heatmapData.isEmpty()) {
                    Toast.makeText(this, "No data for heatmap", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val provider = HeatmapTileProvider.Builder()
                    .weightedData(heatmapData)
                    .radius(50)
                    .build()

                heatmapOverlay?.remove()

                heatmapOverlay = mMap.addTileOverlay(
                    TileOverlayOptions().tileProvider(provider)
                )
            }
    }

    private fun calculateRouteRiskValue(routePoints: List<LatLng>): Int {

        var riskScore = 0

        for (reportLocation in reportLocations) {

            for (point in routePoints) {

                val results = FloatArray(1)

                android.location.Location.distanceBetween(
                    point.latitude,
                    point.longitude,
                    reportLocation.latitude,
                    reportLocation.longitude,
                    results
                )

                val distance = results[0]

                if (distance < 50) {
                    riskScore += 3
                } else if (distance < 100) {
                    riskScore += 2
                } else if (distance < 200) {
                    riskScore += 1
                }
            }
        }

        return riskScore
    }
}
