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
        val reportButton = findViewById<Button>(R.id.reportButton)

        reportButton.setOnClickListener {
            reportMode = true
            Toast.makeText(this, "Tap the map to report an issue", Toast.LENGTH_SHORT).show()
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

}
