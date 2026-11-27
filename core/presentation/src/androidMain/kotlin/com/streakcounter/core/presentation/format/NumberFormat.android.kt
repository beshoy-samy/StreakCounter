package com.streakcounter.core.presentation.format

import android.icu.text.NumberFormat
import android.icu.text.SimpleDateFormat
import android.icu.util.GregorianCalendar
import android.icu.util.TimeZone
import android.text.format.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.datetime.LocalDate

private val numberFormats = ConcurrentHashMap<String, NumberFormat>()
private val dateFormats = ConcurrentHashMap<Pair<String, String>, SimpleDateFormat>()

private val utc = TimeZone.getTimeZone("UTC")

internal actual fun currentLanguageTag(): String = Locale.getDefault().toLanguageTag()

/**
 * Arabic shows Arabic-Indic digits (the design's choice) whatever the region's default numbering
 * is: some Arabic regions default to Latin digits. An explicit numbering extension in the tag
 * (the user asked for Latin digits in system settings) is kept.
 */
private fun localeOf(tag: String): Locale {
    val locale = Locale.forLanguageTag(tag)
    return if (locale.language == "ar" && locale.getUnicodeLocaleType("nu") == null) {
        Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "arab").build()
    } else {
        locale
    }
}

// ICU (not java.text) so digits and calendar data match the rest of the system.
internal actual fun formatCount(value: Int, languageTag: String): String {
    val format = numberFormats.getOrPut(languageTag) { NumberFormat.getIntegerInstance(localeOf(languageTag)) }
    // The formats keep state while formatting, so one instance serves one caller at a time.
    return synchronized(format) { format.format(value.toLong()) }
}

internal actual fun formatDate(date: LocalDate, skeleton: String, languageTag: String): String {
    val format = dateFormats.getOrPut(languageTag to skeleton) {
        val locale = localeOf(languageTag)
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).apply {
            // LocalDate is ISO (Gregorian), shown as such; noon UTC keeps the day whatever the zone.
            calendar = GregorianCalendar(utc, locale)
            timeZone = utc
        }
    }
    val noon = java.time.LocalDate.of(date.year, date.month.ordinal + 1, date.day)
        .atTime(12, 0).toInstant(java.time.ZoneOffset.UTC)
    return synchronized(format) { format.format(Date.from(noon)) }
}
