package com.imshal.saferouteplus

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import com.google.firebase.firestore.FirebaseFirestore

class DashboardActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var totalReportsText: TextView
    private lateinit var highRiskText: TextView
    private lateinit var commonIssueText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        totalReportsText = findViewById(R.id.totalReports)
        highRiskText = findViewById(R.id.highRiskReports)
        commonIssueText = findViewById(R.id.commonIssue)

        loadAnalytics()
    }

    private fun loadAnalytics() {

        db.collection("reports")
            .get()
            .addOnSuccessListener { documents ->

                val total = documents.size()
                var highRisk = 0

                val issueCount = mutableMapOf<String, Int>()

                for (doc in documents) {

                    val risk = doc.getString("riskLevel") ?: "LOW"
                    val issue = doc.getString("issueType") ?: "Other"

                    if (risk == "HIGH") {
                        highRisk++
                    }

                    issueCount[issue] = issueCount.getOrDefault(issue, 0) + 1
                }

                val mostCommon = issueCount.maxByOrNull { it.value }?.key ?: "None"

                totalReportsText.text = "Total Reports: $total"
                highRiskText.text = "High Risk Reports: $highRisk"
                commonIssueText.text = "Most Common Issue: $mostCommon"
            }
    }
}
/*package com.imshal.saferouteplus

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*


class DashboardActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        loadAnalytics()
    }

    private fun loadAnalytics() {

        db.collection("reports")
            .get()
            .addOnSuccessListener { documents ->

                val typeCounts = mutableMapOf<String, Int>()
                val riskCounts = mutableMapOf<String, Int>()

                for (doc in documents) {

                    val type = doc.getString("issueType") ?: "Other"
                    val risk = doc.getString("riskLevel") ?: "LOW"

                    typeCounts[type] = typeCounts.getOrDefault(type, 0) + 1
                    riskCounts[risk] = riskCounts.getOrDefault(risk, 0) + 1
                }

                setupBarChart(typeCounts)
                setupPieChart(riskCounts)
            }
    }

    private fun setupBarChart(data: Map<String, Int>) {

        val entries = ArrayList<BarEntry>()
        var index = 0

        data.forEach { (_, value) ->
            entries.add(BarEntry(index.toFloat(), value.toFloat()))
            index++
        }

        val dataSet = BarDataSet(entries, "Reports by Type")
        val barData = BarData(dataSet)

        val chart = findViewById<BarChart>(R.id.barChart)
        chart.data = barData
        chart.invalidate()
        val labels = data.keys.toList()

        chart.xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return labels.getOrNull(value.toInt()) ?: ""
            }
        }

        chart.xAxis.granularity = 1f
        chart.description.text = "Reports by Type"

        chart.animateY(1000)
        chart.setFitBars(true)
        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
        chart.legend.isEnabled = true
    }

    private fun setupPieChart(data: Map<String, Int>) {

        val entries = ArrayList<PieEntry>()

        data.forEach { (key, value) ->
            entries.add(PieEntry(value.toFloat(), key))
        }

        val dataSet = PieDataSet(entries, "Risk Distribution")
        val pieData = PieData(dataSet)

        val chart = findViewById<PieChart>(R.id.pieChart)
        chart.data = pieData
        chart.invalidate()
        chart.setUsePercentValues(true)
        chart.description.text = "Risk Distribution"
        chart.centerText = "Risk Levels"

        chart.animateY(1000)
        chart.setUsePercentValues(true)
        chart.setEntryLabelTextSize(12f)
        chart.centerText = "Risk Breakdown"
        chart.description.isEnabled = false
    }
}*/