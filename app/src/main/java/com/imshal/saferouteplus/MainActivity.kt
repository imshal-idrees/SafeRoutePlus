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
private var reportMode = false
class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private val db = FirebaseFirestore.getInstance()

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
                    Toast.makeText(this, "Report saved to database", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error saving report", Toast.LENGTH_SHORT).show()
                }

            val markerOptions = MarkerOptions()
                .position(latLng)
                .title(issueType)
                .snippet(descriptionText)

            mMap.addMarker(markerOptions)

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

                    mMap.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title(issue)
                            .snippet(description)
                    )
                }
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
            }
        }
    }
}
