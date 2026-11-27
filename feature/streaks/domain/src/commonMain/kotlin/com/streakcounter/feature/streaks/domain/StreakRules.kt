package com.streakcounter.feature.streaks.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

const val MAX_VALUE = 99_999

private fun LocalDate.dayBefore() = minus(1, DateTimeUnit.DAY)
private fun LocalDate.dayAfter() = plus(1, DateTimeUnit.DAY)

private fun Streak.hasEnded(today: LocalDate): Boolean =
    type == StreakType.CHECK_IN && autoReset && lastCheckIn != null && lastCheckIn < today.dayBefore()

private fun Streak.isStaleTimes(today: LocalDate): Boolean =
    type == StreakType.TIMES && autoReset && countDate != today

fun Streak.valueOn(today: LocalDate): Int = when (type) {
    StreakType.CHECK_IN -> if (hasEnded(today)) 0 else count
    StreakType.DAYS_SINCE -> maxOf(0, (startDate ?: today).daysUntil(today))
    StreakType.TIMES -> if (isStaleTimes(today)) 0 else count
}

fun Streak.isDoneToday(today: LocalDate): Boolean =
    type == StreakType.CHECK_IN && lastCheckIn != null && lastCheckIn >= today

fun Streak.isDailyGoal(): Boolean = type == StreakType.TIMES && autoReset

fun Streak.progressOn(today: LocalDate): Float? {
    val goal = goal ?: return null
    if (goal <= 0) return 1f
    return minOf(1f, valueOn(today).toFloat() / goal)
}

fun Streak.normalize(today: LocalDate): Streak {
    var s = this
    if (s.hasEnded(today) && s.count > 0) {
        s = s.copy(
            count = 0,
            lastCheckIn = null,
            endedNote = EndedNote(s.lastCheckIn!!.dayAfter(), s.count),
        )
    }
    if (s.isStaleTimes(today)) {
        s = s.copy(count = 0, countDate = today)
    }
    val goal = s.goal
    if (goal != null && !s.isDailyGoal() && s.valueOn(today) >= goal && s.goalReachedOn == null) {
        s = s.copy(goalReachedOn = today)
    }
    return s
}

fun Streak.increment(today: LocalDate): Streak = when (type) {
    StreakType.CHECK_IN ->
        if (isDoneToday(today)) this
        else copy(count = minOf(MAX_VALUE, valueOn(today) + 1), lastCheckIn = today, endedNote = null)

    StreakType.DAYS_SINCE -> {
        val start = minOf(startDate ?: today, today)
        if (start.daysUntil(today) >= MAX_VALUE) copy(startDate = start)
        else copy(startDate = start.dayBefore())
    }

    StreakType.TIMES -> copy(count = minOf(MAX_VALUE, valueOn(today) + 1), countDate = today)
}

fun Streak.decrement(today: LocalDate): Streak = when (type) {
    StreakType.CHECK_IN -> {
        val newCount: Int
        val newLast: LocalDate?
        if (isDoneToday(today)) {
            newCount = maxOf(0, count - 1)
            newLast = if (newCount > 0) today.dayBefore() else null
        } else {
            newCount = maxOf(0, valueOn(today) - 1)
            newLast = if (newCount > 0) lastCheckIn else null
        }
        copy(count = newCount, lastCheckIn = newLast, endedNote = null)
    }

    StreakType.DAYS_SINCE -> copy(startDate = minOf((startDate ?: today).dayAfter(), today))

    StreakType.TIMES -> copy(
        count = maxOf(0, valueOn(today) - 1),
        countDate = if (autoReset) today else countDate,
    )
}

fun Streak.reset(today: LocalDate): Streak = when (type) {
    StreakType.CHECK_IN -> copy(count = 0, lastCheckIn = null, endedNote = null)
    StreakType.DAYS_SINCE -> copy(startDate = today, endedNote = null)
    StreakType.TIMES -> copy(count = 0, countDate = if (autoReset) today else countDate, endedNote = null)
}
