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
    /** The count field was changed since the sheet opened; otherwise a save keeps the stored count. */
    val countEdited: Boolean = false,
)

enum class FieldError : Error {
    ENTER_NUMBER, WHOLE_NUMBER, OVER_MAX, GOAL_NOT_ABOVE_COUNT, GOAL_ZERO, GOAL_NOT_NUMBER,
}

data class DraftValidation(val startError: FieldError?, val goalError: FieldError?) {
    val isValid: Boolean get() = startError == null && goalError == null
}

fun normalizeName(raw: String): String? = raw.trim().ifEmpty { null }

/**
 * [this] with Arabic-Indic (U+0660..0669) and Extended Arabic-Indic (U+06F0..06F9) digits turned
 * into ASCII, or null when it is empty or has anything but digits. Digits of different systems
 * may be mixed ("1٢" is 12), because a keyboard can switch between them mid-number.
 */
fun String.asciiDigitsOrNull(): String? {
    if (isEmpty()) return null
    return buildString(length) {
        for (c in this@asciiDigitsOrNull) {
            append(
                when (c) {
                    in '0'..'9' -> c
                    in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                    in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                    else -> return null
                },
            )
        }
    }
}

/** The whole number typed in any digit system (surrounding spaces ignored), or null. */
fun String.toCountOrNull(): Int? = trim().asciiDigitsOrNull()?.toIntOrNull()

/** Digits only, so "99999999999999" reads as over the limit rather than not a number. */
private fun String.overMax() = trimStart('0').let { it.length > 5 || (it.toIntOrNull() ?: 0) > MAX_VALUE }

fun StreakDraft.validate(): DraftValidation {
    val raw = startValue.trim()
    val start = raw.asciiDigitsOrNull()
    val startError = when {
        raw.isEmpty() -> FieldError.ENTER_NUMBER
        start == null -> FieldError.WHOLE_NUMBER
        start.overMax() -> FieldError.OVER_MAX
        else -> null
    }
    val startNumber = if (startError == null) start?.toInt() else null

    val rawGoal = goal.trim()
    val g = rawGoal.asciiDigitsOrNull()
    val goalError = when {
        rawGoal.isEmpty() -> null
        g == null -> FieldError.GOAL_NOT_NUMBER
        g.overMax() -> FieldError.OVER_MAX
        g.toInt() == 0 -> FieldError.GOAL_ZERO
        goalEdited && !(type == StreakType.TIMES && autoReset) &&
            startNumber != null && g.toInt() <= startNumber -> FieldError.GOAL_NOT_ABOVE_COUNT
        else -> null
    }
    return DraftValidation(startError, goalError)
}
