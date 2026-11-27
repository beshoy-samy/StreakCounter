package com.streakcounter.feature.streaks.presentation.format

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

internal actual fun formatNumber(value: Int): String {
    val formatter = NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterDecimalStyle
        locale = NSLocale.currentLocale
    }
    return formatter.stringFromNumber(NSNumber(int = value)) ?: value.toString()
}

internal actual fun formatDate(date: LocalDate, skeleton: String): String {
    // LocalDate is ISO (Gregorian); the formatter then shows it in the locale's own calendar.
    val gregorian = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
    val components = NSDateComponents().apply {
        year = date.year.toLong()
        month = date.month.ordinal.toLong() + 1
        day = date.day.toLong()
        hour = 12
    }
    val nsDate = gregorian.dateFromComponents(components) ?: return date.toString()
    val formatter = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        setLocalizedDateFormatFromTemplate(skeleton)
    }
    return formatter.stringFromDate(nsDate)
}
