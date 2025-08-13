package com.example.geminivoiceassistant // Make sure this matches your package name

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    // UI elements
    private lateinit var responseText: TextView
    private lateinit var micButton: ImageButton
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var fabIncreaseFont: FloatingActionButton
    private lateinit var fabDecreaseFont: FloatingActionButton
    private lateinit var globalInstructionsEditText: TextInputEditText
    private lateinit var additionalInstructionsEditText: TextInputEditText

    // State variable for font size
    private var currentFontSize = 14f

    // SharedPreferences for saving global instructions
    private val sharedPreferences by lazy {
        getSharedPreferences("GeminiAppSettings", Context.MODE_PRIVATE)
    }

    private val speechResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!spokenText.isNullOrEmpty()) {
                    // Get instructions from text boxes
                    val globalInstructions = globalInstructionsEditText.text.toString()
                    val additionalInstructions = additionalInstructionsEditText.text.toString()

                    // Save the global instructions for next time
                    sharedPreferences.edit().putString("global_instructions", globalInstructions).apply()

                    // Call the ViewModel with all the information
                    viewModel.getResponseStream(globalInstructions, additionalInstructions, spokenText[0])

                    // Clear the one-time instruction box after use
                    additionalInstructionsEditText.text?.clear()
                }
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startSpeechToText()
            } else {
                Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.decorView.setBackgroundColor(getColor(R.color.app_background_color))

        // Initialize all UI views
        initializeViews()
        loadSavedInstructions()
        observeUiState()
        setupClickListeners()
    }

    private fun initializeViews() {
        responseText = findViewById(R.id.responseText)
        micButton = findViewById(R.id.micButton)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        fabIncreaseFont = findViewById(R.id.fabIncreaseFont)
        fabDecreaseFont = findViewById(R.id.fabDecreaseFont)
        globalInstructionsEditText = findViewById(R.id.globalInstructionsEditText)
        additionalInstructionsEditText = findViewById(R.id.additionalInstructionsEditText)
        responseText.textSize = currentFontSize
    }

    private fun loadSavedInstructions() {
        val savedInstructions = sharedPreferences.getString("global_instructions", "")
        globalInstructionsEditText.setText(savedInstructions)
    }

    private fun setupClickListeners() {
        micButton.setOnClickListener {
            checkPermissionAndStartSpeech()
        }

        fabIncreaseFont.setOnClickListener {
            currentFontSize += 2f
            responseText.textSize = currentFontSize
        }

        fabDecreaseFont.setOnClickListener {
            if (currentFontSize > 10f) { // Set a minimum font size
                currentFontSize -= 2f
                responseText.textSize = currentFontSize
            }
        }
    }

    private fun observeUiState() {
        val markwon = Markwon.create(this@MainActivity)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    markwon.setMarkdown(responseText, state.displayedText)
                    loadingIndicator.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    micButton.isEnabled = !state.isLoading
                    fabIncreaseFont.isEnabled = !state.isLoading
                    fabDecreaseFont.isEnabled = !state.isLoading
                    state.error?.let {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun checkPermissionAndStartSpeech() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startSpeechToText()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startSpeechToText() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }
        try {
            speechResultLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Speech recognition not available on this device.", Toast.LENGTH_SHORT).show()
        }
    }
}