package com.streakcounter.feature.streaks.presentation.list

import com.streakcounter.core.domain.DataError
import com.streakcounter.feature.streaks.domain.CelebrationSize
import com.streakcounter.feature.streaks.domain.DraftValidation
import com.streakcounter.feature.streaks.domain.EndedNote
import com.streakcounter.feature.streaks.domain.StreakDraft
import com.streakcounter.feature.streaks.domain.StreakType
import com.streakcounter.feature.streaks.presentation.session.UndoKind
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

sealed interface StreakListState {
    /** No value from the file yet. The UI shows the skeleton only if this lasts over 300 ms. */
    data object Opening : StreakListState

    /**
     * The file can't be opened (T5 "Couldn't open your streaks"). "Try again" is always offered.
     * [canStartFresh] adds "Start fresh" (T6 confirm): only for [DataError.Local.UNREADABLE], or for
     * [DataError.Local.KEY_UNAVAILABLE] that stayed after 3 consecutive "Try again" attempts (the
     * UI then uses the "key on this phone can't be used" wording). Never for UNKNOWN.
     */
    data class Unreadable(val error: DataError.Local, val canStartFresh: Boolean) : StreakListState

    data class Ready(
        val cards: List<StreakCardUi>,
        /** Check-in streaks not yet checked in today. */
        val checkInsLeft: Int,
        val hasCheckIns: Boolean,
        val undo: UndoUi?,
        /** T3 "Couldn't save that change. · Try again". */
        val saveFailed: Boolean,
        /** Head of the celebration queue (BIG before SMALL). SMALL draws the burst around the card. */
        val celebration: CelebrationUi?,
        val sheet: SheetUi?,
        val reorderMode: Boolean,
        /** Snackbar text for a SMALL [celebration] ("Water: 8 today. That's your goal."), else null. */
        val dailyGoalHit: DailyGoalUi?,
        /** T4 banner: 3 or more saves in a row failed; cleared by the next successful save. */
        val savingStuck: Boolean,
    ) : StreakListState
}

data class UndoUi(val kind: UndoKind, val name: String?, val type: StreakType)

data class CelebrationUi(
    val streakId: Uuid,
    val size: CelebrationSize,
    val value: Int,
    val name: String?,
    val type: StreakType,
)

data class DailyGoalUi(val streakId: Uuid, val name: String?, val value: Int)

data class SheetUi(
    val draft: StreakDraft,
    /** Set after a save attempt; then re-checked on every change so fixed fields clear. */
    val validation: DraftValidation?,
    val isEdit: Boolean,
    val lockedType: StreakType?,
    val goalReachedOn: LocalDate?,
    /** T6 "Discard this streak?" is open. */
    val showDiscard: Boolean,
)

data class StreakCardUi(
    val id: Uuid,
    val name: String?,
    val type: StreakType,
    val value: Int,
    val doneToday: Boolean,
    val goal: Int?,
    val progress: Float?,
    val goalReached: Boolean,
    val minusEnabled: Boolean,
    val endedNote: EndedNote?,
    val startDate: LocalDate?,
    val autoReset: Boolean,
)
