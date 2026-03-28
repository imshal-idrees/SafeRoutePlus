package com.imshal.saferouteplus

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import com.google.firebase.firestore.FirebaseFirestore
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.Entry
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var pieChart: PieChart
    private lateinit var barChart: BarChart
    private val db = FirebaseFirestore.getInstance()

    private lateinit var totalReportsText: TextView
    private lateinit var highRiskText: TextView
    private lateinit var commonIssueText: TextView
    private lateinit var lineChart: LineChart
    private lateinit var topAreasText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        lineChart = findViewById(R.id.lineChart)
        topAreasText = findViewById(R.id.topAreas)

        pieChart = findViewById(R.id.pieChart)
        barChart = findViewById(R.id.barChart)

        totalReportsText = findViewById(R.id.totalReports)
        highRiskText = findViewById(R.id.highRiskReports)
        commonIssueText = findViewById(R.id.commonIssue)

        loadAnalytics()
    }

    private fun loadAnalytics() {

        db.collection("reports")
            .get()
            .addOnSuccessListener { documents ->

                if (documents.isEmpty) {
                    totalReportsText.text = "No data available"
                    return@addOnSuccessListener
                }

                val total = documents.size()
                var highRisk = 0

                val issueCount = mutableMapOf<String, Int>()
                val riskCount = mutableMapOf<String, Int>()
                val dateCount = mutableMapOf<String, Int>()
                val locationCount = mutableMapOf<String, Int>()

                for (doc in documents) {



                    val risk = doc.getString("riskLevel") ?: "LOW"
                    val issue = doc.getString("issueType") ?: "Other"

                    if (risk == "HIGH") highRisk++

                    issueCount[issue] = issueCount.getOrDefault(issue, 0) + 1
                    riskCount[risk] = riskCount.getOrDefault(risk, 0) + 1

                    // data analytics
                    val timestamp = doc.getLong("timestamp") ?: 0
                    val date = SimpleDateFormat("dd MMM", Locale.getDefault())
                        .format(Date(timestamp))

                    dateCount[date] = dateCount.getOrDefault(date, 0) + 1

// location analytics (rounded to area)
                    val lat = doc.getDouble("latitude") ?: 0.0
                    val lng = doc.getDouble("longitude") ?: 0.0

                    val areaKey = "${"%.2f".format(lat)}, ${"%.2f".format(lng)}"
                    locationCount[areaKey] = locationCount.getOrDefault(areaKey, 0) + 1
                }

                val mostCommon = issueCount.maxByOrNull { it.value }?.key ?: "None"

                totalReportsText.text = "Total Reports: $total"
                highRiskText.text = "High Risk Reports: $highRisk"
                commonIssueText.text = "Most Common Issue: $mostCommon"

                setupPieChart(riskCount)
                setupBarChart(issueCount)
                setupLineChart(dateCount)
                showTopAreas(locationCount)
            }
            .addOnFailureListener {
                totalReportsText.text = "Error loading data"
            }
    }

    private fun setupPieChart(riskCount: Map<String, Int>) {

        val entries = ArrayList<PieEntry>()

        for ((risk, count) in riskCount) {
            entries.add(PieEntry(count.toFloat(), risk))
        }

        val dataSet = PieDataSet(entries, "Risk Levels")
        dataSet.colors = ColorTemplate.COLORFUL_COLORS.toList()
        dataSet.valueTextSize = 14f

        val data = PieData(dataSet)

        pieChart.data = data
        pieChart.description.isEnabled = false
        pieChart.centerText = "Risk Distribution"
        pieChart.setUsePercentValues(true)
        pieChart.setEntryLabelTextSize(12f)
        pieChart.setCenterTextSize(16f)
        pieChart.legend.isEnabled = true
        pieChart.animateY(1000)

        pieChart.invalidate()
    }

    private fun setupBarChart(issueCount: Map<String, Int>) {

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        var index = 0f

        for ((issue, count) in issueCount) {
            entries.add(BarEntry(index, count.toFloat()))
            labels.add(issue)
            index++
        }

        val dataSet = BarDataSet(entries, "Issue Types")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextSize = 12f

        val data = BarData(dataSet)
        data.barWidth = 0.9f

        barChart.data = data
        barChart.description.isEnabled = false
        barChart.axisRight.isEnabled = false
        barChart.setFitBars(true)

        barChart.xAxis.valueFormatter =
            com.github.mikephil.charting.formatter.IndexAxisValueFormatter(labels)

        barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        barChart.xAxis.granularity = 1f
        barChart.xAxis.setDrawGridLines(false)

        barChart.animateY(1000)

        barChart.invalidate()
    }

    private fun setupLineChart(dateCount: Map<String, Int>) {

        val entries = ArrayList<Entry>()
        val labels = ArrayList<String>()

        var index = 0f

        for ((date, count) in dateCount.toSortedMap()) {
            entries.add(Entry(index, count.toFloat()))
            labels.add(date)
            index++
        }

        val dataSet = LineDataSet(entries, "Reports")
        dataSet.valueTextSize = 10f
        dataSet.lineWidth = 2f
        dataSet.setCircleRadius(4f)

        val data = LineData(dataSet)

        lineChart.data = data
        lineChart.description.isEnabled = false
        lineChart.axisRight.isEnabled = false

        lineChart.xAxis.valueFormatter =
            com.github.mikephil.charting.formatter.IndexAxisValueFormatter(labels)

        lineChart.xAxis.granularity = 1f
        lineChart.xAxis.setDrawGridLines(false)

        lineChart.animateX(1000)
        lineChart.invalidate()
    }
    private fun showTopAreas(locationCount: Map<String, Int>) {

        val top = locationCount.entries
            .sortedByDescending { it.value }
            .take(3)

        if (top.isEmpty()) {
            topAreasText.text = "No location data"
            return
        }

        val result = StringBuilder()

        top.forEachIndexed { index, entry ->
            result.append("${index + 1}. Area: ${entry.key} (${entry.value} reports)\n")
        }

        topAreasText.text = result.toString()
    }
}
