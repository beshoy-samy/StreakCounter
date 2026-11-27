package com.streakcounter.feature.streaks.domain

import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

internal val TODAY = LocalDate(2026, 11, 27)
internal val YESTERDAY = LocalDate(2026, 11, 26)

internal fun testStreak(
    type: StreakType,
    autoReset: Boolean = false,
    goal: Int? = null,
    count: Int = 0,
    lastCheckIn: LocalDate? = null,
    startDate: LocalDate? = null,
    countDate: LocalDate? = null,
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
    goalReachedOn = null,
    celebrated = null,
    endedNote = null,
)
