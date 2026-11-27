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

class StreakEditTest {
    private val today = TODAY
    private val yesterday = YESTERDAY
    private val id = Uuid.fromLongs(7L, 8L)

    private fun new(type: StreakType, start: Int, autoReset: Boolean = false, goal: Int? = null) =
        NewStreak(name = "x", type = type, startValue = start, goal = goal, autoReset = autoReset)
            .toStreak(id, position = 3, today = today)

    private fun edit(
        count: Int,
        autoReset: Boolean,
        goal: Int? = null,
        goalChanged: Boolean = false,
        name: String? = null,
    ) = StreakEdit(name, count, goal, autoReset, goalChanged)

    @Test
    fun toStreak_checkIn_5_continues_today() {
        val s = new(StreakType.CHECK_IN, 5, autoReset = true)
        assertThat(s.lastCheckIn).isEqualTo(yesterday)
        assertThat(s.valueOn(today)).isEqualTo(5)
        assertThat(s.isDoneToday(today)).isFalse()
        assertThat(s.id).isEqualTo(id)
        assertThat(s.position).isEqualTo(3)
        assertThat(s.name).isEqualTo("x")
    }

    @Test
    fun toStreak_checkIn_0_has_no_last_checkin() {
        val s = new(StreakType.CHECK_IN, 0, autoReset = true)
        assertThat(s.lastCheckIn).isNull()
        assertThat(s.valueOn(today)).isEqualTo(0)
    }

    @Test
    fun toStreak_daysSince_30_starts_30_days_ago() {
        val s = new(StreakType.DAYS_SINCE, 30)
        assertThat(s.startDate).isEqualTo(today.minus(30, DateTimeUnit.DAY))
        assertThat(s.valueOn(today)).isEqualTo(30)
    }

    @Test
    fun toStreak_times_starts_at_value() {
        val s = new(StreakType.TIMES, 4, autoReset = true, goal = 8)
        assertThat(s.count).isEqualTo(4)
        assertThat(s.countDate).isEqualTo(today)
        assertThat(s.valueOn(today)).isEqualTo(4)
        assertThat(s.goal).isEqualTo(8)
        assertThat(s.goalReachedOn).isNull()
        assertThat(s.celebrated).isNull()
        assertThat(s.endedNote).isNull()
    }

    @Test
    fun edit_autoReset_on_is_not_retroactive() {
        val threeDaysAgo = LocalDate(2026, 11, 24)
        val s = testStreak(StreakType.CHECK_IN, autoReset = false, count = 40, lastCheckIn = threeDaysAgo)
        val e = s.applyEdit(edit(count = 40, autoReset = true), today)
        assertThat(e.autoReset).isTrue()
        assertThat(e.valueOn(today)).isEqualTo(40)
        assertThat(e.lastCheckIn).isEqualTo(yesterday)
        assertThat(e.isDoneToday(today)).isFalse()
        assertThat(e.normalize(today).valueOn(today)).isEqualTo(40)
    }

    @Test
    fun edit_autoReset_on_with_recent_checkin_changes_nothing_else() {
        val s = testStreak(StreakType.CHECK_IN, autoReset = false, count = 6, lastCheckIn = today)
        val e = s.applyEdit(edit(count = 6, autoReset = true), today)
        assertThat(e.lastCheckIn).isEqualTo(today)
        assertThat(e.count).isEqualTo(6)
    }

    @Test
    fun edit_autoReset_off_keeps_ended_streak_ended() {
        val ended = testStreak(StreakType.CHECK_IN, autoReset = true, count = 12, lastCheckIn = LocalDate(2026, 11, 24))
            .normalize(today)
        assertThat(ended.valueOn(today)).isEqualTo(0)
        val e = ended.applyEdit(edit(count = 0, autoReset = false), today)
        assertThat(e.autoReset).isFalse()
        assertThat(e.valueOn(today)).isEqualTo(0)
        assertThat(e.endedNote).isEqualTo(ended.endedNote)
        assertThat(e.lastCheckIn).isNull()
    }

    @Test
    fun edit_count_after_checkin_keeps_today_done() {
        val s = testStreak(StreakType.CHECK_IN, autoReset = true, count = 5, lastCheckIn = today)
        val e = s.applyEdit(edit(count = 9, autoReset = true), today)
        assertThat(e.valueOn(today)).isEqualTo(9)
        assertThat(e.isDoneToday(today)).isTrue()
    }

