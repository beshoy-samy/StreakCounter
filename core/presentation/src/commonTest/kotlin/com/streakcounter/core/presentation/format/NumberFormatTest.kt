package com.streakcounter.core.presentation.format

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.datetime.LocalDate

class NumberFormatTest : LocaleTestBase() {
    private val today = LocalDate(2026, 11, 27)

    @Test
    fun de_uses_dot_grouping() {
        assertThat(formatCount(1234, "de")).isEqualTo("1.234")
    }

    @Test
    fun en_uses_comma_grouping() {
        assertThat(formatCount(1234, "en")).isEqualTo("1,234")
        assertThat(formatCount(0, "en")).isEqualTo("0")
    }

    @Test
    fun ar_uses_arabic_indic_digits() {
        assertThat(formatCount(12, "ar")).isEqualTo("١٢")
    }

    @Test
    fun repeated_calls_reuse_the_formatter_and_stay_correct() {
        repeat(3) {
            assertThat(formatCount(1234, "de")).isEqualTo("1.234")
            assertThat(formatCount(1234, "en")).isEqualTo("1,234")
        }
    }

    @Test
    fun en_short_date() {
        assertThat(formatShortDate(LocalDate(2026, 11, 25), today, "en-GB")).isEqualTo("25 Nov")
    }

    @Test
    fun short_date_shows_the_year_only_when_not_the_current_one() {
        assertThat(formatShortDate(LocalDate(2023, 7, 3), today, "en-GB")).isEqualTo("3 Jul 2023")
        assertThat(formatShortDate(LocalDate(2023, 7, 3), null, "en-GB")).isEqualTo("3 Jul")
    }

    @Test
    fun de_short_date_has_dot_after_the_day() {
        assertThat(formatShortDate(LocalDate(2026, 11, 25), today, "de")).isEqualTo("25. Nov.")
        assertThat(formatShortDate(LocalDate(2023, 7, 3), today, "de")).isEqualTo("3. Juli 2023")
    }

    @Test
    fun ar_short_date_uses_arabic_indic_digits() {
        // Separators differ between the platforms' CLDR versions; the digits are what matters.
        val text = formatShortDate(LocalDate(2023, 7, 3), today, "ar")
        assertThat(text.filter { it.isDigit() }).isEqualTo("٣٢٠٢٣")
    }

    @Test
    fun header_date() {
        // The comma after the weekday comes and goes between CLDR versions.
        assertThat(formatDate(today, DateSkeleton.HEADER, "en-GB").replace(",", "")).isEqualTo("Friday 27 November")
        assertThat(formatDate(today, DateSkeleton.HEADER, "de")).isEqualTo("Freitag, 27. November")
        assertThat(formatDate(today, DateSkeleton.HEADER, "ar").filter { it.isDigit() }).isEqualTo("٢٧")
    }
}
