package com.streakcounter.feature.streaks.domain

import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

enum class StreakType { CHECK_IN, DAYS_SINCE, TIMES }

/** Last goal already celebrated; [day] is set only for daily goals. */
data class CelebrationMark(val goal: Int, val day: LocalDate?)

/** CHECK_IN: "ended on [endedOn] after [previousCount] days", shown once. */
data class EndedNote(val endedOn: LocalDate, val previousCount: Int)

data class Streak(
    val id: Uuid,
    val name: String?,
    val type: StreakType,
    val autoReset: Boolean,
    val goal: Int?,
    val position: Int,
    val count: Int,
    val lastCheckIn: LocalDate?,
    val startDate: LocalDate?,
    val countDate: LocalDate?,
    val goalReachedOn: LocalDate?,
    val celebrated: CelebrationMark?,
    val endedNote: EndedNote?,
)
