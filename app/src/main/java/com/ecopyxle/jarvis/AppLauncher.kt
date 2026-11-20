package com.ecopyxle.jarvis

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import java.util.Locale

class AppLauncher(private val context: Context) {
    fun open(name: String): ActionResult {
        val target = resolveLauncher(name)
            ?: return ActionResult(false, "I couldn't find $name on this device.", "$name is not installed.")
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(target.activityInfo.packageName)
                ?: return ActionResult(false, "I couldn't open $name.", "The app has no launch activity.")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ActionResult(true, "Opening ${target.loadLabel(context.packageManager)}.")
        } catch (exception: SecurityException) {
            ActionResult(false, "I couldn't open $name.", exception.message ?: "Launch permission denied.")
        }
    }

    fun openFirstAvailable(vararg names: String): ActionResult {
        names.forEach { name ->
            resolveLauncher(name)?.let { return open(name) }
        }
        return ActionResult(false, "I couldn't find ${names.firstOrNull() ?: "that app"} on this device.")
    }

    private fun resolveLauncher(name: String): ResolveInfo? {
        val wanted = name.normalized()
        val aliases = when (wanted) {
            "maps", "google maps" -> setOf("maps", "google maps")
            "photos", "gallery" -> setOf("photos", "gallery", "google photos")
            "phone", "dialer" -> setOf("phone", "dialer")
            "messages", "messaging" -> setOf("messages", "messaging")
            else -> setOf(wanted)
        }.map { it.normalized() }.toSet()
        return launcherApps().firstOrNull { info ->
            val label = info.loadLabel(context.packageManager).toString().normalized()
            label in aliases || aliases.any { label.contains(it) || it.contains(label) }
        }
    }

    @Suppress("DEPRECATION")
    private fun launcherApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
    }

    private fun String.normalized(): String =
        lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
}
