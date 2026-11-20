package com.ecopyxle.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

class CommunicationManager(private val context: Context) {
    private val appLauncher = AppLauncher(context)

    fun callContact(name: String): ActionResult {
        val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
        return open(intent, if (name.isBlank()) "Choose a contact, then confirm the call in the dialer." else
            "Find $name in Contacts and tap Call. Review and place the call in your dialer.")
    }

    fun openWhatsAppChat(contact: String): ActionResult {
        val result = appLauncher.open("whatsapp")
        return if (result.success) {
            ActionResult(true, if (contact.isBlank()) "Choose the WhatsApp contact to message." else
                "Choose $contact in WhatsApp. Review and send the message there.")
        } else {
            result
        }
    }

    fun composeSms(text: String): ActionResult {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
            if (text.isNotBlank()) putExtra("sms_body", text)
        }
        return open(intent, "SMS composer opened. Review the recipient and message before sending.")
    }

    fun composeEmail(text: String): ActionResult {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
        }
        return open(intent, "Email composer opened. Review it before sending.")
    }

    fun showContacts(): ActionResult =
        open(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI), "Opening Contacts.")

    private fun open(intent: Intent, response: String): ActionResult = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        ActionResult(true, response)
    } catch (exception: Exception) {
        ActionResult(false, "The required communication app isn't available.", exception.message)
    }
}
