package com.example.geminivoiceassistant // Make sure this matches your package name

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// This data class represents the entire state of our UI at any given moment
data class UiState(
    val displayedText: String = "Press the mic and ask a question...",
    val isLoading: Boolean = false,
    val error: String? = null
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    fun getResponseStream(globalInstructions: String, additionalInstructions: String, prompt: String) {
        _uiState.update { it.copy(isLoading = true, displayedText = "") }

        // Combine all instructions into a single, structured prompt
        val finalPrompt = buildString {
            if (globalInstructions.isNotBlank()) {
                append("Global Instruction: $globalInstructions\n\n")
            }
            if (additionalInstructions.isNotBlank()) {
                append("Additional Instruction for this query: $additionalInstructions\n\n")
            }
            append("User's question: $prompt")
        }

        viewModelScope.launch {
            try {
                var currentText = ""
                generativeModel.generateContentStream(finalPrompt).collect { chunk ->
                    currentText += chunk.text
                    _uiState.update {
                        it.copy(displayedText = currentText)
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Error: ${e.localizedMessage}")
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}