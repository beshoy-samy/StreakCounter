package com.streakcounter.feature.streaks.presentation.session

import androidx.appstate.statestore.StateStore
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.streakcounter.feature.streaks.domain.Streak
import com.streakcounter.feature.streaks.domain.StreakCommand
import com.streakcounter.feature.streaks.domain.StreakDraft
import com.streakcounter.feature.streaks.domain.StreakType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

class StreakSessionTest {

    private val id = Uuid.fromLongs(0L, 1L)
    private val snapshot = Streak(
        id = id, name = "Gym", type = StreakType.TIMES, autoReset = false, goal = null, position = 0,
        count = 4, lastCheckIn = null, startDate = null, countDate = LocalDate(2026, 11, 27),
        goalReachedOn = null, celebrated = null, endedNote = null,
    )

    @Test
    fun undo_and_draft_round_trip() {
        val session = StreakSession(StateStore())
        assertThat(session.undo.value).isNull()
        assertThat(session.draft.value).isNull()

        val entry = UndoEntry(snapshot, UndoKind.RESET, Instant.fromEpochSeconds(100))
        session.offerUndo(entry)
        assertThat(session.undo.value).isEqualTo(entry)
        session.clearUndo()
        assertThat(session.undo.value).isNull()

        val draft = StreakDraft(id, "Gym", StreakType.TIMES, "4", "", false, goalEdited = false, touched = true)
        session.setDraft(draft)
        assertThat(session.draft.value).isEqualTo(draft)
        session.setDraft(null)
        assertThat(session.draft.value).isNull()
    }

    @Test
    fun failed_command_shown_celebrations_and_reorder_round_trip() {
        val session = StreakSession(StateStore())

        session.setFailed(StreakCommand.Increment(id))
        assertThat(session.failedCommand.value).isEqualTo(StreakCommand.Increment(id))
        session.setFailed(null)
        assertThat(session.failedCommand.value).isNull()

        session.markShown("a")
        session.markShown("b")
        session.markShown("a")
        assertThat(session.shownCelebrations.value).containsExactlyInAnyOrder("a", "b")

        assertThat(session.reorderMode.value).isFalse()
        session.setReorderMode(true)
        assertThat(session.reorderMode.value).isTrue()
    }

    @Test
    fun two_sessions_on_one_store_share_state() {
        val store = StateStore()
        StreakSession(store).setReorderMode(true)
        assertThat(StreakSession(store).reorderMode.value).isTrue()
    }
}
