package com.ecopyxle.jarvis

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer

class SpeechRecognitionManager(private val context: Context) {
    fun createOnDeviceRecognizer(listener: RecognitionListener): SpeechRecognizer =
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }

    fun recognitionIntent(languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
}
