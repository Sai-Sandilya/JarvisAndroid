package com.ecopyxle.jarvis

import java.util.Locale

class IntentClassifier {
    fun classify(input: String, preferredLanguage: String): JarvisCommand {
        val normalized = CommandNormalizer().normalize(input, preferredLanguage)
        val text = normalized.text
        fun command(
            intent: JarvisIntent,
            action: String,
            parameters: Map<String, String> = emptyMap(),
            confirmation: Boolean = false
        ) = JarvisCommand(
            intent = intent,
            action = action,
            parameters = parameters,
            language = normalized.language,
            requiresConfirmation = confirmation,
            originalText = input
        )

        if (text.isBlank()) return command(JarvisIntent.UNKNOWN, "empty")

        if (matches(text, "torch|flashlight|टॉर्च|फ्लैशलाइट")) {
            val enabled = matches(text, "on|enable|start|chalu|chaalu|చాలూ|చాలా|चालू|जलाओ|చేయి|పెట్టు") &&
                !matches(text, "off|बंद|ఆఫ్")
            return command(JarvisIntent.FLASHLIGHT, if (enabled) "on" else "off")
        }
        if (matches(text, "volume|media|आवाज़|वॉल्यूम|సౌండ్|వాల్యూమ్")) {
            val direction = when {
                matches(text, "increase|up|raise|badhao|बढ़ा|बढा|పెంచు|ఎక్కువ") -> "up"
                matches(text, "decrease|down|lower|kam|ghatao|कम|घटा|తగ్గించు|తక్కువ") -> "down"
                matches(text, "unmute|आवाज़ चालू|అన్ మ్యూట్") -> "unmute"
                matches(text, "mute|म्यूट|మ్యూట్") -> "mute"
                else -> "up"
            }
            val stream = if (matches(text, "ring|रिंग|रिंगटोन|రింగ్")) "ring" else "media"
            return command(JarvisIntent.VOLUME, direction, mapOf("stream" to stream))
        }
        if (matches(text, "battery settings|settings battery|बैटरी सेटिंग|బ్యాటరీ సెట్టింగ్స్")) {
            return command(JarvisIntent.OPEN_SETTINGS, "battery")
        }
        if (matches(text, "battery|बैटरी|బ్యాటరీ|charge|charging")) {
            return command(JarvisIntent.BATTERY_STATUS, "status")
        }
        if (matches(text, "date|time|समय|तारीख|టైమ్|సమయం|తేదీ") &&
            !matches(text, "timer|टाइमर|టైమర్")
        ) {
            return command(JarvisIntent.DATE_TIME, "now")
        }
        if (matches(text, "alarm|अलार्म|జార్మ్|ఉదయం")) {
            val time = Regex("(\\d{1,2})(?:\\s+(\\d{1,2}))?\\s*(am|pm)?").find(text)
            return command(
                JarvisIntent.SET_ALARM,
                "set",
                time?.let { mapOf("hour" to it.groupValues[1], "minute" to it.groupValues[2].ifBlank { "0" }, "period" to it.groupValues[3]) }
                    ?: emptyMap()
            )
        }
        if (matches(text, "remind|reminder|याद दिला|याद रखना|గుర్తు చేయి|రిమైండర్")) {
            return if (matches(text, "list|show|दिखाओ|చూపించు")) {
                command(JarvisIntent.LIST_REMINDERS, "list")
            } else {
                val reminder = text
                    .replace(
                        Regex("^(please\\s+)?(remind me to|remind me|add a reminder|add reminder|set a reminder|reminder)\\s*"),
                        ""
                    )
                    .replace(Regex("^to\\s+"), "")
                    .replace(Regex("मुझे|याद दिलाओ|याद रखना|कि|గుర్తు చేయి|గుర్తు పెట్టు"), "")
                    .trim()
                command(JarvisIntent.CREATE_REMINDER, "save", mapOf("text" to reminder))
            }
        }
        if (matches(text, "timer|टाइमर|టైమర్")) {
            val amount = Regex("(\\d+)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|सेकंड|मिनट|घंटा|నిమిషాలు?|గంటలు?)").find(text)
            return command(
                JarvisIntent.SET_TIMER,
                "set",
                amount?.let { mapOf("amount" to it.groupValues[1], "unit" to it.groupValues[2]) } ?: emptyMap()
            )
        }
        if (matches(text, "navigate|directions|route|रास्ता|नेविगेट|దారి|నావిగేట్")) {
            val destination = text
                .replace(Regex("^(please\\s+)?(navigate|directions|route|show|find|go)\\s+(me\\s+)?"), "")
                .replace(Regex("\\b(to|directions|route)\\b|रास्ता|दिखाओ|नेविगेट|దారి|నావిగేట్|కి|కు"), "")
                .replace(Regex("\\b(का|के|को)\\b"), "")
                .trim()
            return command(JarvisIntent.MAPS_NAVIGATE, "navigate", mapOf("destination" to destination))
        }
        if (matches(text, "search|खोजो|ढूंढो|వెతుకు|సెర్చ్") && matches(text, "youtube|यूट्यूब|యూట్యూబ్")) {
            return command(JarvisIntent.WEB_SEARCH, "youtube", mapOf("query" to extractQuery(text)))
        }
        if (matches(text, "search|google|खोजो|ढूंढो|వెతుకు|సెర్చ్")) {
            return command(JarvisIntent.WEB_SEARCH, "google", mapOf("query" to extractQuery(text)))
        }
        if (matches(text, "call|कॉल|फोन करो|కాల్")) {
            val marker = Regex("call|कॉल|फोन|కాల్").find(text)
            val contact = if (marker == null) "" else {
                val before = text.substring(0, marker.range.first)
                val after = text.substring(marker.range.last + 1)
                (if (after.isBlank() || matches(after, "करो|karo|చేయి|cheyyi")) before else after)
                    .replace(Regex("\\b(my|mere|mera|meri|na|naku)\\b"), "")
                    .replace(Regex("\\b(ki|ko)\\b|मेरे|मेरा|मेरी|को|కి|కు"), "")
                    .replace(Regex("^to\\s+"), "")
                    .trim()
            }
            return command(JarvisIntent.CALL_CONTACT, "call", mapOf("contact" to contact), true)
        }
        if (matches(text, "whatsapp") && matches(text, "chat|message|चैट|मैसेज|చాట్|మెసేజ్")) {
            val contact = text.substringAfter("whatsapp")
                .replace(Regex("^(with|chat|message|చాట్|మెసేజ్)\\s*"), "")
                .replace(Regex("\\b(to|with)\\b"), "")
                .trim()
            return command(JarvisIntent.WHATSAPP_CHAT, "chat", mapOf("contact" to contact), true)
        }
        if (matches(text, "sms|text message|message|संदेश|मैसेज|సందేశం|మెసేజ్")) {
            return command(JarvisIntent.COMPOSE_SMS, "compose", mapOf("text" to extractMessage(text)), true)
        }
        if (matches(text, "email|ईमेल|మెయిల్")) {
            return command(JarvisIntent.COMPOSE_EMAIL, "compose", mapOf("text" to extractMessage(text)), true)
        }
        if (matches(text, "contact|संपर्क|కాంటాక్ట్") && matches(text, "show|open|details|दिखाओ|खोलो|చూపించు")) {
            return command(JarvisIntent.CONTACT_DETAILS, "show")
        }
        if (matches(text, "note|नोट|గమనిక")) {
            return when {
                matches(text, "read|सुनाओ|చదువు") -> command(JarvisIntent.READ_NOTES, "read")
                matches(text, "list|show|दिखाओ|చూపించు") -> command(JarvisIntent.LIST_NOTES, "list")
                else -> command(JarvisIntent.CREATE_NOTE, "save", mapOf("text" to extractMessage(text)))
            }
        }
        if (matches(text, "task|todo|काम|कार्य|పని|టాస్క్")) {
            return when {
                matches(text, "complete|done|पूरा|పూర్తి") -> command(JarvisIntent.COMPLETE_TASK, "complete", mapOf("text" to extractMessage(text)))
                matches(text, "list|show|दिखाओ|చూపించు") -> command(JarvisIntent.LIST_TASKS, "list")
                else -> command(JarvisIntent.ADD_TASK, "add", mapOf("text" to extractMessage(text)))
            }
        }
        if (matches(text, "wifi|wi fi|वाई फाई|వైఫై")) return command(JarvisIntent.OPEN_SETTINGS, "wifi")
        if (matches(text, "bluetooth|ब्लूटूथ|బ్లూటూత్")) return command(JarvisIntent.OPEN_SETTINGS, "bluetooth")
        if (matches(text, "mobile network|cellular|मोबाइल नेटवर्क|మొబైల్ నెట్‌వర్క్")) return command(JarvisIntent.OPEN_SETTINGS, "mobile")
        if (matches(text, "display|screen|डिस्प्ले|स्क्रीन|డిస్ప్లే")) return command(JarvisIntent.OPEN_SETTINGS, "display")
        if (matches(text, "location|लोकेशन|स्थान|లొకేషన్")) return command(JarvisIntent.OPEN_SETTINGS, "location")
        if (matches(text, "settings|सेटिंग्स|సెట్టింగ్స్")) return command(JarvisIntent.OPEN_SETTINGS, "settings")

        val app = appName(text)
        if (app != null && matches(text, "open|launch|start|खोलो|खोल|kholo|khol|చేయి|తెరువు|cheyyi|చేయండి")) {
            return command(JarvisIntent.OPEN_APP, "open", mapOf("app" to app))
        }
        val websiteInput = input.lowercase(Locale.ROOT)
        val website = Regex("(?:open|visit)\\s+([a-z0-9.-]+\\.[a-z]{2,})").find(websiteInput)?.groupValues?.get(1)
        if (website != null) return command(JarvisIntent.OPEN_WEBSITE, "open", mapOf("website" to website))
        if (matches(text, "open|launch|start|खोलो|खोल|kholo|khol|खोलें|తెరువు|cheyyi")) {
            val target = text
                .replace(Regex("\\s+(open\\s+cheyyi|cheyyi|खोलो|खोलें|खोल|kholo|khol|తెరువు|చేయి)$"), "")
                .replace(Regex("^(please\\s+|hey\\s+jarvis\\s+)?(open|launch|start)\\s+"), "")
                .trim()
            if (target.isNotBlank()) {
                return command(JarvisIntent.OPEN_APP, "open", mapOf("app" to target))
            }
        }

        return command(JarvisIntent.UNKNOWN, "unknown")
    }

