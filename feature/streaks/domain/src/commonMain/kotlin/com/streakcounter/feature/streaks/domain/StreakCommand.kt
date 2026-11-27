package com.streakcounter.feature.streaks.domain

import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/** Every change to the stored streaks. Each one runs as a single atomic transaction. */
sealed interface StreakCommand {
    data class Create(val new: NewStreak) : StreakCommand
    data class Update(val id: Uuid, val edit: StreakEdit) : StreakCommand
    data class Increment(val id: Uuid) : StreakCommand
    data class Decrement(val id: Uuid) : StreakCommand
    data class Reset(val id: Uuid) : StreakCommand
    data class Delete(val id: Uuid) : StreakCommand

    /** Undo: puts [snapshot] back at its [Streak.position], replacing a streak with the same id. */
    data class Restore(val snapshot: Streak) : StreakCommand

    /** New order. Ids not in the list keep their relative order after the listed ones; unknown ids are ignored. */
    data class Reorder(val ids: List<Uuid>) : StreakCommand
    data class MarkCelebrated(val id: Uuid) : StreakCommand
    data object NormalizeAll : StreakCommand
}

/**
 * [streaks] in display order with positions `0..n-1`; [before] is the changed streak as it was
 * before the command (for undo), or null when nothing existing changed.
 */
data class CommandOutcome(val streaks: List<Streak>, val before: Streak?)

/**
 * The pure core of every repository command, shared by the real repository and the test fake.
 *
 * Order: normalize every streak for [today], apply [command], then renumber positions and
 * normalize again, so derived facts (an ended check-in, `goalReachedOn`) are recorded on the day
 * they happen. An unknown id changes nothing beyond the normalization and returns `before = null`.
 */
fun List<Streak>.applyCommand(command: StreakCommand, today: LocalDate, newId: () -> Uuid): CommandOutcome {
    val current = map { it.normalize(today) }.sortedBy { it.position }

    fun change(id: Uuid, transform: (Streak) -> Streak): CommandOutcome {
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return CommandOutcome(current, null)
        val before = current[index]
        return CommandOutcome(current.toMutableList().apply { set(index, transform(before)) }, before)
    }

    val outcome = when (command) {
        is StreakCommand.Create ->
            CommandOutcome(current + command.new.toStreak(newId(), current.size, today), null)

        is StreakCommand.Update -> change(command.id) { it.applyEdit(command.edit, today) }
        is StreakCommand.Increment -> change(command.id) { it.increment(today) }
        is StreakCommand.Decrement -> change(command.id) { it.decrement(today) }
        is StreakCommand.Reset -> change(command.id) { it.reset(today) }
        is StreakCommand.MarkCelebrated -> change(command.id) { it.markCelebrated(today) }

        is StreakCommand.Delete -> {
            val before = current.firstOrNull { it.id == command.id }
            CommandOutcome(current.filter { it.id != command.id }, before)
        }

        is StreakCommand.Restore -> {
            val snapshot = command.snapshot
            val before = current.firstOrNull { it.id == snapshot.id }
            val others = current.filter { it.id != snapshot.id }
            val index = snapshot.position.coerceIn(0, others.size)
            CommandOutcome(others.toMutableList().apply { add(index, snapshot) }, before)
        }

        is StreakCommand.Reorder -> {
            val byId = current.associateBy { it.id }
            val listed = command.ids.distinct().mapNotNull { byId[it] }
            val listedIds = listed.mapTo(HashSet()) { it.id }
            CommandOutcome(listed + current.filter { it.id !in listedIds }, null)
        }

        StreakCommand.NormalizeAll -> CommandOutcome(current, null)
    }
    return outcome.copy(streaks = outcome.streaks.mapIndexed { index, s -> s.copy(position = index).normalize(today) })
}
