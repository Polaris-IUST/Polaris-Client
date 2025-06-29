package com.example.polaris_client.views.ui.tests

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.polaris_client.utils.NetworkTestEntry
import com.example.polaris_client.R
import java.text.SimpleDateFormat
import java.util.*

class TestResultAdapter(private var results: List<NetworkTestEntry>) : 
    RecyclerView.Adapter<TestResultAdapter.ResultViewHolder>() {

    fun updateData(newResults: List<NetworkTestEntry>) {
        results = newResults
        notifyDataSetChanged()
    }
    
    class ResultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val resultText: TextView = view.findViewById(R.id.result_value)
        val timestampText: TextView = view.findViewById(R.id.timestamp_value)
        val additionalInfoText: TextView = view.findViewById(R.id.additional_info_value)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_test_result, parent, false)
        return ResultViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val entry = results[position]
        
        when (entry.testType) {
            "HTTP" -> {
                holder.resultText.text = "${entry.result} Kbps"
                holder.additionalInfoText.text = "Duration: ${entry.additionalInfo} seconds"
            }
            "PING" -> {
                holder.resultText.text = "${entry.result} ms"
                holder.additionalInfoText.text = "Count: ${entry.additionalInfo}"
            }
            "DNS" -> {
                holder.resultText.text = "${entry.result} ms"
                holder.additionalInfoText.text = "Host: ${entry.additionalInfo}"
            }
            "WEB" -> {
                holder.resultText.text = "${entry.result} ms"
                holder.additionalInfoText.text = "URL: ${entry.additionalInfo}"
            }
            "SMS" -> {
                holder.resultText.text = "${entry.result} ms"
                holder.additionalInfoText.text = "Phone: ${entry.additionalInfo}"
            }
            "SPEED_DOWN" -> {
                holder.resultText.text = "${entry.result} Mbps"
                holder.additionalInfoText.text = "Download: ${entry.additionalInfo}"
            }
            "SPEED_UP" -> {
                holder.resultText.text = "${entry.result} Mbps"
                holder.additionalInfoText.text = "Upload: ${entry.additionalInfo}"
            }
            "SPEED_FULL" -> {
                holder.resultText.text = "Speed Test"
                holder.additionalInfoText.text = entry.result
            }
            else -> {
                holder.resultText.text = entry.result
                holder.additionalInfoText.text = entry.additionalInfo
            }
        }
        
        // Format timestamp
        holder.timestampText.text = formatTimestamp(entry.timestamp)
    }
    
    override fun getItemCount() = results.size
    private fun formatTimestamp(timestamp: String): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return try {
            val date = Date(timestamp.toLong())
            dateFormat.format(date)
        } catch (e: Exception) {
            timestamp
        }
    }
} 