    private fun matches(text: String, terms: String): Boolean =
        terms.split("|").any { term -> text.contains(term.trim(), ignoreCase = true) }

    private fun appName(text: String): String? {
        val aliases = listOf(
            "youtube" to listOf("youtube", "यूट्यूब", "యూట్యూబ్"),
            "whatsapp" to listOf("whatsapp", "व्हाट्सएप", "వాట్సాప్"),
            "chrome" to listOf("chrome", "क्रोम", "క్రోమ్"),
            "maps" to listOf("google maps", "maps", "मैप्स", "मैप", "మ్యాప్స్", "మ్యాప్స్"),
            "camera" to listOf("camera", "कैमरा", "కెమెరా"),
            "photos" to listOf("gallery", "photos", "गैलरी", "फोटो", "గ్యాలరీ", "ఫోటోలు"),
            "settings" to listOf("settings", "सेटिंग्स", "సెట్టింగ్స్"),
            "phone" to listOf("phone", "dialer", "फोन", "డయలర్"),
            "messages" to listOf("messages", "messaging", "संदेश", "मैसेज", "మెసేజ్")
        )
        return aliases.firstOrNull { (_, names) -> names.any { text.contains(it) } }?.first
    }

    private fun extractQuery(text: String): String =
        text.replace(Regex("^(hey )?jarvis\\s*"), "")
            .replace(Regex("^(search|google|खोजो|ढूंढो|వెతుకు|సెర్చ్)\\s*"), "")
            .replace(Regex("^(google|youtube|यूट्यूब|యూట్యూబ్)\\s*"), "")
            .replace(Regex("\\b(for|on|के लिए|पर)\\b"), "")
            .trim()

    private fun extractMessage(text: String): String {
        val markers = listOf(
            "note", "task", "todo", "sms", "text message", "message", "email",
            "नोट", "मैसेज", "संदेश", "గమనిక", "మెసేజ్", "సందేశం", "ईमेल", "మెయిల్"
        )
        var payload = text
        for (marker in markers) {
            if (payload.contains(marker)) {
                payload = payload.substringAfter(marker).trim()
                break
            }
        }
        return payload
            .replace(Regex("^(that\\s+|about\\s+|to\\s+|saying\\s+|कि\\s+|ఇది\\s+|చెబుతూ\\s+)"), "")
            .replace(Regex("\\b(send|compose|add|create|make)\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
