package com.streakcounter.feature.streaks.data

import com.streakcounter.feature.streaks.domain.CelebrationMark
import com.streakcounter.feature.streaks.domain.EndedNote
import com.streakcounter.feature.streaks.domain.Streak
import com.streakcounter.feature.streaks.domain.StreakType
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/** Throws IllegalArgumentException for a malformed id or date; the file serializer turns that into corruption. */
fun StreakDto.toStreak(): Streak = Streak(
    id = Uuid.parse(id),
    name = name,
    type = type.toStreakType(),
    autoReset = autoReset,
    goal = goal,
    position = position,
    count = count,
    lastCheckIn = lastCheckIn?.let(LocalDate::parse),
    startDate = startDate?.let(LocalDate::parse),
    countDate = countDate?.let(LocalDate::parse),
    goalReachedOn = goalReachedOn?.let(LocalDate::parse),
    celebrated = celebrated?.let { CelebrationMark(it.goal, it.day?.let(LocalDate::parse)) },
    endedNote = endedNote?.let { EndedNote(LocalDate.parse(it.endedOn), it.previousCount) },
)

fun Streak.toStreakDto(): StreakDto = StreakDto(
    id = id.toString(),
    name = name,
    type = type.toStreakTypeDto(),
    autoReset = autoReset,
    goal = goal,
    position = position,
    count = count,
    lastCheckIn = lastCheckIn?.toString(),
    startDate = startDate?.toString(),
    countDate = countDate?.toString(),
    goalReachedOn = goalReachedOn?.toString(),
    celebrated = celebrated?.let { CelebrationMarkDto(it.goal, it.day?.toString()) },
    endedNote = endedNote?.let { EndedNoteDto(it.endedOn.toString(), it.previousCount) },
)

private fun StreakTypeDto.toStreakType(): StreakType = when (this) {
    StreakTypeDto.CHECK_IN -> StreakType.CHECK_IN
    StreakTypeDto.DAYS_SINCE -> StreakType.DAYS_SINCE
    StreakTypeDto.TIMES -> StreakType.TIMES
}

private fun StreakType.toStreakTypeDto(): StreakTypeDto = when (this) {
    StreakType.CHECK_IN -> StreakTypeDto.CHECK_IN
    StreakType.DAYS_SINCE -> StreakTypeDto.DAYS_SINCE
    StreakType.TIMES -> StreakTypeDto.TIMES
}