    @Test
    fun edit_checkIn_count_from_zero_starts_streak_continuing_today() {
        val ended = testStreak(StreakType.CHECK_IN, autoReset = true, count = 12, lastCheckIn = LocalDate(2026, 11, 24))
            .normalize(today)
        val e = ended.applyEdit(edit(count = 5, autoReset = true), today)
        assertThat(e.valueOn(today)).isEqualTo(5)
        assertThat(e.lastCheckIn).isEqualTo(yesterday)
        assertThat(e.endedNote).isNull()
        assertThat(e.normalize(today).valueOn(today)).isEqualTo(5)
    }

    @Test
    fun edit_checkIn_count_to_zero_when_not_done_clears_last_checkin() {
        val s = testStreak(StreakType.CHECK_IN, autoReset = true, count = 5, lastCheckIn = yesterday)
        val e = s.applyEdit(edit(count = 0, autoReset = true), today)
        assertThat(e.valueOn(today)).isEqualTo(0)
        assertThat(e.lastCheckIn).isNull()
    }

    @Test
    fun edit_times_reset_on_keeps_today_total() {
        val s = testStreak(StreakType.TIMES, autoReset = false, count = 40, countDate = yesterday)
        val e = s.applyEdit(edit(count = 40, autoReset = true), today)
        assertThat(e.isDailyGoal()).isTrue()
        assertThat(e.valueOn(today)).isEqualTo(40)
        assertThat(e.countDate).isEqualTo(today)
        assertThat(e.valueOn(LocalDate(2026, 11, 28))).isEqualTo(0)
    }

    @Test
    fun edit_times_reset_off_makes_today_the_running_total() {
        val s = testStreak(StreakType.TIMES, autoReset = true, count = 6, countDate = today)
        val e = s.applyEdit(edit(count = 6, autoReset = false), today)
        assertThat(e.isDailyGoal()).isFalse()
        assertThat(e.valueOn(today)).isEqualTo(6)
        assertThat(e.valueOn(LocalDate(2026, 11, 28))).isEqualTo(6)
    }

    @Test
    fun edit_times_mode_switch_clears_goalReachedOn() {
        val s = testStreak(StreakType.TIMES, autoReset = false, goal = 10, count = 12, countDate = today)
            .copy(goalReachedOn = yesterday)
        assertThat(s.applyEdit(edit(count = 12, autoReset = true, goal = 10), today).goalReachedOn).isNull()
    }

    @Test
    fun edit_goal_unchanged_reached_goal_saves() {
        val s = testStreak(StreakType.TIMES, goal = 30, count = 31, countDate = today).copy(goalReachedOn = yesterday)
        val e = s.applyEdit(edit(count = 31, autoReset = false, goal = 30, goalChanged = false, name = "Water"), today)
        assertThat(e.goal).isEqualTo(30)
        assertThat(e.goalReachedOn).isEqualTo(yesterday)
        assertThat(e.name).isEqualTo("Water")
    }

    @Test
    fun edit_goal_changed_clears_goalReachedOn() {
        val s = testStreak(StreakType.TIMES, goal = 30, count = 31, countDate = today).copy(goalReachedOn = yesterday)
        val e = s.applyEdit(edit(count = 31, autoReset = false, goal = 50, goalChanged = true), today)
        assertThat(e.goal).isEqualTo(50)
        assertThat(e.goalReachedOn).isNull()
    }

    @Test
    fun edit_goal_cleared_removes_goal() {
        val s = testStreak(StreakType.TIMES, goal = 30, count = 31, countDate = today).copy(goalReachedOn = yesterday)
        val e = s.applyEdit(edit(count = 31, autoReset = false, goal = null, goalChanged = true), today)
        assertThat(e.goal).isNull()
        assertThat(e.goalReachedOn).isNull()
    }

    @Test
    fun edit_daysSince_days_so_far_moves_start() {
        val s = testStreak(StreakType.DAYS_SINCE, startDate = today.minus(10, DateTimeUnit.DAY))
        val e = s.applyEdit(edit(count = 25, autoReset = false), today)
        assertThat(e.startDate).isEqualTo(today.minus(25, DateTimeUnit.DAY))
        assertThat(e.valueOn(today)).isEqualTo(25)
    }

    @Test
    fun edit_daysSince_ignores_autoReset_flag() {
        val s = testStreak(StreakType.DAYS_SINCE, autoReset = false, startDate = today)
        assertThat(s.applyEdit(edit(count = 0, autoReset = true), today).autoReset).isFalse()
    }
}
