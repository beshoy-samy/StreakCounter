package com.streakcounter.widget

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.streakcounter.core.domain.DataError
import com.streakcounter.core.testing.FakeStreakRepository
import com.streakcounter.core.testing.FakeToday
import com.streakcounter.core.testing.FakeWidgetUpdater
import com.streakcounter.feature.streaks.domain.StreakCommand
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test

class MidnightRefreshTest {
    private val berlin = TimeZone.of("Europe/Berlin")

    @Test
    fun next_midnight_is_the_start_of_tomorrow_in_the_zone() {
        val now = LocalDateTime(2026, 11, 27, 23, 59).toInstant(berlin)

        assertThat(nextLocalMidnight(now, berlin)).isEqualTo(LocalDateTime(2026, 11, 28, 0, 0).toInstant(berlin))
    }

    @Test
    fun next_midnight_right_at_midnight_is_the_following_one() {
        val now = LocalDateTime(2026, 11, 28, 0, 0).toInstant(berlin)

        assertThat(nextLocalMidnight(now, berlin)).isEqualTo(LocalDateTime(2026, 11, 29, 0, 0).toInstant(berlin))
    }

    @Test
    fun midnight_refresh_normalizes_then_updates_widgets() = runTest {
        val today = FakeToday(TODAY)
        val repository = FakeStreakRepository(today, listOf(checkIn()))
        val updater = FakeWidgetUpdater()

        runMidnightRefresh(repository, today, updater)

        assertThat(today.refreshCalls).isEqualTo(1)
        assertThat(repository.executed).containsExactly(StreakCommand.NormalizeAll)
        assertThat(updater.updateAllCalls).isEqualTo(1)
    }

    @Test
    fun midnight_refresh_on_unreadable_file_does_not_write() = runTest {
        val today = FakeToday(TODAY)
        val repository = FakeStreakRepository(today, listOf(checkIn()))
        repository.emitReadError(DataError.Local.KEY_UNAVAILABLE)
        val updater = FakeWidgetUpdater()

        runMidnightRefresh(repository, today, updater)

        assertThat(repository.executed).isEmpty()
        assertThat(updater.updateAllCalls).isEqualTo(1)
    }
}
