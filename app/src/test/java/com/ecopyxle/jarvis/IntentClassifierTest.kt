package com.ecopyxle.jarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentClassifierTest {
    private val classifier = IntentClassifier()

    @Test
    fun recognizesEnglishHindiAndTeluguAppCommands() {
        assertEquals(
            "youtube",
            classifier.classify("Hey Jarvis, open YouTube", VoiceLanguage.ENGLISH).parameters["app"]
        )
        assertEquals(
            "youtube",
            classifier.classify("YouTube kholo", VoiceLanguage.HINDI).parameters["app"]
        )
        assertEquals(
            "youtube",
            classifier.classify("YouTube open cheyyi", VoiceLanguage.TELUGU).parameters["app"]
        )
    }

    @Test
    fun recognizesMultilingualDeviceControls() {
        assertEquals(
            "on",
            classifier.classify("Turn on the flashlight", VoiceLanguage.ENGLISH).action
        )
        assertEquals(
            "on",
            classifier.classify("Torch chalu karo", VoiceLanguage.HINDI).action
        )
        assertEquals(
            "up",
            classifier.classify("Volume penchu", VoiceLanguage.TELUGU).action
        )
        assertEquals(
            "down",
            classifier.classify("Volume kam karo", VoiceLanguage.HINDI).action
        )
    }

    @Test
    fun marksCallsAsConfirmationRequiredAndSupportsAlarmTime() {
        val call = classifier.classify("Call my brother", VoiceLanguage.ENGLISH)
        assertEquals(JarvisIntent.CALL_CONTACT, call.intent)
        assertTrue(call.requiresConfirmation)
        assertTrue(ConfirmationPolicy.requiresApproval(call, confirmed = false))
        assertFalse(ConfirmationPolicy.requiresApproval(call, confirmed = true))

        assertEquals(
            "bhai",
            classifier.classify("Mere bhai ko call karo", VoiceLanguage.HINDI).parameters["contact"]
        )
        assertEquals(
            "brother",
            classifier.classify("Na brother ki call cheyyi", VoiceLanguage.TELUGU).parameters["contact"]
        )

        val alarm = classifier.classify("Subah 6 baje alarm lagao", VoiceLanguage.HINDI)
        assertEquals(JarvisIntent.SET_ALARM, alarm.intent)
        assertEquals("6", alarm.parameters["hour"])
        assertEquals(
            "6",
            classifier.classify("Udayam 6 gantalki alarm pettu", VoiceLanguage.TELUGU).parameters["hour"]
        )

        assertEquals(
            JarvisIntent.SET_TIMER,
            classifier.classify("Set a timer for 5 minutes", VoiceLanguage.ENGLISH).intent
        )
    }

    @Test
    fun rejectsUnrecognizedCommandsAndConfirmationIsNotDefault() {
        val unknown = classifier.classify("do something impossible", VoiceLanguage.ENGLISH)
        assertEquals(JarvisIntent.UNKNOWN, unknown.intent)
        assertFalse(unknown.requiresConfirmation)
    }

    @Test
    fun extractsPayloadsForMessagesNotesTasksAndTimers() {
        val sms = classifier.classify("Send SMS saying I am on my way", VoiceLanguage.ENGLISH)
        assertEquals(JarvisIntent.COMPOSE_SMS, sms.intent)
        assertEquals("i am on my way", sms.parameters["text"])

        val email = classifier.classify("Email John about the meeting", VoiceLanguage.ENGLISH)
        assertEquals(JarvisIntent.COMPOSE_EMAIL, email.intent)
        assertEquals("john about the meeting", email.parameters["text"])

        val note = classifier.classify("Make a note buy milk", VoiceLanguage.ENGLISH)
        assertEquals(JarvisIntent.CREATE_NOTE, note.intent)
        assertEquals("buy milk", note.parameters["text"])

        val task = classifier.classify("Add task call the dentist", VoiceLanguage.ENGLISH)
        assertEquals(JarvisIntent.ADD_TASK, task.intent)
        assertEquals("call the dentist", task.parameters["text"])

        val timer = classifier.classify("Set a timer for 10 seconds", VoiceLanguage.ENGLISH)
        assertEquals("10", timer.parameters["amount"])
        assertEquals("seconds", timer.parameters["unit"])
    }

    @Test
    fun parsesAlarmMinutesAndWebsites() {
        val alarm = classifier.classify("Set an alarm for 7 30 pm", VoiceLanguage.ENGLISH)
        assertEquals(JarvisIntent.SET_ALARM, alarm.intent)
        assertEquals("7", alarm.parameters["hour"])
        assertEquals("30", alarm.parameters["minute"])
        assertEquals("pm", alarm.parameters["period"])

        val website = classifier.classify("Open example.com", VoiceLanguage.ENGLISH)
        assertEquals(JarvisIntent.OPEN_WEBSITE, website.intent)
        assertEquals("example.com", website.parameters["website"])
    }
}
