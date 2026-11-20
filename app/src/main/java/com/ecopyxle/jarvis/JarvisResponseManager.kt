package com.ecopyxle.jarvis

import java.text.DateFormat
import java.util.Date

class JarvisResponseManager {
    fun dateTime(language: String): String {
        val locale = java.util.Locale.forLanguageTag(language)
        return DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, locale).format(Date())
    }
}
