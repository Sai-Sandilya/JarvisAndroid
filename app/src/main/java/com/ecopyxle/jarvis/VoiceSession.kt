package com.ecopyxle.jarvis

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceUiState(
    val active: Boolean = false,
    val status: String = "Voice mode is off",
    val languageTag: String = VoiceLanguage.ENGLISH,
    val transcript: String = "",
    val response: String = "",
    val detectedLanguage: String = VoiceLanguage.ENGLISH,
    val currentAction: String = "",
    val actionStatus: String = "",
    val pendingCommand: String? = null,
    val history: List<String> = emptyList(),
    val error: String? = null
)

data class VoiceLanguage(val tag: String, val label: String) {
    companion object {
        const val ENGLISH = "en-IN"
        const val HINDI = "hi-IN"
        const val TELUGU = "te-IN"

        val supported = listOf(
            VoiceLanguage(ENGLISH, "English"),
            VoiceLanguage(HINDI, "हिन्दी"),
            VoiceLanguage(TELUGU, "తెలుగు")
        )
    }
}

object VoiceSession {
    private val mutableState = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = mutableState.asStateFlow()

    fun update(transform: (VoiceUiState) -> VoiceUiState) {
        mutableState.value = transform(mutableState.value)
    }
}
