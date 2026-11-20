package com.ecopyxle.jarvis

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings

class DeviceControlManager(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun setFlashlight(enabled: Boolean): ActionResult {
        val cameraManager = context.getSystemService(CameraManager::class.java)
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ActionResult(false, "This device doesn't expose a flashlight.")
            cameraManager.setTorchMode(cameraId, enabled)
            ActionResult(true, if (enabled) "Flashlight on." else "Flashlight off.")
        } catch (exception: SecurityException) {
            ActionResult(
                false,
                "Camera permission is needed to control the flashlight.",
                exception.message ?: "Camera permission denied.",
                requiresPermission = true
            )
        } catch (exception: Exception) {
            ActionResult(false, "I couldn't change the flashlight.", exception.message)
        }
    }

    fun adjustVolume(direction: String, stream: String): ActionResult {
        val streamType = if (stream == "ring") AudioManager.STREAM_RING else AudioManager.STREAM_MUSIC
        val adjustment = when (direction) {
            "down" -> AudioManager.ADJUST_LOWER
            "mute" -> AudioManager.ADJUST_MUTE
            "unmute" -> AudioManager.ADJUST_UNMUTE
            else -> AudioManager.ADJUST_RAISE
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(streamType, adjustment, 0)
            } else {
                @Suppress("DEPRECATION")
                audioManager.adjustStreamVolume(streamType, adjustment, 0)
            }
            ActionResult(true, when (direction) {
                "down" -> "Volume decreased."
                "mute" -> if (stream == "ring") "Ringtone muted." else "Media muted."
                "unmute" -> if (stream == "ring") "Ringtone unmuted." else "Media unmuted."
                else -> "Volume increased."
            })
        } catch (exception: SecurityException) {
            ActionResult(false, "Android doesn't allow this volume change.", exception.message)
        }
    }

    fun openSettings(screen: String): ActionResult {
        val action = when (screen) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "mobile" -> Settings.ACTION_DATA_ROAMING_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return openSystemScreen(action, "Opening $screen settings.")
    }

    fun batteryStatus(): ActionResult {
        val battery = context.registerReceiver(null, IntentFilterCompat.batteryChanged())
            ?: return ActionResult(false, "Battery status is unavailable.")
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val percentage = (level * 100 / scale).coerceIn(0, 100)
        val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return ActionResult(true, "Battery is $percentage percent${if (charging) " and charging" else ""}.")
    }

    private fun openSystemScreen(action: String, response: String): ActionResult = try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionResult(true, response)
    } catch (exception: Exception) {
        ActionResult(false, "That settings screen isn't available.", exception.message)
    }
}

private object IntentFilterCompat {
    fun batteryChanged() = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
}
