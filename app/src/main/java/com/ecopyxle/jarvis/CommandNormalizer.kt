package com.ecopyxle.jarvis

import java.util.Locale

data class NormalizedCommand(val text: String, val language: String)

class CommandNormalizer {
    fun normalize(input: String, preferredLanguage: String): NormalizedCommand {
        val language = when {
            input.any { it in '\u0900'..'\u097F' } -> VoiceLanguage.HINDI
            input.any { it in '\u0C00'..'\u0C7F' } -> VoiceLanguage.TELUGU
            else -> preferredLanguage
        }
        val text = input.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removeWakePhrase()
            .trim()
        return NormalizedCommand(text, language)
    }

    private fun String.removeWakePhrase(): String =
        replace(Regex("^(hey\\s+)?jarvis\\b\\s*"), "")
            .replace(Regex("^हे\\s*जार्विस\\s*"), "")
            .replace(Regex("^జార్విస్\\s*"), "")
}
