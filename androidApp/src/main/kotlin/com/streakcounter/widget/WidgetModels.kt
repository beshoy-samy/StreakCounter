package com.streakcounter.widget

import com.streakcounter.core.domain.DataError
import com.streakcounter.core.domain.Result
import com.streakcounter.feature.streaks.domain.Streak
import com.streakcounter.feature.streaks.domain.StreakType
import com.streakcounter.feature.streaks.domain.isDoneToday
import com.streakcounter.feature.streaks.domain.progressOn
import com.streakcounter.feature.streaks.domain.valueOn
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/** One streak as a widget draws it, computed for [today]. */
internal data class WidgetStreak(
    val id: Uuid,
    val name: String?,
    val type: StreakType,
    val autoReset: Boolean,
    val value: Int,
    val goal: Int?,
    /** 0..1 goal progress for the outline, null without a goal. */
    val progress: Float?,
    val goalReached: Boolean,
    val doneToday: Boolean,
    val startDate: LocalDate?,
    val today: LocalDate,
) {
    /** Days since counts on its own: no plus or minus. */
    val hasButtons: Boolean get() = type != StreakType.DAYS_SINCE
    val minusEnabled: Boolean get() = value > 0

    /** A check-in done today can't be bumped again; plus shows a check. */
    val plusDone: Boolean get() = type == StreakType.CHECK_IN && doneToday
}

internal fun Streak.toWidgetStreak(today: LocalDate): WidgetStreak {
    val value = valueOn(today)
    return WidgetStreak(
        id = id,
        name = name,
        type = type,
        autoReset = autoReset,
        value = value,
        goal = goal,
        progress = progressOn(today),
        goalReached = goal?.let { value >= it } ?: false,
        doneToday = isDoneToday(today),
        startDate = startDate,
        today = today,
    )
}

internal sealed interface SmallWidgetUi {
    /** No read yet: draw the empty surface. */
    data object Loading : SmallWidgetUi

    /** The file can't be read: "Open StreakCounter", no buttons. */
    data object Unreadable : SmallWidgetUi

    /** Placed but no streak chosen yet (setup not finished). */
    data object NotConfigured : SmallWidgetUi

    /** The chosen streak was deleted: "Streak removed. Tap to pick another." */
    data object Removed : SmallWidgetUi

    data class Shown(val streak: WidgetStreak) : SmallWidgetUi
}

/** [read] is the latest repository value (null before the first), [streakId] the widget's stored choice. */
internal fun smallWidgetUi(
    read: Result<List<Streak>, DataError.Local>?,
    streakId: String?,
    today: LocalDate,
): SmallWidgetUi = when (read) {
    null -> SmallWidgetUi.Loading
    is Result.Error -> SmallWidgetUi.Unreadable
    is Result.Success -> {
        val id = streakId?.let { Uuid.parseOrNull(it) }
        when {
            streakId == null -> SmallWidgetUi.NotConfigured
            else -> read.data.firstOrNull { it.id == id }
                ?.let { SmallWidgetUi.Shown(it.toWidgetStreak(today)) }
                ?: SmallWidgetUi.Removed
        }
    }
}

internal sealed interface ListWidgetUi {
    data object Loading : ListWidgetUi
    data object Unreadable : ListWidgetUi
    data object Empty : ListWidgetUi
    data class Shown(val streaks: List<WidgetStreak>, val today: LocalDate) : ListWidgetUi
}

internal fun listWidgetUi(read: Result<List<Streak>, DataError.Local>?, today: LocalDate): ListWidgetUi = when (read) {
    null -> ListWidgetUi.Loading
    is Result.Error -> ListWidgetUi.Unreadable
    is Result.Success ->
        if (read.data.isEmpty()) ListWidgetUi.Empty
        else ListWidgetUi.Shown(read.data.sortedBy { it.position }.map { it.toWidgetStreak(today) }, today)
}
