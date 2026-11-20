package com.ecopyxle.jarvis

enum class JarvisIntent {
    OPEN_APP,
    FLASHLIGHT,
    VOLUME,
    OPEN_SETTINGS,
    BATTERY_STATUS,
    WEB_SEARCH,
    OPEN_WEBSITE,
    MAPS_NAVIGATE,
    DATE_TIME,
    SET_ALARM,
    SET_TIMER,
    CALL_CONTACT,
    WHATSAPP_CHAT,
    COMPOSE_SMS,
    COMPOSE_EMAIL,
    CONTACT_DETAILS,
    CREATE_NOTE,
    LIST_NOTES,
    READ_NOTES,
    CREATE_REMINDER,
    LIST_REMINDERS,
    ADD_TASK,
    LIST_TASKS,
    COMPLETE_TASK,
    UNKNOWN
}

enum class ExecutionStatus {
    READY,
    NEEDS_CONFIRMATION,
    SUCCESS,
    FAILED,
    NEEDS_PERMISSION
}

data class JarvisCommand(
    val intent: JarvisIntent,
    val action: String,
    val parameters: Map<String, String>,
    val language: String,
    val requiresConfirmation: Boolean,
    val executionStatus: ExecutionStatus = ExecutionStatus.READY,
    val errorMessage: String? = null,
    val originalText: String = ""
)

data class ActionResult(
    val success: Boolean,
    val response: String,
    val errorMessage: String? = null,
    val requiresPermission: Boolean = false
)

object ConfirmationPolicy {
    fun requiresApproval(command: JarvisCommand, confirmed: Boolean): Boolean =
        command.requiresConfirmation && !confirmed
}
