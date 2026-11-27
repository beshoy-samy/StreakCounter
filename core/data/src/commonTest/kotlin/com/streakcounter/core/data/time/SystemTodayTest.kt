package com.streakcounter.core.data.time

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.streakcounter.core.testing.TestClock.Companion.testClock
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SystemTodayTest {
    private val berlin = TimeZone.of("Europe/Berlin")
    private val tokyo = TimeZone.of("Asia/Tokyo")

    @Test
    fun emits_next_day_after_midnight() = runTest {
        val clock = testClock(Instant.parse("2026-03-10T23:59:00Z"))
        val today = SystemToday(clock, { TimeZone.UTC }, backgroundScope)

        assertThat(today.date.value).isEqualTo(LocalDate(2026, 3, 10))
        advanceTimeBy(59_000)
        runCurrent()
        assertThat(today.date.value).isEqualTo(LocalDate(2026, 3, 10))
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(today.date.value).isEqualTo(LocalDate(2026, 3, 11))
        // The timer re-arms itself: one more midnight, one more day.
        advanceTimeBy(24.hours.inWholeMilliseconds)
        runCurrent()
        assertThat(today.date.value).isEqualTo(LocalDate(2026, 3, 12))
    }

    @Test
    fun refresh_after_zone_change_moves_day() = runTest {
        // 23:30 in Berlin (winter, UTC+1) is 07:30 the next day in Tokyo.
        val clock = testClock(Instant.parse("2026-03-10T22:30:00Z"))
        var zone = berlin
        val today = SystemToday(clock, { zone }, backgroundScope)
        runCurrent()
        assertThat(today.date.value).isEqualTo(LocalDate(2026, 3, 10))

        zone = tokyo
        today.refresh()
        assertThat(today.date.value).isEqualTo(LocalDate(2026, 3, 11))

        // Re-armed for Tokyo: next midnight is 15:00Z, 16.5h from now, not Berlin's 23:00Z.
        advanceTimeBy(16.hours.inWholeMilliseconds + 29.minutes.inWholeMilliseconds)
        runCurrent()
        assertThat(today.date.value).isEqualTo(LocalDate(2026, 3, 11))
        advanceTimeBy(1.minutes.inWholeMilliseconds)
        runCurrent()
        assertThat(today.date.value).isEqualTo(LocalDate(2026, 3, 12))
    }

    @Test
    fun refresh_after_clock_change_moves_day() = runTest {
        val clock = testClock(Instant.parse("2026-03-10T12:00:00Z"))
        val today = SystemToday(clock, { TimeZone.UTC }, backgroundScope)
        runCurrent()

        clock.set(Instant.parse("2026-03-20T12:00:00Z"))
        today.refresh()
        assertThat(today.date.value).isEqualTo(LocalDate(2026, 3, 20))
    }

    @Test
    fun no_duplicate_emission_within_day() = runTest {
        val clock = testClock(Instant.parse("2026-03-10T10:00:00Z"))
        val today = SystemToday(clock, { TimeZone.UTC }, backgroundScope)

        today.date.test {
            assertThat(awaitItem()).isEqualTo(LocalDate(2026, 3, 10))
            today.refresh()
            today.refresh()
            advanceTimeBy(5.hours.inWholeMilliseconds)
            runCurrent()
            today.refresh()
            expectNoEvents()

            // Only the midnight tick emits, once, even after the refreshes re-armed the timer.
            advanceTimeBy(9.hours.inWholeMilliseconds)
            runCurrent()
            assertThat(awaitItem()).isEqualTo(LocalDate(2026, 3, 11))
            expectNoEvents()
        }
    }

    @Test
    fun dst_spring_forward_day_is_23_hours_long() = runTest {
        // Europe/Berlin 2026-03-29: 02:00 CET jumps to 03:00 CEST. Local midnight Mar 29 = 23:00Z.
        val clock = testClock(Instant.parse("2026-03-28T23:00:00Z"))
        val today = SystemToday(clock, { berlin }, backgroundScope)
        runCurrent()
        assertThat(today.date.value).isEqualTo(LocalDate(2026, 3, 29))

        advanceTimeBy(22.hours.inWholeMilliseconds + 59.minutes.inWholeMilliseconds)
        runCurrent()
        assertThat(today.date.value).isEqualTo(LocalDate(2026, 3, 29))
        advanceTimeBy(1.minutes.inWholeMilliseconds)
        runCurrent()
        assertThat(today.date.value).isEqualTo(LocalDate(2026, 3, 30))
    }
}
