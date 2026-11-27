package com.streakcounter.feature.streaks.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.uuid.Uuid

data class NewStreak(
    val name: String?,
    val type: StreakType,
    val startValue: Int,
    val goal: Int?,
    val autoReset: Boolean,
)

fun NewStreak.toStreak(id: Uuid, position: Int, today: LocalDate): Streak {
    val n = startValue.coerceIn(0, MAX_VALUE)
    return Streak(
        id = id,
        name = name,
        type = type,
        autoReset = autoReset,
        goal = goal,
        position = position,
        count = if (type == StreakType.DAYS_SINCE) 0 else n,
        lastCheckIn = if (type == StreakType.CHECK_IN && n > 0) today.minus(1, DateTimeUnit.DAY) else null,
        startDate = if (type == StreakType.DAYS_SINCE) today.minus(n, DateTimeUnit.DAY) else null,
        countDate = if (type == StreakType.TIMES) today else null,
        goalReachedOn = null,
        celebrated = null,
        endedNote = null,
    )
}

/** [count] is the number the sheet shows: days so far (days since), current count, or running total. */
data class StreakEdit(
    val name: String?,
    val count: Int,
    val goal: Int?,
    val autoReset: Boolean,
    val goalChanged: Boolean,
)

/** Spec 4.1a. Edits never lose a count silently. */
fun Streak.applyEdit(edit: StreakEdit, today: LocalDate): Streak {
    val yesterday = today.minus(1, DateTimeUnit.DAY)
    val n = edit.count.coerceIn(0, MAX_VALUE)
    val shown = valueOn(today)
    val autoReset = if (type == StreakType.DAYS_SINCE) autoReset else edit.autoReset
    val modeChanged = type == StreakType.TIMES && autoReset != this.autoReset
    var s = copy(
        name = edit.name,
        goal = edit.goal,
        autoReset = autoReset,
        goalReachedOn = if (edit.goalChanged || modeChanged) null else goalReachedOn,
    )
    s = when (type) {
        StreakType.CHECK_IN -> {
            // Auto-reset on is not retroactive: an old last check-in moves to yesterday.
            val last = lastCheckIn?.let { if (autoReset && it < yesterday) yesterday else it }
            s.copy(
                count = n,
                lastCheckIn = when {
                    last != null && last >= today -> last
                    n > 0 -> last ?: yesterday
                    else -> null
                },
                endedNote = if (n != shown) null else endedNote,
            )
        }

        StreakType.DAYS_SINCE -> s.copy(startDate = today.minus(n, DateTimeUnit.DAY))
        StreakType.TIMES -> s.copy(count = n, countDate = today)
    }
    return s
}
