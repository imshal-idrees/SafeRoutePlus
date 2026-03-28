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
import android.location.Geocoder
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.heatmaps.WeightedLatLng
import com.google.android.gms.maps.model.TileOverlay
import java.util.Locale
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private var reportMode = false
class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private val db = FirebaseFirestore.getInstance()
    private val heatmapPoints = ArrayList<LatLng>()
    private val reportLocations = ArrayList<LatLng>()
    private var heatmapOverlay: TileOverlay? = null
    private var safestPolyline: com.google.android.gms.maps.model.Polyline? = null
    private var fastestPolyline: com.google.android.gms.maps.model.Polyline? = null
    private val selectedFilters = mutableSetOf<String>()
    private var deleteMode = false
    private var heatmapVisible = true
    private lateinit var startInput: EditText
    private var selectedRoute: List<LatLng>? = null
    private val staticRiskPoints = ArrayList<StaticRiskPoint>()


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadStaticDataset()

        startInput = findViewById(R.id.startInput)
        val resetStartButton = findViewById<Button>(R.id.resetStartButton)

        resetStartButton.setOnClickListener {
            startInput.setText("")
            Toast.makeText(this, "Using default start (London)", Toast.LENGTH_SHORT).show()

        }
        val destinationInput = findViewById<EditText>(R.id.destinationInput)

        val startNavButton = findViewById<Button>(R.id.startNavigationButton)

        startNavButton.setOnClickListener {

            val route = selectedRoute

            if (route == null || route.isEmpty()) {
                Toast.makeText(this, "Please select a route first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val origin = route.first()
            val destination = route.last()

            // pick a few waypoints bc Google limit is 23
            val waypointList = route
                .filterIndexed { index, _ -> index % 10 == 0 } // reduce points
                .map { "${it.latitude},${it.longitude}" }

            val waypoints = waypointList.joinToString("|")

            val uri = android.net.Uri.parse(
                "https://www.google.com/maps/dir/?api=1" +
                        "&origin=${origin.latitude},${origin.longitude}" +
                        "&destination=${destination.latitude},${destination.longitude}" +
                        "&travelmode=walking" +
                        "&waypoints=$waypoints"
            )

            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")

            startActivity(intent)
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        val reportButton = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.reportButton)

        reportButton.setOnClickListener {
            reportMode = true
            Toast.makeText(this, "Tap the map to report an issue", Toast.LENGTH_SHORT).show()
        }
        val routeButton = findViewById<Button>(R.id.routeButton)
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

            if (heatmapVisible) {
                heatmapOverlay?.remove()
                heatmapVisible = false
                Toast.makeText(this, "Heatmap OFF", Toast.LENGTH_SHORT).show()
            } else {
                showHeatmap()
                heatmapVisible = true
                Toast.makeText(this, "Heatmap ON", Toast.LENGTH_SHORT).show()
            }
        }
        val filterButton = findViewById<Button>(R.id.filterButton)

        val filterOptions = arrayOf(
            "Poor Lighting",
            "Suspicious Activity",
            "Unsafe Path",
            "Harassment",
            "Other"
        )

        val checkedItems = BooleanArray(filterOptions.size)

        filterButton.setOnClickListener {

            AlertDialog.Builder(this)
                .setTitle("Select Filters")
                .setMultiChoiceItems(filterOptions, checkedItems) { _, which, isChecked ->

                    if (isChecked) {
                        selectedFilters.add(filterOptions[which])
                    } else {
                        selectedFilters.remove(filterOptions[which])
                    }
                }
                .setPositiveButton("Apply") { _, _ ->
                    mMap.clear()
                    loadReports()
                    loadStaticRiskMarkers()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        val deleteButton = findViewById<Button>(R.id.deleteButton)

        deleteButton.setOnClickListener {
            deleteMode = true
            Toast.makeText(this, "Tap a marker to delete", Toast.LENGTH_SHORT).show()
        }
        val dashboardButton = findViewById<Button>(R.id.dashboardButton)

        dashboardButton.setOnClickListener {
            startActivity(android.content.Intent(this, DashboardActivity::class.java))
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
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        submitButton.setOnClickListener {

            val issueType = spinner.selectedItem.toString()
            val descriptionText = description.text.toString()

            if (issueType == "Other" && descriptionText.isEmpty()) {
                Toast.makeText(this, "Please describe the issue", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val (riskLevel, riskScore) = classifyRisk(descriptionText, issueType)

            val report = hashMapOf(
                "latitude" to latLng.latitude,
                "longitude" to latLng.longitude,
                "issueType" to issueType,
                "description" to descriptionText,
                "riskLevel" to riskLevel,
                "riskScore" to riskScore,
                "timestamp" to System.currentTimeMillis()
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
                    val issue = doc.getString("issueType") ?: "Other"
                    val timestamp = doc.getLong("timestamp") ?: 0

                    val date = java.text.SimpleDateFormat("dd MMM yyyy")
                        .format(java.util.Date(timestamp))

                    if (selectedFilters.isNotEmpty() && !selectedFilters.contains(issue)) {
                        continue
                    }
                    val description = doc.getString("description")
                    val riskLevel = doc.getString("riskLevel") ?: "LOW"

                    val position = LatLng(lat, lng)
                    heatmapPoints.add(position)
                    reportLocations.add(position)

                    val marker = mMap.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title(issue)
                            .snippet("Risk: $riskLevel\n$description\n$date")
                            .icon(BitmapDescriptorFactory.defaultMarker(getMarkerColor(issue ?: "Other")))
                    )

                    marker?.tag = doc.id
                }
                if (heatmapPoints.isNotEmpty()) {
                    val provider = HeatmapTileProvider.Builder()
                        .data(heatmapPoints)
                        .radius(50)
                        .build()

                    mMap.addTileOverlay(TileOverlayOptions().tileProvider(provider))
                }
                if (heatmapPoints.isEmpty()) {
                    Toast.makeText(this, "No reports for selected filter", Toast.LENGTH_SHORT).show()
                }
            }
    }
    private fun loadStaticRiskMarkers() {
        for (point in staticRiskPoints) {
            val position = LatLng(point.latitude, point.longitude)

            mMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(point.type)
                    .snippet("Static dataset risk | Severity: ${point.severity}")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }
    }
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        loadReports()
        loadStaticRiskMarkers()
        val london = LatLng(51.5074, -0.1278)
        mMap.addMarker(MarkerOptions().position(london).title("Marker in London"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(london, 8f))
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isZoomGesturesEnabled = true
        mMap.uiSettings.isCompassEnabled = true
        mMap.uiSettings.isMapToolbarEnabled = true
        mMap.setOnMapClickListener { latLng ->
            if (deleteMode) {
                deleteMode = false
                Toast.makeText(this, "Delete mode cancelled", Toast.LENGTH_SHORT).show()
                return@setOnMapClickListener
            }

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

            // iff reporting then ignore marker click so user can place a new one
            if (reportMode) {
                return@setOnMarkerClickListener true
            }

            // delete mode
            if (deleteMode) {

                val docId = marker.tag as? String

                if (docId != null) {

                    AlertDialog.Builder(this)
                        .setTitle("Delete Report")
                        .setMessage("Are you sure you want to delete this report?")
                        .setPositiveButton("Yes") { _, _ ->

                            db.collection("reports").document(docId).delete()
                            marker.remove()

                            Toast.makeText(this, "Report deleted", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("No", null)
                        .show()
                }

                return@setOnMarkerClickListener true
            }

            // normal mode to show the info window at the top of the marker
            marker.showInfoWindow()

            true
        }
        mMap.setOnPolylineClickListener { polyline ->

            if (polyline == safestPolyline) {

                safestPolyline?.color = Color.GREEN
                safestPolyline?.width = 18f

                fastestPolyline?.color = Color.BLUE
                fastestPolyline?.width = 8f

                selectedRoute = safestPolyline?.points

                Toast.makeText(this, "Safest route selected", Toast.LENGTH_SHORT).show()

            } else if (polyline == fastestPolyline) {

                fastestPolyline?.color = Color.BLUE
                fastestPolyline?.width = 18f

                safestPolyline?.color = Color.GREEN
                safestPolyline?.width = 8f

                selectedRoute = fastestPolyline?.points

                Toast.makeText(this, "Fastest route selected", Toast.LENGTH_SHORT).show()
            }
        }
        mMap.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {

            override fun getInfoWindow(marker: com.google.android.gms.maps.model.Marker): android.view.View? {
                return null
            }

            override fun getInfoContents(marker: com.google.android.gms.maps.model.Marker): android.view.View {

                val view = layoutInflater.inflate(R.layout.custom_info_window, null)

                val title = view.findViewById<android.widget.TextView>(R.id.title)
                val snippet = view.findViewById<android.widget.TextView>(R.id.snippet)

                title.text = marker.title
                snippet.text = marker.snippet

                return view
            }
        })
    }
    private fun getMarkerColor(issueType: String): Float {
        return when (issueType) {
            "Poor Lighting" -> BitmapDescriptorFactory.HUE_YELLOW
            "Suspicious Activity" -> BitmapDescriptorFactory.HUE_ORANGE
            "Unsafe Path" -> BitmapDescriptorFactory.HUE_BLUE
            "Harassment" -> BitmapDescriptorFactory.HUE_MAGENTA
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
    private fun callDirections(originLatLng: LatLng, destinationLatLng: LatLng) {

        val origin = "${originLatLng.latitude},${originLatLng.longitude}"
        val dest = "${destinationLatLng.latitude},${destinationLatLng.longitude}"

        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()

        val service = retrofit.create(DirectionsService::class.java)

        // create 2 artificial waypoints (left & right)
        val midLat = (originLatLng.latitude + destinationLatLng.latitude) / 2
        val midLng = (originLatLng.longitude + destinationLatLng.longitude) / 2

        val offset = 0.01 // adjust for more separation

        val waypoint1 = "$midLat,${midLng + offset}"
        val waypoint2 = "$midLat,${midLng - offset}"

        val waypointsList = listOf(
            null, // fastest (no waypoint)
            waypoint1, // route A
            waypoint2  // route B
        )

        val allRoutes = mutableListOf<List<LatLng>>()

        var completedCalls = 0

        waypointsList.forEach { waypoint ->

            val call = service.getRoute(
                origin = origin,
                destination = dest,
                waypoints = waypoint,
                apiKey = "AIzaSyCZs0CcyHqeA31qqzJnVk5Mq6DoLaMONxM"
            )

            call.enqueue(object : retrofit2.Callback<DirectionsResponse> {

                override fun onResponse(
                    call: retrofit2.Call<DirectionsResponse>,
                    response: retrofit2.Response<DirectionsResponse>
                ) {

                    completedCalls++

                    if (response.isSuccessful && response.body() != null) {

                        val routes = response.body()!!.routes

                        routes.forEach { route ->
                            val decoded = decodePolyline(route.overview_polyline.points)
                            allRoutes.add(decoded)
                        }
                    }

                    // when all 3 calls are done
                    if (completedCalls == waypointsList.size) {

                        if (allRoutes.isEmpty()) {
                            Toast.makeText(this@MainActivity, "No routes found", Toast.LENGTH_SHORT).show()
                            return
                        }

                        var fastestRoute: List<LatLng>? = null
                        var safestRoute: List<LatLng>? = null

                        var lowestRisk = Int.MAX_VALUE
                        var fastestRisk = 0

                        allRoutes.forEachIndexed { index, route ->

                            val risk = calculateRouteRiskValue(route)

                            if (index == 0) {
                                fastestRoute = route
                                fastestRisk = risk
                            }

                            if (risk < lowestRisk) {
                                lowestRisk = risk
                                safestRoute = route
                            }
                        }

                        // draw the routes
                        mMap.clear()

                        mMap.addMarker(
                            MarkerOptions()
                                .position(originLatLng)
                                .title("Start")
                        )

                        mMap.addMarker(
                            MarkerOptions()
                                .position(destinationLatLng)
                                .title("Destination")
                        )

                        loadReports()
                        loadStaticRiskMarkers()

                        fastestPolyline = fastestRoute?.let {
                            mMap.addPolyline(
                                PolylineOptions()
                                    .addAll(it)
                                    .width(10f)
                                    .color(Color.BLUE)
                                    .clickable(true)
                            )
                        }

                        safestPolyline = safestRoute?.let {
                            mMap.addPolyline(
                                PolylineOptions()
                                    .addAll(it)
                                    .width(14f)
                                    .color(Color.GREEN)
                                    .clickable(true)
                            )
                        }

                        safestRoute?.lastOrNull()?.let {
                            mMap.addMarker(
                                MarkerOptions()
                                    .position(it)
                                    .title("Destination")
                            )
                        }

                        Toast.makeText(
                            this@MainActivity,
                            "Safest: $lowestRisk | Fastest: $fastestRisk",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: retrofit2.Call<DirectionsResponse>, t: Throwable) {
                    completedCalls++
                }
            })
        }
    }

    private fun getRoute(destination: String) {

        mMap.clear()
        loadReports()
        loadStaticRiskMarkers()

        val geocoder = Geocoder(this, Locale.getDefault())

        val destinationAddresses = try {
            geocoder.getFromLocationName(destination, 1)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        if (destinationAddresses.isNullOrEmpty()) {
            Toast.makeText(this, "Location not found: $destination", Toast.LENGTH_LONG).show()
            return
        }

        val destinationLatLng = LatLng(
            destinationAddresses[0].latitude,
            destinationAddresses[0].longitude
        )

        val originText = startInput.text.toString()

        // if user entered a custom start
        if (originText.isNotEmpty()) {

            val originAddresses = try {
                geocoder.getFromLocationName(originText, 1)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            if (originAddresses.isNullOrEmpty()) {
                Toast.makeText(this, "Start location not found", Toast.LENGTH_SHORT).show()
                return
            }

            val originLatLng = LatLng(
                originAddresses[0].latitude,
                originAddresses[0].longitude
            )

            callDirections(originLatLng, destinationLatLng)

        } else {
            // default to London
            val london = LatLng(51.5074, -0.1278)
            callDirections(london, destinationLatLng)
        }
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

        // user submitted reports
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

                val weight = when {
                    distance < 50 -> 3
                    distance < 100 -> 2
                    distance < 200 -> 1
                    else -> 0
                }

                riskScore += weight
            }
        }

        // static dataset points
        for (staticPoint in staticRiskPoints) {
            for (point in routePoints) {
                val results = FloatArray(1)

                android.location.Location.distanceBetween(
                    point.latitude,
                    point.longitude,
                    staticPoint.latitude,
                    staticPoint.longitude,
                    results
                )

                val distance = results[0]

                val weight = when {
                    distance < 50 -> staticPoint.severity * 3
                    distance < 100 -> staticPoint.severity * 2
                    distance < 200 -> staticPoint.severity
                    else -> 0
                }

                riskScore += weight
            }
        }

        return riskScore
    }

    private fun classifyRisk(description: String, issueType: String): Pair<String, Int> {

        val text = description.lowercase()

        return when {

            // high risk
            text.contains("attack") ||
                    text.contains("knife") ||
                    text.contains("followed") ||
                    text.contains("assault") ||
                    issueType == "Harassment" -> {
                Pair("HIGH", 3)
            }

            // medium risk
            text.contains("dark") ||
                    text.contains("no light") ||
                    text.contains("suspicious") -> {
                Pair("MEDIUM", 2)
            }

            // low risk
            else -> {
                Pair("LOW", 1)
            }
        }
    }
    private fun loadStaticDataset() {
        try {
            val json = assets.open("crime_data.json")
                .bufferedReader()
                .use { it.readText() }

            val type = object : TypeToken<List<StaticRiskPoint>>() {}.type
            val points: List<StaticRiskPoint> = Gson().fromJson(json, type)

            staticRiskPoints.clear()
            staticRiskPoints.addAll(points)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load static dataset", Toast.LENGTH_SHORT).show()
        }
    }

}
