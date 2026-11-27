package com.streakcounter.feature.streaks.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.datetime.LocalDate
import kotlin.test.Test

class CelebrationTest {
    private val today = TODAY
    private val tomorrow = LocalDate(2026, 11, 28)

    @Test
    fun no_goal_or_below_goal_has_no_celebration() {
        assertThat(testStreak(StreakType.TIMES, count = 50, countDate = today).pendingCelebration(today)).isNull()
        assertThat(testStreak(StreakType.TIMES, goal = 30, count = 29, countDate = today).pendingCelebration(today)).isNull()
    }

    @Test
    fun celebration_big_once_per_goal() {
        val s = testStreak(StreakType.TIMES, goal = 30, count = 31, countDate = today)
        val pending = s.pendingCelebration(today)
        assertThat(pending).isEqualTo(Celebration(s.id, CelebrationSize.BIG, goal = 30, value = 31))
        val done = s.markCelebrated(today)
        assertThat(done.celebrated).isEqualTo(CelebrationMark(30, null))
        assertThat(done.pendingCelebration(today)).isNull()
        assertThat(done.pendingCelebration(tomorrow)).isNull()
    }

    @Test
    fun celebration_small_once_per_day() {
        val s = testStreak(StreakType.TIMES, autoReset = true, goal = 8, count = 8, countDate = today)
        assertThat(s.pendingCelebration(today)?.size).isEqualTo(CelebrationSize.SMALL)
        val done = s.markCelebrated(today)
        assertThat(done.celebrated).isEqualTo(CelebrationMark(8, today))
        assertThat(done.pendingCelebration(today)).isNull()
        val next = done.copy(count = 8, countDate = tomorrow)
        assertThat(next.pendingCelebration(tomorrow)?.size).isEqualTo(CelebrationSize.SMALL)
    }

    @Test
    fun new_goal_after_reached_can_celebrate_again() {
        val s = testStreak(StreakType.TIMES, goal = 30, count = 31, countDate = today).markCelebrated(today)
        val bumped = s.applyEdit(StreakEdit(null, 31, 50, false, goalChanged = true), today)
        assertThat(bumped.pendingCelebration(today)).isNull()
        val reached = bumped.copy(count = 50)
        assertThat(reached.pendingCelebration(today)?.size).isEqualTo(CelebrationSize.BIG)
        assertThat(reached.pendingCelebration(today)?.goal).isEqualTo(50)
    }

    @Test
    fun markCelebrated_without_goal_is_noop() {
        val s = testStreak(StreakType.TIMES, count = 3, countDate = today)
        assertThat(s.markCelebrated(today)).isEqualTo(s)
    }

    @Test
    fun reset_then_reach_same_goal_celebrates_again() {
        var s = testStreak(StreakType.TIMES, goal = 3, count = 2, countDate = today)
        s = s.increment(today).normalize(today)
        assertThat(s.pendingCelebration(today)?.size).isEqualTo(CelebrationSize.BIG)
        s = s.markCelebrated(today)
        assertThat(s.pendingCelebration(today)).isNull()
        s = s.reset(today)
        assertThat(s.celebrated).isNull()
        assertThat(s.goalReachedOn).isNull()
        repeat(3) { s = s.increment(today) }
        assertThat(s.normalize(today).pendingCelebration(today)?.size).isEqualTo(CelebrationSize.BIG)
    }

    @Test
    fun ended_checkIn_then_fresh_run_to_same_goal_celebrates_again() {
        var s = testStreak(StreakType.CHECK_IN, autoReset = true, goal = 5, count = 5, lastCheckIn = LocalDate(2026, 11, 26))
        s = s.normalize(today).markCelebrated(today)
        assertThat(s.goalReachedOn).isEqualTo(today)
        val later = LocalDate(2026, 12, 3)
        s = s.normalize(later)
        assertThat(s.valueOn(later)).isEqualTo(0)
        assertThat(s.celebrated).isNull()
        assertThat(s.goalReachedOn).isNull()
    }

    @Test
    fun reset_keeps_daily_goal_mark() {
        val s = testStreak(StreakType.TIMES, autoReset = true, goal = 8, count = 8, countDate = today).markCelebrated(today)
        assertThat(s.reset(today).celebrated).isEqualTo(CelebrationMark(8, today))
    }

    @Test
    fun daysSince_reset_clears_celebration() {
        val s = testStreak(StreakType.DAYS_SINCE, goal = 3, startDate = LocalDate(2026, 11, 24)).normalize(today).markCelebrated(today)
        assertThat(s.reset(today).celebrated).isNull()
        assertThat(s.reset(today).goalReachedOn).isNull()
    }
}
