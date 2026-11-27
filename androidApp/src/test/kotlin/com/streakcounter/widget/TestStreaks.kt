package com.streakcounter.widget

import com.streakcounter.feature.streaks.domain.Streak
import com.streakcounter.feature.streaks.domain.StreakType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.uuid.Uuid

internal val TODAY = LocalDate(2026, 11, 27)

/** Check-in at [count], not done today (last check-in yesterday). */
internal fun checkIn(count: Int = 3, name: String? = "Read 20 pages", goal: Int? = 7, position: Int = 0) = Streak(
    id = Uuid.random(),
    name = name,
    type = StreakType.CHECK_IN,
    autoReset = true,
    goal = goal,
    position = position,
    count = count,
    lastCheckIn = TODAY.minus(1, DateTimeUnit.DAY),
    startDate = null,
    countDate = null,
    goalReachedOn = null,
    celebrated = null,
    endedNote = null,
)

/** Days since, started [days] days ago. */
internal fun daysSince(days: Int = 18, name: String? = "No smoking", goal: Int? = 30, position: Int = 0) = Streak(
    id = Uuid.random(),
    name = name,
    type = StreakType.DAYS_SINCE,
    autoReset = false,
    goal = goal,
    position = position,
    count = 0,
    lastCheckIn = null,
    startDate = TODAY.minus(days, DateTimeUnit.DAY),
    countDate = null,
    goalReachedOn = null,
    celebrated = null,
    endedNote = null,
)
