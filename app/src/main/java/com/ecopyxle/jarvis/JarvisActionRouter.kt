package com.ecopyxle.jarvis

import android.content.Context

data class RouteResult(
    val result: ActionResult,
    val awaitingConfirmation: Boolean = false
)

class JarvisActionRouter(context: Context) {
    private val appLauncher = AppLauncher(context)
    private val deviceControls = DeviceControlManager(context)
    private val webActions = WebActionManager(context)
    private val communication = CommunicationManager(context)
    private val reminders = ReminderManager(context)
    private val memory = LocalMemoryManager(context)
    private val responses = JarvisResponseManager()

    fun execute(command: JarvisCommand, confirmed: Boolean = false): RouteResult {
        if (ConfirmationPolicy.requiresApproval(command, confirmed)) {
            return RouteResult(
                ActionResult(false, confirmationPrompt(command)),
                awaitingConfirmation = true
            )
        }
        val result = when (command.intent) {
            JarvisIntent.OPEN_APP -> appLauncher.open(command.parameters["app"].orEmpty())
            JarvisIntent.FLASHLIGHT -> deviceControls.setFlashlight(command.action == "on")
            JarvisIntent.VOLUME -> deviceControls.adjustVolume(
                command.action,
                command.parameters["stream"].orEmpty()
            )
            JarvisIntent.OPEN_SETTINGS -> deviceControls.openSettings(command.action)
            JarvisIntent.BATTERY_STATUS -> deviceControls.batteryStatus()
            JarvisIntent.WEB_SEARCH -> webActions.search(
                command.parameters["query"].orEmpty(),
                command.action
            )
            JarvisIntent.OPEN_WEBSITE -> webActions.openWebsite(command.parameters["website"].orEmpty())
            JarvisIntent.MAPS_NAVIGATE -> webActions.navigate(command.parameters["destination"].orEmpty())
            JarvisIntent.DATE_TIME -> ActionResult(
                true,
                responses.dateTime(command.language)
            )
            JarvisIntent.SET_ALARM -> reminders.setAlarm(
                command.parameters["hour"],
                command.parameters["minute"],
                command.parameters["period"]
            )
            JarvisIntent.SET_TIMER -> reminders.setTimer(
                command.parameters["amount"],
                command.parameters["unit"]
            )
            JarvisIntent.CALL_CONTACT -> communication.callContact(command.parameters["contact"].orEmpty())
            JarvisIntent.WHATSAPP_CHAT -> communication.openWhatsAppChat(command.parameters["contact"].orEmpty())
            JarvisIntent.COMPOSE_SMS -> communication.composeSms(command.parameters["text"].orEmpty())
            JarvisIntent.COMPOSE_EMAIL -> communication.composeEmail(command.parameters["text"].orEmpty())
            JarvisIntent.CONTACT_DETAILS -> communication.showContacts()
            JarvisIntent.CREATE_NOTE -> memory.saveNote(command.parameters["text"].orEmpty())
            JarvisIntent.LIST_NOTES, JarvisIntent.READ_NOTES -> listResult(memory.notes(), "notes")
            JarvisIntent.CREATE_REMINDER -> memory.saveReminder(command.parameters["text"].orEmpty())
            JarvisIntent.LIST_REMINDERS -> listResult(memory.reminders(), "reminders")
            JarvisIntent.ADD_TASK -> memory.saveTask(command.parameters["text"].orEmpty())
            JarvisIntent.LIST_TASKS -> listResult(memory.tasks(), "tasks")
            JarvisIntent.COMPLETE_TASK -> memory.completeTask(command.parameters["text"].orEmpty())
            JarvisIntent.UNKNOWN -> ActionResult(false, "I don't understand that command yet.")
        }
        return RouteResult(result)
    }

    private fun confirmationPrompt(command: JarvisCommand): String = when (command.intent) {
        JarvisIntent.CALL_CONTACT -> "Do you want to open Contacts to call ${command.parameters["contact"] ?: "that contact"}?"
        JarvisIntent.WHATSAPP_CHAT -> "Open WhatsApp to message ${command.parameters["contact"] ?: "that contact"}?"
        JarvisIntent.COMPOSE_SMS -> "Open the SMS composer? You will review the message before sending."
        JarvisIntent.COMPOSE_EMAIL -> "Open the email composer? You will review it before sending."
        else -> "Do you want me to continue?"
    }

    private fun listResult(items: List<String>, type: String): ActionResult =
        if (items.isEmpty()) ActionResult(true, "You don't have any saved $type yet.")
        else ActionResult(true, "Your saved $type: ${items.joinToString(". ")}")
}

class VoiceCommandManager(
    private val classifier: IntentClassifier,
    private val router: JarvisActionRouter
) {
    fun process(text: String, preferredLanguage: String, confirmed: Boolean = false): Pair<JarvisCommand, RouteResult> {
        val command = classifier.classify(text, preferredLanguage)
        return command to router.execute(command, confirmed)
    }
}
