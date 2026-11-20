package com.ecopyxle.jarvis

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import java.util.Locale

class VoiceRecognitionService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var selectedLanguage = VoiceLanguage.ENGLISH
    private var shouldListen = false
    private var ttsReady = false
    private var pendingSpeech: Pair<String, String>? = null
    private var restartRunnable: Runnable? = null
    private lateinit var commandManager: VoiceCommandManager
    private lateinit var speechRecognitionManager: SpeechRecognitionManager

    override fun onCreate() {
        super.onCreate()
        commandManager = VoiceCommandManager(
            IntentClassifier(),
            JarvisActionRouter(this)
        )
        speechRecognitionManager = SpeechRecognitionManager(this)
        createNotificationChannel()
        textToSpeech = TextToSpeech(this) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) {
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            if (shouldListen) {
                                scheduleListening(RESTART_AFTER_SPEECH_MS)
                            } else if (!VoiceSession.state.value.active &&
                                VoiceSession.state.value.pendingCommand == null
                            ) {
                                stopSelf()
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post { scheduleListening(RESTART_DELAY_MS) }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        mainHandler.post { scheduleListening(RESTART_DELAY_MS) }
                    }
                })
                pendingSpeech?.let { (text, languageTag) ->
                    pendingSpeech = null
                    speakOffline(text, languageTag)
                }
            } else {
                VoiceSession.update {
                    it.copy(error = "Text-to-speech is unavailable; install an offline voice.")
                }
                if (!VoiceSession.state.value.active &&
                    VoiceSession.state.value.pendingCommand == null
                ) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVoiceSession()
            ACTION_EXECUTE_COMMAND -> {
                if (VoiceSession.state.value.active) shouldListen = true
                intent.getStringExtra(EXTRA_COMMAND)?.let { processCommand(it) }
            }
            ACTION_RESUME_LISTENING -> {
                shouldListen = VoiceSession.state.value.active
                if (shouldListen) scheduleListening(0L) else stopSelf()
            }
            ACTION_CONFIRM -> {
                val approved = intent.getBooleanExtra(EXTRA_APPROVED, false)
                val command = VoiceSession.state.value.pendingCommand
                if (command != null) {
                    if (approved) {
                        shouldListen = VoiceSession.state.value.active
                        processCommand(command, confirmed = true)
                    } else {
                        VoiceSession.update {
                            it.copy(
                                pendingCommand = null,
                                currentAction = "",
                                actionStatus = "Cancelled",
                                response = "Cancelled."
                            )
                        }
                        shouldListen = VoiceSession.state.value.active
                        if (shouldListen) scheduleListening(RESTART_DELAY_MS)
                    }
                }
            }
            ACTION_SET_LANGUAGE -> {
                intent.getStringExtra(EXTRA_LANGUAGE)?.let(::changeLanguage)
            }
            else -> {
                val requestedLanguage = intent?.getStringExtra(EXTRA_LANGUAGE)
                    ?: selectedLanguage
                startVoiceSession(requestedLanguage)
            }
        }
        return START_NOT_STICKY
    }

    private fun startVoiceSession(languageTag: String) {
        selectedLanguage = supportedLanguage(languageTag)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            failVoiceSession("Microphone permission is required for voice mode.")
            return
        }

        startAsForeground()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            failVoiceSession("No speech recognition service is installed on this device.")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        ) {
            failVoiceSession(
                "On-device speech recognition is unavailable. Install an offline speech model " +
                    "for this language and try again."
            )
            return
        }

        shouldListen = true
        VoiceSession.update {
            it.copy(
                active = true,
                status = "Starting on-device recognition…",
                languageTag = selectedLanguage,
                error = null
            )
        }
        createRecognizer()
    }

    private fun changeLanguage(languageTag: String) {
        selectedLanguage = supportedLanguage(languageTag)
        VoiceSession.update {
            it.copy(
                languageTag = selectedLanguage,
                transcript = "",
                response = "",
                error = null,
                status = if (it.active) "Switching recognition language…" else it.status
            )
        }
        updateNotification()
        if (shouldListen) {
            cancelScheduledListening()
            releaseRecognizer()
            createRecognizer()
        }
    }

    private fun createRecognizer() {
        if (!shouldListen) return
        speechRecognizer = speechRecognitionManager.createOnDeviceRecognizer(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    VoiceSession.update {
                        it.copy(
                            active = true,
                            status = "Listening in ${languageLabel(selectedLanguage)}",
                            error = null
                        )
                    }
                }

                override fun onBeginningOfSpeech() {
                    VoiceSession.update { it.copy(status = "Listening…") }
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    VoiceSession.update { it.copy(status = "Processing on device…") }
                }

                override fun onError(error: Int) {
                    handleRecognitionError(error)
                }

                override fun onResults(results: android.os.Bundle?) {
                    val transcript = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (transcript.isNotEmpty()) handleTranscript(transcript)
                    else scheduleListening(RESTART_DELAY_MS)
                }

                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotEmpty()) {
                        VoiceSession.update { it.copy(transcript = partial) }
                    }
                }

                override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            })
        startRecognition()
    }

    private fun startRecognition() {
        val recognizer = speechRecognizer ?: return
        if (!shouldListen) return
        val intent = speechRecognitionManager.recognitionIntent(selectedLanguage)
        try {
            recognizer.startListening(intent)
        } catch (exception: IllegalStateException) {
            VoiceSession.update {
                it.copy(status = "Restarting speech recognition…")
            }
            scheduleListening(RESTART_DELAY_MS)
        }
    }

    private fun handleTranscript(transcript: String) {
        val normalized = CommandNormalizer().normalize(transcript, selectedLanguage).text
        if (normalized.isBlank()) {
            VoiceSession.update { it.copy(response = wakeResponse()) }
            if (!speakOffline(wakeResponse(), selectedLanguage)) {
                scheduleListening(RESTART_DELAY_MS)
            }
            return
        }
        processCommand(transcript)
    }

    private fun processCommand(transcript: String, confirmed: Boolean = false) {
        val (command, route) = commandManager.process(transcript, selectedLanguage, confirmed)
        val result = route.result
        val language = command.language
        val status = when {
            route.awaitingConfirmation -> "Confirmation required"
            result.requiresPermission -> "Permission required"
            result.success -> "Completed"
            else -> "Failed"
        }
        val response = localizedResponse(result.response, command)
        val historyLine = "${transcript.trim()} — $status"
        VoiceSession.update { current ->
            current.copy(
                active = current.active,
                status = if (route.awaitingConfirmation) "Waiting for your confirmation" else current.status,
                transcript = transcript,
                response = response,
                detectedLanguage = language,
                currentAction = command.intent.name.replace('_', ' ').lowercase()
                    .replaceFirstChar { it.uppercase() },
                actionStatus = status,
                pendingCommand = if (route.awaitingConfirmation || result.requiresPermission) transcript else null,
                history = (listOf(historyLine) + current.history).take(8),
                error = result.errorMessage ?: if (result.requiresPermission) result.response else null
            )
        }
        if (route.awaitingConfirmation) {
            shouldListen = false
            cancelScheduledListening()
            releaseRecognizer()
        } else if (result.requiresPermission) {
            shouldListen = false
            cancelScheduledListening()
            releaseRecognizer()
        }
        if (!speakOffline(response, language)) {
            if (shouldListen) scheduleListening(RESTART_DELAY_MS)
            else if (!VoiceSession.state.value.active &&
                VoiceSession.state.value.pendingCommand == null
            ) stopSelf()
        }
    }

    private fun localizedResponse(response: String, command: JarvisCommand): String {
        return when (command.language) {
            VoiceLanguage.HINDI -> when (command.intent) {
                JarvisIntent.OPEN_APP -> "${command.parameters["app"] ?: "ऐप"} खोल रहा हूँ।"
                JarvisIntent.FLASHLIGHT -> if (command.action == "on") "टॉर्च चालू है।" else "टॉर्च बंद है।"
                JarvisIntent.VOLUME -> when (command.action) {
                    "down" -> "आवाज़ कम कर दी।"
                    "mute" -> "मीडिया म्यूट कर दिया।"
                    "unmute" -> "मीडिया की आवाज़ चालू कर दी।"
                    else -> "आवाज़ बढ़ा दी।"
                }
                JarvisIntent.OPEN_SETTINGS -> "सेटिंग्स खोल रहा हूँ।"
                else -> response
            }
            VoiceLanguage.TELUGU -> when (command.intent) {
                JarvisIntent.OPEN_APP -> "${command.parameters["app"] ?: "యాప్"} తెరుస్తున్నాను."
                JarvisIntent.FLASHLIGHT -> if (command.action == "on") "టార్చ్ ఆన్ చేశాను." else "టార్చ్ ఆఫ్ చేశాను."
                JarvisIntent.VOLUME -> when (command.action) {
                    "down" -> "వాల్యూమ్ తగ్గించాను."
                    "mute" -> "మీడియా మ్యూట్ చేశాను."
                    "unmute" -> "మీడియా అన్‌మ్యూట్ చేశాను."
                    else -> "వాల్యూమ్ పెంచాను."
                }
                JarvisIntent.OPEN_SETTINGS -> "సెట్టింగ్స్ తెరుస్తున్నాను."
                else -> response
            }
            else -> response
        }
    }

    private fun handleRecognitionError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleListening(RESTART_DELAY_MS)
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> failVoiceSession(
                "The on-device speech recognizer does not support " +
                    "${languageLabel(selectedLanguage)}. Choose another recognition language."
            )
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> handleLanguageUnavailable()
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> failVoiceSession(
                "Microphone permission was revoked. Enable it in Android Settings to continue."
            )
            else -> failVoiceSession(
                "On-device speech recognition stopped (error $error). " +
                    "Check that the offline speech service is available."
            )
        }
    }

    private fun handleLanguageUnavailable() {
        val modelMessage =
            "The offline speech model for ${languageLabel(selectedLanguage)} is not installed."
        val recognizer = speechRecognizer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && recognizer != null) {
            try {
                recognizer.triggerModelDownload(speechRecognitionManager.recognitionIntent(selectedLanguage))
                failVoiceSession(
                    "$modelMessage A download was requested. Connect to the internet, " +
                        "wait for it to finish, then start voice mode again."
                )
                return
            } catch (exception: IllegalStateException) {
                failVoiceSession(
                    "$modelMessage Android could not start the download. Install the language " +
                        "in your device's speech-recognition settings, then try again."
                )
                return
            }
        }

        failVoiceSession(
            "$modelMessage Install the language in your device's speech-recognition settings, " +
                "then try again."
        )
    }

    private fun speakOffline(text: String, languageTag: String): Boolean {
        val tts = textToSpeech
        if (tts == null || !ttsReady) {
            pendingSpeech = text to languageTag
            VoiceSession.update {
                it.copy(error = null)
            }
            return true
        }

        val locale = Locale.forLanguageTag(languageTag)
        val voices = tts.voices.orEmpty()
        val voice = voices.firstOrNull {
            it.locale == locale && !it.isNetworkConnectionRequired
        } ?: voices.firstOrNull {
            it.locale.language == locale.language && !it.isNetworkConnectionRequired
        }
        if (voice == null) {
            VoiceSession.update {
                it.copy(
                    error = "No offline text-to-speech voice is installed for " +
                        "${languageLabel(languageTag)}."
                )
            }
            return false
        }

        tts.voice = voice
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (result == TextToSpeech.ERROR) {
            VoiceSession.update { it.copy(error = "The offline TTS voice could not speak.") }
            return false
        }
        return true
    }

    private fun wakeResponse(): String = when (selectedLanguage) {
        VoiceLanguage.HINDI -> "जी, बताइए।"
        VoiceLanguage.TELUGU -> "చెప్పండి, వింటున్నాను."
        else -> "Yes? I'm listening."
    }

    private fun scheduleListening(delayMillis: Long) {
        if (!shouldListen) return
        cancelScheduledListening()
        val runnable = Runnable {
            if (shouldListen) startRecognition()
        }
        restartRunnable = runnable
        mainHandler.postDelayed(runnable, delayMillis)
    }

    private fun cancelScheduledListening() {
        restartRunnable?.let(mainHandler::removeCallbacks)
        restartRunnable = null
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, VoiceRecognitionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis voice mode")
            .setContentText("Listening in ${languageLabel(selectedLanguage)}")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                Notification.Action.Builder(null, "Stop", stopIntent).build()
            )
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Jarvis voice mode",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification shown while Jarvis uses the microphone."
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun failVoiceSession(message: String) {
        shouldListen = false
        cancelScheduledListening()
        releaseRecognizer()
        VoiceSession.update {
            it.copy(active = false, status = "Voice mode unavailable", error = message)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopVoiceSession() {
        shouldListen = false
        cancelScheduledListening()
        releaseRecognizer()
        VoiceSession.update {
            it.copy(
                active = false,
                status = "Voice mode is off",
                transcript = "",
                response = "",
                error = null
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseRecognizer() {
        speechRecognizer?.let {
            it.cancel()
            it.destroy()
        }
        speechRecognizer = null
    }

    private fun supportedLanguage(tag: String): String =
        VoiceLanguage.supported.firstOrNull { it.tag == tag }?.tag
            ?: VoiceLanguage.ENGLISH

    private fun languageLabel(tag: String): String =
        VoiceLanguage.supported.firstOrNull { it.tag == tag }?.label ?: "English"

    override fun onDestroy() {
        shouldListen = false
        cancelScheduledListening()
        releaseRecognizer()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        if (VoiceSession.state.value.active) {
            VoiceSession.update {
                it.copy(active = false, status = "Voice mode stopped")
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.ecopyxle.jarvis.action.START_VOICE"
        const val ACTION_STOP = "com.ecopyxle.jarvis.action.STOP_VOICE"
        const val ACTION_SET_LANGUAGE = "com.ecopyxle.jarvis.action.SET_LANGUAGE"
        const val ACTION_EXECUTE_COMMAND = "com.ecopyxle.jarvis.action.EXECUTE_COMMAND"
        const val ACTION_CONFIRM = "com.ecopyxle.jarvis.action.CONFIRM"
        const val ACTION_RESUME_LISTENING = "com.ecopyxle.jarvis.action.RESUME_LISTENING"
        const val EXTRA_LANGUAGE = "com.ecopyxle.jarvis.extra.LANGUAGE"
        const val EXTRA_COMMAND = "com.ecopyxle.jarvis.extra.COMMAND"
        const val EXTRA_APPROVED = "com.ecopyxle.jarvis.extra.APPROVED"

        private const val CHANNEL_ID = "jarvis_voice"
        private const val NOTIFICATION_ID = 101
        private const val UTTERANCE_ID = "jarvis-response"
        private const val RESTART_DELAY_MS = 700L
        private const val RESTART_AFTER_SPEECH_MS = 500L
    }
}
