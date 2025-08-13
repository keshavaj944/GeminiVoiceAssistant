package com.example.geminivoiceassistant // Make sure this matches your package name

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    // UI elements
    private lateinit var responseText: TextView
    private lateinit var micButton: ImageButton
    private lateinit var listeningAnimation: LottieAnimationView
    private lateinit var fabIncreaseFont: FloatingActionButton
    private lateinit var fabDecreaseFont: FloatingActionButton
    private lateinit var globalInstructionsEditText: TextInputEditText
    private lateinit var additionalInstructionsEditText: TextInputEditText

    // Speech Recognizer
    private var speechRecognizer: SpeechRecognizer? = null

    private var currentFontSize = 14f
    private val sharedPreferences by lazy {
        getSharedPreferences("GeminiAppSettings", Context.MODE_PRIVATE)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startListening()
            } else {
                Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.decorView.setBackgroundColor(getColor(R.color.app_background_color))

        // Check if speech recognition is available on the device
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition is not available on this device.", Toast.LENGTH_LONG).show()
        } else {
            setupSpeechRecognizer()
        }

        initializeViews()
        loadSavedInstructions()
        observeUiState()
        setupClickListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release the speech recognizer resources
        speechRecognizer?.destroy()
    }

    private fun initializeViews() {
        responseText = findViewById(R.id.responseText)
        micButton = findViewById(R.id.micButton)
        listeningAnimation = findViewById(R.id.listeningAnimation)
        fabIncreaseFont = findViewById(R.id.fabIncreaseFont)
        fabDecreaseFont = findViewById(R.id.fabDecreaseFont)
        globalInstructionsEditText = findViewById(R.id.globalInstructionsEditText)
        additionalInstructionsEditText = findViewById(R.id.additionalInstructionsEditText)
        responseText.textSize = currentFontSize
    }

    private fun loadSavedInstructions() {
        val savedGlobal = sharedPreferences.getString("global_instructions", "")
        globalInstructionsEditText.setText(savedGlobal)

        // Add these two lines
        val savedAdditional = sharedPreferences.getString("additional_instructions", "")
        additionalInstructionsEditText.setText(savedAdditional)
    }

    private fun setupClickListeners() {
        micButton.setOnClickListener {
            checkPermissionAndStartListening()
        }

        fabIncreaseFont.setOnClickListener {
            currentFontSize += 2f
            responseText.textSize = currentFontSize
        }

        fabDecreaseFont.setOnClickListener {
            if (currentFontSize > 10f) {
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
                    if (state.isLoading) {
                        listeningAnimation.visibility = View.VISIBLE
                        listeningAnimation.playAnimation()
                    } else {
                        listeningAnimation.visibility = View.GONE
                        listeningAnimation.cancelAnimation()
                    }

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

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val recognitionListener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                micButton.isEnabled = false // Disable button while listening
                listeningAnimation.visibility = View.VISIBLE
                listeningAnimation.playAnimation()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                micButton.isEnabled = true // Re-enable button
                listeningAnimation.visibility = View.GONE
                listeningAnimation.cancelAnimation()
            }

            override fun onError(error: Int) {
                micButton.isEnabled = true // Re-enable button
                listeningAnimation.visibility = View.GONE
                listeningAnimation.cancelAnimation()
                // Provide a more user-friendly error message
                Toast.makeText(this@MainActivity, "Didn't catch that. Please try again.", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                micButton.isEnabled = true
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0]
                    val globalInstructions = globalInstructionsEditText.text.toString()
                    val additionalInstructions = additionalInstructionsEditText.text.toString()

                    // Change this block
                    sharedPreferences.edit()
                        .putString("global_instructions", globalInstructions)
                        .putString("additional_instructions", additionalInstructions) // Add this line
                        .apply()

                    viewModel.getResponseStream(globalInstructions, additionalInstructions, spokenText)

                    // REMOVE this line:
                    // additionalInstructionsEditText.text?.clear()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer?.setRecognitionListener(recognitionListener)
    }

    private fun checkPermissionAndStartListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
    }
}