package com.ecopyxle.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.core.content.ContextCompat

private val Background = Color(0xFF07111C)
private val Panel = Color(0xFF102333)
private val Cyan = Color(0xFF35D9F5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val voiceState by VoiceSession.state.collectAsState()
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
                    startVoiceService(voiceState.languageTag)
                } else {
                    VoiceSession.update {
                        it.copy(
                            error = "Microphone permission is required for voice mode.",
                            status = "Microphone permission needed"
                        )
                    }
                }
            }
            val cameraPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                val command = VoiceSession.state.value.pendingCommand
                if (granted && command != null) {
                    executeCommand(command)
                } else {
                    VoiceSession.update {
                        it.copy(
                            pendingCommand = null,
                            actionStatus = "Permission denied",
                            error = "Camera permission was denied; flashlight control is unavailable."
                        )
                    }
                    startService(
                        Intent(this, VoiceRecognitionService::class.java)
                            .setAction(VoiceRecognitionService.ACTION_RESUME_LISTENING)
                    )
                }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Background,
                    surface = Panel,
                    primary = Cyan
                )
            ) {
                JarvisDashboard(
                    voiceState = voiceState,
                    onStart = {
                        if (ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            startVoiceService(voiceState.languageTag)
                        } else {
                            val permissions = buildList {
                                add(Manifest.permission.RECORD_AUDIO)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            permissionLauncher.launch(permissions.toTypedArray())
                        }
                    },
                    onStop = { stopVoiceService() },
                    onLanguageSelected = { languageTag ->
                        VoiceSession.update { it.copy(languageTag = languageTag, error = null) }
                        if (voiceState.active) {
                            startService(
                                Intent(this, VoiceRecognitionService::class.java)
                                    .setAction(VoiceRecognitionService.ACTION_SET_LANGUAGE)
                                    .putExtra(VoiceRecognitionService.EXTRA_LANGUAGE, languageTag)
                            )
                        }
                    },
                    onCommand = { executeCommand(it) },
                    onConfirm = { approved ->
                        startService(
                            Intent(this, VoiceRecognitionService::class.java)
                                .setAction(VoiceRecognitionService.ACTION_CONFIRM)
                                .putExtra(VoiceRecognitionService.EXTRA_APPROVED, approved)
                        )
                    },
                    onGrantCameraPermission = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }
        }
    }

    private fun startVoiceService(languageTag: String) {
        val intent = Intent(this, VoiceRecognitionService::class.java)
            .setAction(VoiceRecognitionService.ACTION_START)
            .putExtra(VoiceRecognitionService.EXTRA_LANGUAGE, languageTag)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopVoiceService() {
        startService(
            Intent(this, VoiceRecognitionService::class.java)
                .setAction(VoiceRecognitionService.ACTION_STOP)
        )
    }

    private fun executeCommand(command: String) {
        startService(
            Intent(this, VoiceRecognitionService::class.java)
                .setAction(VoiceRecognitionService.ACTION_EXECUTE_COMMAND)
                .putExtra(VoiceRecognitionService.EXTRA_COMMAND, command)
        )
    }
}

@Composable
fun JarvisDashboard(
    voiceState: VoiceUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onCommand: (String) -> Unit,
    onConfirm: (Boolean) -> Unit,
    onGrantCameraPermission: () -> Unit
) {
    var manualCommand by remember { mutableStateOf("") }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(35.dp))
            Text(
                "J.A.R.V.I.S",
                color = Cyan,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 5.sp
            )
            Text(
                "PERSONAL AI ASSISTANT",
                color = Color.LightGray,
                fontSize = 11.sp,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(42.dp))

            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(Panel, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(Color(0xFF17394B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "J",
                        color = Cyan,
                        fontSize = 68.sp,
                        fontWeight = FontWeight.Light
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                if (voiceState.active) "VOICE MODE ACTIVE" else "SYSTEM READY",
                color = Cyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(voiceState.status, color = Color.LightGray, fontSize = 14.sp)
            if (voiceState.currentAction.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${voiceState.currentAction} • ${voiceState.actionStatus}",
                    color = if (voiceState.actionStatus == "Failed") Color(0xFFFF8A80) else Cyan,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "ASSISTANT CONFIGURATION",
                        color = Cyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    LanguageSelector(voiceState.languageTag, onLanguageSelected)
                    StatusRow("Speech recognition", "On-device")
                    StatusRow("Speech responses", "Offline voices only")
                    StatusRow("Wake phrase", "Hey Jarvis (speech match)")
                    StatusRow("Microphone", if (voiceState.active) "In use" else "Off")
                    StatusRow(
                        "Detected language",
                        VoiceLanguage.supported.firstOrNull { it.tag == voiceState.detectedLanguage }?.label
                            ?: voiceState.detectedLanguage
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("COMMAND", color = Cyan, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualCommand,
                        onValueChange = { manualCommand = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Try: open YouTube") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                if (manualCommand.isNotBlank()) {
                                    onCommand(manualCommand)
                                    manualCommand = ""
                                }
                            }
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onCommand(manualCommand)
                            manualCommand = ""
                        },
                        enabled = manualCommand.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("EXECUTE COMMAND")
                    }
                }
            }

            if (voiceState.transcript.isNotBlank() || voiceState.response.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("YOU", color = Cyan, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(voiceState.transcript, color = Color.White)
                        if (voiceState.response.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Text("JARVIS", color = Cyan, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(voiceState.response, color = Color.White)
                        }
                    }
                }
            }

            voiceState.pendingCommand?.let { pending ->
                Spacer(Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            if (voiceState.actionStatus == "Permission required") "PERMISSION REQUIRED"
                            else "CONFIRM ACTION",
                            color = Cyan,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(pending, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        if (voiceState.actionStatus == "Permission required") {
                            Button(onClick = onGrantCameraPermission, modifier = Modifier.fillMaxWidth()) {
                                Text("GRANT CAMERA PERMISSION")
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { onConfirm(true) }, modifier = Modifier.weight(1f)) {
                                    Text("CONFIRM")
                                }
                                OutlinedButton(onClick = { onConfirm(false) }, modifier = Modifier.weight(1f)) {
                                    Text("CANCEL")
                                }
                            }
                        }
                    }
                }
            }

            if (voiceState.history.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("RECENT COMMANDS", color = Cyan, fontWeight = FontWeight.Bold)
                        voiceState.history.forEach { entry ->
                            Spacer(Modifier.height(7.dp))
                            Text(entry, color = Color.LightGray, fontSize = 13.sp)
                        }
                    }
                }
            }

            voiceState.error?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(message, color = Color(0xFFFF8A80), fontSize = 13.sp)
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = if (voiceState.active) onStop else onStart,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(if (voiceState.active) "STOP VOICE MODE" else "START VOICE MODE")
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "JARVIS ANDROID • ON-DEVICE VOICE",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun LanguageSelector(selectedTag: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = VoiceLanguage.supported.first { it.tag == selectedTag }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Recognition language", color = Color.LightGray, fontSize = 13.sp)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(selected.label, color = Color.White)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                VoiceLanguage.supported.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.label) },
                        onClick = {
                            expanded = false
                            onSelected(language.tag)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.LightGray, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp)
    }
}
