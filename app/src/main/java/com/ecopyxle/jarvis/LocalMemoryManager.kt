package com.ecopyxle.jarvis

import android.content.Context

class LocalMemoryManager(context: Context) {
    private val preferences = context.getSharedPreferences("jarvis_local_memory", Context.MODE_PRIVATE)

    fun saveNote(note: String): ActionResult {
        if (note.isBlank()) return ActionResult(false, "Tell me what note to save.")
        val notes = preferences.getStringSet(KEY_NOTES, emptySet()).orEmpty().toMutableSet()
        notes.add(note)
        preferences.edit().putStringSet(KEY_NOTES, notes).apply()
        return ActionResult(true, "Note saved.")
    }

    fun notes(): List<String> = preferences.getStringSet(KEY_NOTES, emptySet()).orEmpty().toList()

    fun saveTask(task: String): ActionResult {
        if (task.isBlank()) return ActionResult(false, "Tell me what task to add.")
        val tasks = preferences.getStringSet(KEY_TASKS, emptySet()).orEmpty().toMutableSet()
        tasks.add(task)
        preferences.edit().putStringSet(KEY_TASKS, tasks).apply()
        return ActionResult(true, "Task added.")
    }

    fun completeTask(task: String): ActionResult {
        val tasks = preferences.getStringSet(KEY_TASKS, emptySet()).orEmpty().toMutableSet()
        val match = tasks.firstOrNull { task.isBlank() || it.contains(task, ignoreCase = true) }
            ?: return ActionResult(false, "I couldn't find that task.")
        tasks.remove(match)
        preferences.edit().putStringSet(KEY_TASKS, tasks).apply()
        return ActionResult(true, "Task completed.")
    }

    fun tasks(): List<String> = preferences.getStringSet(KEY_TASKS, emptySet()).orEmpty().toList()

    fun saveReminder(reminder: String): ActionResult {
        if (reminder.isBlank()) return ActionResult(false, "Tell me what reminder to save.")
        val reminders = preferences.getStringSet(KEY_REMINDERS, emptySet()).orEmpty().toMutableSet()
        reminders.add(reminder)
        preferences.edit().putStringSet(KEY_REMINDERS, reminders).apply()
        return ActionResult(true, "Reminder saved locally. No alert is scheduled.")
    }

    fun reminders(): List<String> =
        preferences.getStringSet(KEY_REMINDERS, emptySet()).orEmpty().toList()

    companion object {
        private const val KEY_NOTES = "notes"
        private const val KEY_TASKS = "tasks"
        private const val KEY_REMINDERS = "reminders"
    }
}
