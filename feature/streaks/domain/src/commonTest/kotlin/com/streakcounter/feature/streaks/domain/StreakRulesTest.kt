package com.streakcounter.feature.streaks.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.uuid.Uuid

class StreakRulesTest {
    private val today = LocalDate(2026, 11, 27)
    private val yesterday = LocalDate(2026, 11, 26)

    private fun streak(
        type: StreakType,
        autoReset: Boolean = false,
        goal: Int? = null,
        count: Int = 0,
        lastCheckIn: LocalDate? = null,
        startDate: LocalDate? = null,
        countDate: LocalDate? = null,
        goalReachedOn: LocalDate? = null,
        endedNote: EndedNote? = null,
    ) = Streak(
        id = Uuid.fromLongs(1L, 2L),
        name = null,
        type = type,
        autoReset = autoReset,
        goal = goal,
        position = 0,
        count = count,
        lastCheckIn = lastCheckIn,
        startDate = startDate,
        countDate = countDate,
        goalReachedOn = goalReachedOn,
        celebrated = null,
        endedNote = endedNote,
    )

    private fun checkIn(
        count: Int,
        lastCheckIn: LocalDate?,
        autoReset: Boolean = true,
        goal: Int? = null,
    ) = streak(StreakType.CHECK_IN, autoReset, goal, count = count, lastCheckIn = lastCheckIn)

    private fun times(
        count: Int,
        countDate: LocalDate?,
        autoReset: Boolean,
        goal: Int? = null,
    ) = streak(StreakType.TIMES, autoReset, goal, count = count, countDate = countDate)

    @Test
    fun checkIn_value_keeps_count_when_last_checkin_yesterday() {
        assertThat(checkIn(12, yesterday).valueOn(today)).isEqualTo(12)
    }

    @Test
    fun checkIn_autoReset_missed_day_reads_zero_and_normalize_writes_note() {
        val s = checkIn(12, LocalDate(2026, 11, 24))
        assertThat(s.valueOn(today)).isEqualTo(0)
        val n = s.normalize(today)
        assertThat(n.endedNote).isEqualTo(EndedNote(LocalDate(2026, 11, 25), 12))
        assertThat(n.count).isEqualTo(0)
        assertThat(n.lastCheckIn).isNull()
        assertThat(n.valueOn(today)).isEqualTo(0)
    }

    @Test
    fun checkIn_without_autoReset_never_ends() {
        val s = checkIn(12, LocalDate(2026, 10, 1), autoReset = false)
        assertThat(s.valueOn(today)).isEqualTo(12)
        assertThat(s.normalize(today)).isEqualTo(s)
    }

    @Test
    fun checkIn_increment_twice_same_day_adds_one() {
        val once = checkIn(12, yesterday).increment(today)
        assertThat(once.valueOn(today)).isEqualTo(13)
        assertThat(once.lastCheckIn).isEqualTo(today)
        assertThat(once.increment(today)).isEqualTo(once)
    }

    @Test
    fun checkIn_increment_after_ended_starts_new_streak_and_clears_note() {
        val ended = checkIn(12, LocalDate(2026, 11, 24)).normalize(today)
        val n = ended.increment(today)
        assertThat(n.valueOn(today)).isEqualTo(1)
        assertThat(n.endedNote).isNull()
    }

    @Test
    fun checkIn_decrement_after_checkin_undoes_today() {
        val s = checkIn(12, yesterday).increment(today).decrement(today)
        assertThat(s.isDoneToday(today)).isFalse()
        assertThat(s.valueOn(today)).isEqualTo(12)
        assertThat(s.lastCheckIn).isEqualTo(yesterday)
    }

    @Test
    fun checkIn_decrement_undo_of_first_checkin_clears_last_checkin() {
        val s = checkIn(0, null).increment(today).decrement(today)
        assertThat(s.count).isEqualTo(0)
        assertThat(s.lastCheckIn).isNull()
    }

    @Test
    fun checkIn_decrement_when_not_done_corrects_down_floor_zero() {
        val one = checkIn(1, yesterday)
        val zero = one.decrement(today)
        assertThat(zero.valueOn(today)).isEqualTo(0)
        assertThat(zero.lastCheckIn).isNull()
        assertThat(zero.decrement(today).valueOn(today)).isEqualTo(0)
    }

    @Test
    fun checkIn_decrement_clears_ended_note() {
        val s = checkIn(3, yesterday).copy(endedNote = EndedNote(LocalDate(2026, 11, 1), 5))
        assertThat(s.decrement(today).endedNote).isNull()
    }

    @Test
    fun checkIn_reset_zeroes_and_clears_note() {
        val s = checkIn(7, yesterday).copy(endedNote = EndedNote(LocalDate(2026, 11, 1), 5)).reset(today)
        assertThat(s.count).isEqualTo(0)
        assertThat(s.lastCheckIn).isNull()
        assertThat(s.endedNote).isNull()
    }

    @Test
    fun checkIn_increment_caps_at_max() {
        assertThat(checkIn(MAX_VALUE, yesterday).increment(today).valueOn(today)).isEqualTo(MAX_VALUE)
    }

