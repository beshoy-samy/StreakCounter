package com.streakcounter.feature.streaks.presentation.list.components

import androidx.compose.runtime.Composable
import com.streakcounter.feature.streaks.domain.EndedNote
import com.streakcounter.feature.streaks.domain.StreakType
import com.streakcounter.feature.streaks.presentation.format.DateSkeleton
import com.streakcounter.feature.streaks.presentation.format.formatDate
import com.streakcounter.feature.streaks.presentation.format.formatNumber
import com.streakcounter.feature.streaks.presentation.list.StreakCardUi
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import streakcounter.feature.streaks.presentation.generated.resources.Res
import streakcounter.feature.streaks.presentation.generated.resources.detail_done_today
import streakcounter.feature.streaks.presentation.generated.resources.detail_goal_reached
import streakcounter.feature.streaks.presentation.generated.resources.detail_not_yet_today
import streakcounter.feature.streaks.presentation.generated.resources.detail_progress
import streakcounter.feature.streaks.presentation.generated.resources.detail_progress_today
import streakcounter.feature.streaks.presentation.generated.resources.detail_resets_at_midnight
import streakcounter.feature.streaks.presentation.generated.resources.detail_running_total
import streakcounter.feature.streaks.presentation.generated.resources.detail_since
import streakcounter.feature.streaks.presentation.generated.resources.ended_note
import streakcounter.feature.streaks.presentation.generated.resources.subtitle
import streakcounter.feature.streaks.presentation.generated.resources.type_check_in
import streakcounter.feature.streaks.presentation.generated.resources.type_days_since
import streakcounter.feature.streaks.presentation.generated.resources.type_times
import streakcounter.feature.streaks.presentation.generated.resources.unit_days
import streakcounter.feature.streaks.presentation.generated.resources.unit_times
import streakcounter.feature.streaks.presentation.generated.resources.unit_today
import streakcounter.feature.streaks.presentation.generated.resources.unnamed_check_in
import streakcounter.feature.streaks.presentation.generated.resources.unnamed_days_since
import streakcounter.feature.streaks.presentation.generated.resources.unnamed_times

/** What the card calls the streak: its name, or the type name ("Check-in streak") when unnamed. */
@Composable
internal fun streakDisplayName(name: String?, type: StreakType): String =
    name ?: stringResource(unnamedName(type))

internal fun unnamedName(type: StreakType): StringResource = when (type) {
    StreakType.CHECK_IN -> Res.string.unnamed_check_in
    StreakType.DAYS_SINCE -> Res.string.unnamed_days_since
    StreakType.TIMES -> Res.string.unnamed_times
}

/** "Check-in · 12 of 30, 18 to go" (spec 4.2), with type-specific text when there is no goal. */
@Composable
internal fun StreakCardUi.subtitle(today: LocalDate?): String {
    val typeName = stringResource(
        when (type) {
            StreakType.CHECK_IN -> Res.string.type_check_in
            StreakType.DAYS_SINCE -> Res.string.type_days_since
            StreakType.TIMES -> Res.string.type_times
        },
    )
    val dailyGoal = type == StreakType.TIMES && autoReset
    val detail = when {
        goal != null && goalReached -> stringResource(Res.string.detail_goal_reached, formatNumber(goal))
        goal != null -> stringResource(
            if (dailyGoal) Res.string.detail_progress_today else Res.string.detail_progress,
            formatNumber(value),
            formatNumber(goal),
            formatNumber(goal - value),
        )
        type == StreakType.CHECK_IN ->
            stringResource(if (doneToday) Res.string.detail_done_today else Res.string.detail_not_yet_today)
        type == StreakType.DAYS_SINCE && startDate != null ->
            stringResource(Res.string.detail_since, shortDate(startDate, today))
        type == StreakType.TIMES && autoReset -> stringResource(Res.string.detail_resets_at_midnight)
        type == StreakType.TIMES -> stringResource(Res.string.detail_running_total)
        else -> null
    }
    return if (detail == null) typeName else stringResource(Res.string.subtitle, typeName, detail)
}

/** "days" / "today" (Times resetting at midnight) / "times". */
@Composable
internal fun StreakCardUi.unit(): String = when {
    type == StreakType.TIMES && autoReset -> stringResource(Res.string.unit_today)
    type == StreakType.TIMES -> pluralStringResource(Res.plurals.unit_times, value)
    else -> pluralStringResource(Res.plurals.unit_days, value)
}

/** "Ended 25 Nov after 12 days. A new streak starts with your next check-in." */
@Composable
internal fun EndedNote.text(today: LocalDate?): String = pluralStringResource(
    Res.plurals.ended_note,
    previousCount,
    shortDate(endedOn, today),
    formatNumber(previousCount),
)

/** "11 Oct" in the current year, "3 Jul 2023" otherwise (or when today is unknown, without year). */
internal fun shortDate(date: LocalDate, today: LocalDate?): String = formatDate(
    date,
    if (today == null || date.year == today.year) DateSkeleton.DAY_MONTH else DateSkeleton.DAY_MONTH_YEAR,
)
