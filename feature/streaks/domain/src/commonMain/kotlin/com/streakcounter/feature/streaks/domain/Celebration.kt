package com.streakcounter.feature.streaks.domain

import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

enum class CelebrationSize { BIG, SMALL }

data class Celebration(val streakId: Uuid, val size: CelebrationSize, val goal: Int, val value: Int)

/** Daily goal: SMALL once per day. Any other goal: BIG once per goal value. */
fun Streak.pendingCelebration(today: LocalDate): Celebration? {
    val goal = goal ?: return null
    val value = valueOn(today)
    if (value < goal) return null
    val size = if (isDailyGoal()) {
        if (celebrated?.day != today) CelebrationSize.SMALL else return null
    } else {
        if (celebrated?.goal != goal) CelebrationSize.BIG else return null
    }
    return Celebration(id, size, goal, value)
}

fun Streak.markCelebrated(today: LocalDate): Streak {
    val goal = goal ?: return this
    return copy(celebrated = CelebrationMark(goal, if (isDailyGoal()) today else null))
}
