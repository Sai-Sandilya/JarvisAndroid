package com.ecopyxle.jarvis

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

class ReminderManager(private val context: Context) {
    fun setAlarm(hourText: String?, minuteText: String?, period: String?): ActionResult {
        val hour = hourText?.toIntOrNull()
            ?: return ActionResult(false, "Tell me the alarm time, for example 6 AM.")
        val minute = minuteText?.toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59) return ActionResult(false, "That alarm time is invalid.")
        val hour24 = when (period?.lowercase()) {
            "pm" -> if (hour in 1..11) hour + 12 else hour
            "am" -> if (hour == 12) 0 else hour
            else -> hour
        }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour24)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launch(intent, "Opening the alarm screen for %02d:%02d.".format(hour24, minute))
    }

    fun setTimer(amountText: String?, unit: String?): ActionResult {
        val amount = amountText?.toIntOrNull()
            ?: return ActionResult(false, "Tell me the timer duration.")
        val unitText = unit.orEmpty()
        val seconds = when {
            unitText.startsWith("hour") || unitText.contains("घंटा") || unitText.contains("గంట") -> amount * 3600
            unitText.startsWith("second") || unitText.contains("सेकंड") -> amount
            else -> amount * 60
        }
        if (seconds <= 0) return ActionResult(false, "Timer duration must be greater than zero.")
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launch(intent, "Opening the timer screen for $amount $unitText.")
    }

    private fun launch(intent: Intent, response: String): ActionResult = try {
        context.startActivity(intent)
        ActionResult(true, response)
    } catch (exception: Exception) {
        ActionResult(false, "No clock app can handle that request.", exception.message)
    }
}