    @Test
    fun daysSince_counts_whole_days() {
        val s = streak(StreakType.DAYS_SINCE, startDate = LocalDate(2026, 10, 11))
        assertThat(s.valueOn(today)).isEqualTo(47)
        assertThat(s.increment(today).startDate).isEqualTo(LocalDate(2026, 10, 10))
        assertThat(s.increment(today).valueOn(today)).isEqualTo(48)
        assertThat(s.decrement(today).valueOn(today)).isEqualTo(46)
        assertThat(s.reset(today).startDate).isEqualTo(today)
    }

    @Test
    fun daysSince_decrement_at_start_today_stays_today() {
        val s = streak(StreakType.DAYS_SINCE, startDate = today)
        assertThat(s.decrement(today).startDate).isEqualTo(today)
    }

    @Test
    fun daysSince_increment_caps_at_max() {
        val atMax = streak(StreakType.DAYS_SINCE, startDate = today.minus(MAX_VALUE, DateTimeUnit.DAY))
        assertThat(atMax.increment(today).valueOn(today)).isEqualTo(MAX_VALUE)
        assertThat(atMax.increment(today).startDate).isEqualTo(atMax.startDate)
    }

    @Test
    fun daysSince_reset_clears_ended_note() {
        val s = streak(StreakType.DAYS_SINCE, startDate = yesterday, endedNote = EndedNote(yesterday, 1))
        assertThat(s.reset(today).endedNote).isNull()
    }

    @Test
    fun times_autoReset_reads_zero_next_day() {
        val s = times(5, yesterday, autoReset = true)
        assertThat(s.valueOn(today)).isEqualTo(0)
        assertThat(times(5, today, autoReset = true).valueOn(today)).isEqualTo(5)
        val n = s.normalize(today)
        assertThat(n.count).isEqualTo(0)
        assertThat(n.countDate).isEqualTo(today)
    }

    @Test
    fun times_without_reset_keeps_running_total() {
        val s = times(5, LocalDate(2026, 10, 1), autoReset = false)
        assertThat(s.valueOn(today)).isEqualTo(5)
        assertThat(s.increment(today).valueOn(today)).isEqualTo(6)
    }

    @Test
    fun times_increment_on_stale_day_starts_from_zero() {
        val s = times(5, yesterday, autoReset = true).increment(today)
        assertThat(s.count).isEqualTo(1)
        assertThat(s.countDate).isEqualTo(today)
    }

    @Test
    fun times_decrement_floor_zero() {
        val s = times(1, today, autoReset = true)
        assertThat(s.decrement(today).valueOn(today)).isEqualTo(0)
        assertThat(s.decrement(today).decrement(today).valueOn(today)).isEqualTo(0)
        assertThat(times(2, null, autoReset = false).decrement(today).valueOn(today)).isEqualTo(1)
    }

    @Test
    fun times_increment_caps_at_99999() {
        assertThat(times(MAX_VALUE, today, autoReset = true).increment(today).valueOn(today)).isEqualTo(99_999)
    }

    @Test
    fun times_reset_zeroes() {
        assertThat(times(9, today, autoReset = false).reset(today).valueOn(today)).isEqualTo(0)
        assertThat(times(9, today, autoReset = true).reset(today).valueOn(today)).isEqualTo(0)
    }

    @Test
    fun isDailyGoal_only_for_times_with_autoReset() {
        val cases = listOf(
            Triple(StreakType.TIMES, true, true),
            Triple(StreakType.TIMES, false, false),
            Triple(StreakType.CHECK_IN, true, false),
            Triple(StreakType.DAYS_SINCE, true, false),
        )
        for ((type, auto, expected) in cases) {
            assertThat(streak(type, autoReset = auto).isDailyGoal(), "$type autoReset=$auto").isEqualTo(expected)
        }
    }

    @Test
    fun progress_is_null_without_goal_and_capped_at_one() {
        assertThat(times(5, today, autoReset = true).progressOn(today)).isNull()
        assertThat(times(18, today, autoReset = true, goal = 30).progressOn(today)).isEqualTo(0.6f)
        assertThat(times(31, today, autoReset = true, goal = 30).progressOn(today)).isEqualTo(1f)
    }

    @Test
    fun normalize_sets_goalReachedOn_once() {
        val s = times(30, today, autoReset = false, goal = 30)
        val n = s.normalize(today)
        assertThat(n.goalReachedOn).isEqualTo(today)
        val later = LocalDate(2026, 11, 28)
        assertThat(n.normalize(later).goalReachedOn).isEqualTo(today)
    }

    @Test
    fun normalize_does_not_set_goalReachedOn_below_goal_or_for_daily_goals() {
        assertThat(times(29, today, autoReset = false, goal = 30).normalize(today).goalReachedOn).isNull()
        assertThat(times(30, today, autoReset = true, goal = 30).normalize(today).goalReachedOn).isNull()
    }

    @Test
    fun future_dates_never_go_negative() {
        val ci = checkIn(4, LocalDate(2026, 11, 30))
        assertThat(ci.isDoneToday(today)).isTrue()
        assertThat(ci.valueOn(today)).isEqualTo(4)

        val ds = streak(StreakType.DAYS_SINCE, startDate = LocalDate(2026, 12, 1))
        assertThat(ds.valueOn(today)).isEqualTo(0)
        assertThat(ds.decrement(today).startDate).isEqualTo(today)

        val t = times(7, LocalDate(2026, 11, 28), autoReset = true)
        assertThat(t.valueOn(today)).isEqualTo(0)
        assertThat(t.normalize(today).valueOn(today)).isEqualTo(0)
    }
}
