package com.streakcounter.feature.streaks.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test

class DraftValidationTest {
    private fun draft(
        start: String = "0",
        goal: String = "",
        type: StreakType = StreakType.TIMES,
        autoReset: Boolean = false,
        goalEdited: Boolean = true,
    ) = StreakDraft(
        editingId = null, name = "", type = type, startValue = start, goal = goal,
        autoReset = autoReset, goalEdited = goalEdited, touched = true,
    )

    @Test
    fun validate_messages() {
        val cases = listOf(
            draft(start = "") to (FieldError.ENTER_NUMBER to null),
            draft(start = "12.5") to (FieldError.WHOLE_NUMBER to null),
            draft(start = "-3") to (FieldError.WHOLE_NUMBER to null),
            draft(start = "abc") to (FieldError.WHOLE_NUMBER to null),
            draft(start = "100000") to (FieldError.OVER_MAX to null),
            draft(start = "99999999999999") to (FieldError.OVER_MAX to null),
            draft(start = "99999") to (null to null),
            draft(start = "0") to (null to null),
            draft(start = "12", goal = "10") to (null to FieldError.GOAL_NOT_ABOVE_COUNT),
            draft(start = "12", goal = "12") to (null to FieldError.GOAL_NOT_ABOVE_COUNT),
            draft(start = "12", goal = "13") to (null to null),
            draft(start = "12", goal = "") to (null to null),
            draft(start = "0", goal = "0") to (null to FieldError.GOAL_ZERO),
            draft(start = "0", goal = "x") to (null to FieldError.GOAL_NOT_NUMBER),
            draft(start = "0", goal = "2.5") to (null to FieldError.GOAL_NOT_NUMBER),
            draft(start = "0", goal = "-1") to (null to FieldError.GOAL_NOT_NUMBER),
            draft(start = "0", goal = "100000") to (null to FieldError.OVER_MAX),
        )
        cases.forEach { (d, expected) ->
            val v = d.validate()
            assertThat(v.startError to v.goalError).isEqualTo(expected)
        }
    }

    @Test
    fun unedited_reached_goal_does_not_block_saving() {
        val v = draft(start = "31", goal = "30", goalEdited = false).validate()
        assertThat(v.goalError).isNull()
        assertThat(v.isValid).isTrue()
    }

    @Test
    fun daily_goal_only_needs_to_be_at_least_one() {
        val d = draft(start = "40", goal = "8", type = StreakType.TIMES, autoReset = true)
        assertThat(d.validate().isValid).isTrue()
        assertThat(d.copy(goal = "0").validate().goalError).isEqualTo(FieldError.GOAL_ZERO)
    }

    @Test
    fun goal_vs_count_check_skipped_when_start_invalid() {
        val v = draft(start = "", goal = "5").validate()
        assertThat(v.startError).isEqualTo(FieldError.ENTER_NUMBER)
        assertThat(v.goalError).isNull()
        assertThat(v.isValid).isFalse()
    }

    @Test
    fun normalizeName_trims_and_blank_is_null() {
        assertThat(normalizeName("   ")).isNull()
        assertThat(normalizeName("")).isNull()
        assertThat(normalizeName(" Gym ")).isEqualTo("Gym")
    }

    @Test
    fun normalizeName_keeps_long_rtl_and_emoji_intact() {
        val long = "a".repeat(300)
        assertThat(normalizeName(long)).isEqualTo(long)
        assertThat(normalizeName("قراءة ٢٠ صفحة")).isEqualTo("قراءة ٢٠ صفحة")
        assertThat(normalizeName("💧 Water")).isEqualTo("💧 Water")
    }
}
