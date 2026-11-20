package com.ecopyxle.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri

class WebActionManager(private val context: Context) {
    fun search(query: String, provider: String): ActionResult {
        if (query.isBlank()) return ActionResult(false, "Tell me what you want to search for.")
        val url = if (provider == "youtube") {
            "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
        } else {
            "https://www.google.com/search?q=${Uri.encode(query)}"
        }
        return openUri(url, if (provider == "youtube") "Searching YouTube for $query." else "Searching Google for $query.")
    }

    fun openWebsite(website: String): ActionResult {
        val url = if (website.startsWith("http")) website else "https://$website"
        return openUri(url, "Opening $website.")
    }

    fun navigate(destination: String): ActionResult {
        if (destination.isBlank()) return ActionResult(false, "Tell me the destination for directions.")
        val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
        return openUri(uri.toString(), "Opening directions to $destination in Maps.")
    }

    private fun openUri(uri: String, response: String): ActionResult = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        ActionResult(true, response)
    } catch (exception: Exception) {
        ActionResult(false, "I couldn't open that link.", exception.message)
    }
}
