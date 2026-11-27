package com.streakcounter.core.presentation.format

import kotlin.concurrent.AtomicReference
import kotlinx.datetime.LocalDate
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

// Copy-on-write maps: the formatters themselves are thread-safe on iOS, so only the map needs care.
private val numberFormatters = AtomicReference<Map<String, NSNumberFormatter>>(emptyMap())
private val dateFormatters = AtomicReference<Map<Pair<String, String>, NSDateFormatter>>(emptyMap())

private fun <K, V> AtomicReference<Map<K, V>>.getOrPut(key: K, create: () -> V): V {
    value[key]?.let { return it }
    val made = create()
    while (true) {
        val seen = value
        seen[key]?.let { return it }
        if (compareAndSet(seen, seen + (key to made))) return made
    }
}

/**
 * Arabic shows Arabic-Indic digits (the design's choice) whatever the region's default numbering
 * is: some Arabic regions default to Latin digits. An explicit numbering in the identifier
 * ("@numbers=latn", the user asked for Latin digits in system settings) is kept.
 */
private fun localeOf(tag: String): NSLocale {
    val arabic = tag.substringBefore('@').split('-', '_').first() == "ar"
    return NSLocale(localeIdentifier = if (arabic && "numbers=" !in tag) "$tag${if ('@' in tag) ";" else "@"}numbers=arab" else tag)
}

internal actual fun currentLanguageTag(): String = NSLocale.currentLocale.localeIdentifier

internal actual fun formatCount(value: Int, languageTag: String): String {
    val formatter = numberFormatters.getOrPut(languageTag) {
        NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterDecimalStyle
            locale = localeOf(languageTag)
        }
    }
    return formatter.stringFromNumber(NSNumber(int = value)) ?: value.toString()
}

internal actual fun formatDate(date: LocalDate, skeleton: String, languageTag: String): String {
    // LocalDate is ISO (Gregorian); the formatter then shows it in the locale's own calendar.
    val gregorian = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
    val components = NSDateComponents().apply {
        year = date.year.toLong()
        month = date.month.ordinal.toLong() + 1
        day = date.day.toLong()
        hour = 12
    }
    val nsDate = gregorian.dateFromComponents(components) ?: return date.toString()
    val formatter = dateFormatters.getOrPut(languageTag to skeleton) {
        NSDateFormatter().apply {
            locale = localeOf(languageTag)
            setLocalizedDateFormatFromTemplate(skeleton)
        }
    }
    return formatter.stringFromDate(nsDate)
}
