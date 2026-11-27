package com.streakcounter.feature.streaks.domain

import com.streakcounter.core.domain.Error
import kotlin.uuid.Uuid

data class StreakDraft(
    val editingId: Uuid?,
    val name: String,
    val type: StreakType,
    val startValue: String,
    val goal: String,
    val autoReset: Boolean,
    val goalEdited: Boolean,
    val touched: Boolean,
)

enum class FieldError : Error {
    ENTER_NUMBER, WHOLE_NUMBER, OVER_MAX, GOAL_NOT_ABOVE_COUNT, GOAL_ZERO, GOAL_NOT_NUMBER,
}

data class DraftValidation(val startError: FieldError?, val goalError: FieldError?) {
    val isValid: Boolean get() = startError == null && goalError == null
}

fun normalizeName(raw: String): String? = raw.trim().ifEmpty { null }

private fun String.isDigits() = isNotEmpty() && all { it in '0'..'9' }

/** Digits only, so "99999999999999" reads as over the limit rather than not a number. */
private fun String.overMax() = trimStart('0').let { it.length > 5 || (it.toIntOrNull() ?: 0) > MAX_VALUE }

fun StreakDraft.validate(): DraftValidation {
    val start = startValue.trim()
    val startError = when {
        start.isEmpty() -> FieldError.ENTER_NUMBER
        !start.isDigits() -> FieldError.WHOLE_NUMBER
        start.overMax() -> FieldError.OVER_MAX
        else -> null
    }
    val startNumber = if (startError == null) start.toInt() else null

    val g = goal.trim()
    val goalError = when {
        g.isEmpty() -> null
        !g.isDigits() -> FieldError.GOAL_NOT_NUMBER
        g.overMax() -> FieldError.OVER_MAX
        g.toInt() == 0 -> FieldError.GOAL_ZERO
        goalEdited && !(type == StreakType.TIMES && autoReset) &&
            startNumber != null && g.toInt() <= startNumber -> FieldError.GOAL_NOT_ABOVE_COUNT
        else -> null
    }
    return DraftValidation(startError, goalError)
}
