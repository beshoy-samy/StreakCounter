package com.streakcounter.core.presentation.format

import kotlinx.datetime.LocalDate

/** Whole number in the phone's locale (grouping, digits): "1,234" in English, "1.234" in German, "١٢" in Arabic. */
fun formatCount(value: Int): String = formatCount(value, currentLanguageTag())

/**
 * "25 Nov" in the current year, "3 Jul 2023" otherwise, in the phone's locale (the locale picks
 * order, punctuation and digits). Without [today] the year is left out.
 */
fun formatShortDate(date: LocalDate, today: LocalDate? = null): String =
    formatShortDate(date, today, currentLanguageTag())

/**
 * [date] in the phone's locale from a CLDR skeleton (fields only, the locale picks order and
 * punctuation): [DateSkeleton.HEADER] gives "Thursday, 27 November" (en-GB).
 */
fun formatDate(date: LocalDate, skeleton: String): String = formatDate(date, skeleton, currentLanguageTag())

object DateSkeleton {
    /** Weekday, day, month: the list header. */
    const val HEADER = "EEEEdMMMM"

    /** Day and short month: "25 Nov". */
    const val DAY_MONTH = "dMMM"

    /** Day, short month and year: "3 Jul 2023". */
    const val DAY_MONTH_YEAR = "dMMMy"
}

internal fun formatShortDate(date: LocalDate, today: LocalDate?, languageTag: String): String = formatDate(
    date,
    if (today == null || date.year == today.year) DateSkeleton.DAY_MONTH else DateSkeleton.DAY_MONTH_YEAR,
    languageTag,
)

/** BCP 47 tag of the phone's current locale. */
internal expect fun currentLanguageTag(): String

// The tag is a parameter so tests can pin a locale. Formatters are cached per tag (they are costly
// to create), so a language change simply asks for a new one.
internal expect fun formatCount(value: Int, languageTag: String): String

internal expect fun formatDate(date: LocalDate, skeleton: String, languageTag: String): String
