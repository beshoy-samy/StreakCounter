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

/**
 * [count] is the number the sheet shows: days so far (days since), current count, or running total.
 * [countChanged] is false when the user never touched that field; the stored count then wins, so a
 * widget tap, check-in or midnight that happened while the sheet was open is not undone.
 */
data class StreakEdit(
    val name: String?,
    val count: Int,
    val goal: Int?,
    val autoReset: Boolean,
    val goalChanged: Boolean,
    val countChanged: Boolean,
)

/**
 * Spec 4.1a. Edits never lose a count silently. When the count field was not edited, the stored
 * anchors (count, last check-in, start date, count date) stay as they are; only the auto-reset
 * rules below still apply.
 */
fun Streak.applyEdit(edit: StreakEdit, today: LocalDate): Streak {
    val yesterday = today.minus(1, DateTimeUnit.DAY)
    val shown = valueOn(today)
    val n = if (edit.countChanged) edit.count.coerceIn(0, MAX_VALUE) else shown
    val autoReset = if (type == StreakType.DAYS_SINCE) autoReset else edit.autoReset
    val modeChanged = type == StreakType.TIMES && autoReset != this.autoReset
    var s = copy(
        name = edit.name,
        goal = edit.goal,
        autoReset = autoReset,
        goalReachedOn = if (edit.goalChanged || modeChanged) null else goalReachedOn,
        // A daily mark must not stop the big celebration of a total goal (and the reverse).
        celebrated = if (modeChanged) null else celebrated,
    )
    s = when (type) {
        StreakType.CHECK_IN -> {
            // Auto-reset on is not retroactive: an old last check-in moves to yesterday.
            val last = lastCheckIn?.let { if (autoReset && it < yesterday) yesterday else it }
            if (!edit.countChanged) {
                s.copy(lastCheckIn = last)
            } else {
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
        }

        StreakType.DAYS_SINCE ->
            if (!edit.countChanged) s else s.copy(startDate = today.minus(n, DateTimeUnit.DAY))

        // A mode switch makes today's value the count from today on, edited or not.
        StreakType.TIMES ->
            if (!edit.countChanged && !modeChanged) s else s.copy(count = n, countDate = today)
    }
    return s
}
