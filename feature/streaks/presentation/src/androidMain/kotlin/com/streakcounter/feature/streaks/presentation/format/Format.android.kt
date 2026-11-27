package com.streakcounter.feature.streaks.presentation.format

import android.text.format.DateFormat
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate

internal actual fun formatNumber(value: Int): String = NumberFormat.getIntegerInstance().format(value)

internal actual fun formatDate(date: LocalDate, skeleton: String): String {
    val locale = Locale.getDefault()
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return DateTimeFormatter.ofPattern(pattern, locale).format(date.toJavaLocalDate())
}
