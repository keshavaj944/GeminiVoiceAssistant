package com.example.geminivoiceassistant // Make sure this matches your package name

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import com.google.android.material.textfield.TextInputLayout
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    // UI elements
    private lateinit var responseText: TextView
    private lateinit var micButton: ImageButton
    private lateinit var listeningAnimation: LottieAnimationView
    private lateinit var fabIncreaseFont: FloatingActionButton
    private lateinit var fabDecreaseFont: FloatingActionButton
    private lateinit var globalInstructionsLayout: TextInputLayout // Changed to layout
    private lateinit var globalInstructionsEditText: TextInputEditText
    private lateinit var additionalInstructionsEditText: TextInputEditText

    // Speech Recognizer
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var finalUserTranscript: String = ""
    private val markwon by lazy { Markwon.create(this) }
    private var currentFontSize = 14f
    private val sharedPreferences by lazy {
        getSharedPreferences("GeminiAppSettings", Context.MODE_PRIVATE)
    }

    // Launcher for the system file picker
    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                readTextFromUri(it)
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                toggleListening()
            } else {
                Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
        speechRecognizer?.destroy()
    }

    private fun initializeViews() {
        responseText = findViewById(R.id.responseText)
        micButton = findViewById(R.id.micButton)
        listeningAnimation = findViewById(R.id.listeningAnimation)
        fabIncreaseFont = findViewById(R.id.fabIncreaseFont)
        fabDecreaseFont = findViewById(R.id.fabDecreaseFont)
        globalInstructionsLayout = findViewById(R.id.globalInstructionsLayout) // Initialize layout
        globalInstructionsEditText = findViewById(R.id.globalInstructionsEditText)
        additionalInstructionsEditText = findViewById(R.id.additionalInstructionsEditText)
        responseText.textSize = currentFontSize
    }

    private fun loadSavedInstructions() {
        val savedGlobal = sharedPreferences.getString("global_instructions", "")
        globalInstructionsEditText.setText(savedGlobal)
        val savedAdditional = sharedPreferences.getString("additional_instructions", "")
        additionalInstructionsEditText.setText(savedAdditional)
    }

    private fun setupClickListeners() {
        micButton.setOnClickListener {
            checkPermissionAndToggleListening()
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

        // Set listener for the new "open file" icon
        globalInstructionsLayout.setEndIconOnClickListener {
            // Launch the file picker to select a text file
            filePickerLauncher.launch(arrayOf("text/plain"))
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (!isListening) {
                        val fullMarkdownString = buildString {
                            if (finalUserTranscript.isNotEmpty()) {
                                append("**You said:** ${finalUserTranscript}\n\n---\n\n")
                            }
                            append(state.displayedText)
                        }
                        markwon.setMarkdown(responseText, fullMarkdownString)
                    }

                    if (state.isLoading && !isListening) {
                        listeningAnimation.visibility = View.VISIBLE
                        listeningAnimation.playAnimation()
                    } else if (!isListening) {
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
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                stopListening(isError = false)
            }

            override fun onError(error: Int) {
                stopListening(isError = true)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    finalUserTranscript = matches[0]
                    val globalInstructions = globalInstructionsEditText.text.toString()
                    val additionalInstructions = additionalInstructionsEditText.text.toString()

                    sharedPreferences.edit()
                        .putString("global_instructions", globalInstructions)
                        .putString("additional_instructions", additionalInstructions)
                        .apply()

                    viewModel.getResponseStream(globalInstructions, additionalInstructions, finalUserTranscript)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    responseText.text = matches[0]
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer?.setRecognitionListener(recognitionListener)
    }

    private fun checkPermissionAndToggleListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            toggleListening()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun toggleListening() {
        if (isListening) {
            stopListening(isError = false)
        } else {
            startListening()
        }
    }

    private fun startListening() {
        responseText.text = ""
        finalUserTranscript = ""
        isListening = true
        micButton.setImageResource(android.R.drawable.ic_media_pause)
        listeningAnimation.visibility = View.VISIBLE
        listeningAnimation.playAnimation()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening(isError: Boolean) {
        if (isListening) {
            isListening = false
            speechRecognizer?.stopListening()
            listeningAnimation.visibility = View.GONE
            listeningAnimation.cancelAnimation()
            micButton.setImageResource(android.R.drawable.ic_btn_speak_now)
            if (isError) {
                Toast.makeText(this, "Didn't catch that. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // New function to read text from the selected file URI
    private fun readTextFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val stringBuilder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line).append("\n")
                    }
                    globalInstructionsEditText.setText(stringBuilder.toString())
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to read file", Toast.LENGTH_SHORT).show()
        }
    }
}