package com.example.polaris_client.views.ui.tests

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.polaris_client.utils.DatabaseHelper
import com.example.polaris_client.controllers.NetworkTestService
import com.example.polaris_client.R
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class WebTestFragment : Fragment() {

    private lateinit var urlInput: TextInputEditText
    private lateinit var startButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var resultText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var networkTestService: NetworkTestService
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_web_test, container, false)

        urlInput = root.findViewById(R.id.url_input)
        startButton = root.findViewById(R.id.start_test_button)
        progressBar = root.findViewById(R.id.progress_bar)
        resultText = root.findViewById(R.id.result_text)
        recyclerView = root.findViewById(R.id.results_recycler_view)

        recyclerView.layoutManager = LinearLayoutManager(context)
        
        networkTestService = NetworkTestService(requireContext())
        dbHelper = DatabaseHelper(requireContext())

        startButton.setOnClickListener {
            val url = urlInput.text.toString()
            if (url.isEmpty()) {
                Toast.makeText(context, "Please enter a URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Add http:// prefix if not present
            val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            
            runTest(formattedUrl)
        }
        
        loadPreviousResults()

        return root
    }
    
    private fun runTest(url: String) {
        progressBar.visibility = View.VISIBLE
        startButton.isEnabled = false
        resultText.text = "Running web test..."
        
        lifecycleScope.launch {
            val result = networkTestService.runWebTest(url)
            progressBar.visibility = View.GONE
            startButton.isEnabled = true
            
            if (result >= 0) {
                resultText.text = "Web response time: ${String.format("%.2f", result)} ms"
            } else {
                resultText.text = "Error running web test. Please check the URL and try again."
            }
            
            loadPreviousResults()
        }
    }
    
    private fun loadPreviousResults() {
        val results = dbHelper.getNetworkTestResults("WEB")
        recyclerView.adapter = TestResultAdapter(results)
    }
} 