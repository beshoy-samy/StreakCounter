package com.streakcounter.feature.streaks.presentation.list

import com.streakcounter.feature.streaks.domain.StreakDraft
import kotlin.uuid.Uuid

sealed interface StreakListAction {
    data class OnIncrement(val id: Uuid) : StreakListAction
    data class OnDecrement(val id: Uuid) : StreakListAction
    data class OnReset(val id: Uuid) : StreakListAction

    /** Swipe on the card, or "Delete streak" in the edit sheet (which then closes). */
    data class OnDelete(val id: Uuid) : StreakListAction
    data object OnUndo : StreakListAction

    /** "Try again" on the save-failed snackbar: replays the failed command. */
    data object OnRetry : StreakListAction
    data class OnCardClick(val id: Uuid) : StreakListAction
    data object OnNewClick : StreakListAction
    data class OnDraftChange(val draft: StreakDraft) : StreakListAction
    data object OnSave : StreakListAction
    data object OnSheetDismiss : StreakListAction
    data object OnKeepEditing : StreakListAction
    data object OnDiscard : StreakListAction
    data object OnCelebrationDismiss : StreakListAction
    data object OnSetNewGoal : StreakListAction
    data object OnReorderStart : StreakListAction
    data class OnMove(val from: Int, val to: Int) : StreakListAction
    data object OnReorderDone : StreakListAction

    /** Confirmed in the T6 dialog. */
    data object OnStartFreshConfirm : StreakListAction

    /** "Try again" on the can't-open screen: reads the file again. */
    data object OnRetryOpen : StreakListAction
}
