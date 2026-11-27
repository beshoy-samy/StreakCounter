package com.streakcounter.feature.streaks.presentation.format

import kotlinx.datetime.LocalDate

/** Whole number in the phone's locale (grouping, digits): "1,234" in English, "1.234" in German. */
internal expect fun formatNumber(value: Int): String

/**
 * [date] in the phone's locale from a CLDR skeleton (fields only, the locale picks order and
 * punctuation): [DateSkeleton.HEADER] gives "Thursday, 27 November" (en-GB).
 */
internal expect fun formatDate(date: LocalDate, skeleton: String): String

internal object DateSkeleton {
    /** Weekday, day, month: the list header. */
    const val HEADER = "EEEEdMMMM"

    /** Day and short month: "25 Nov". */
    const val DAY_MONTH = "dMMM"

    /** Day, short month and year: "3 Jul 2023". */
    const val DAY_MONTH_YEAR = "dMMMy"
}
