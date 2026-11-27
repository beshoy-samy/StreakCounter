package com.streakcounter.feature.streaks.presentation.session

import androidx.appstate.statestore.StateStore
import androidx.appstate.statestore.StateStoreKey
import androidx.compose.runtime.State
import com.streakcounter.feature.streaks.domain.Streak
import com.streakcounter.feature.streaks.domain.StreakCommand
import com.streakcounter.feature.streaks.domain.StreakDraft
import kotlinx.serialization.Serializable
import kotlin.time.Instant

enum class UndoKind { RESET, DELETE }

/** The last undoable change: [snapshot] is the streak as it was before it. */
data class UndoEntry(val snapshot: Streak, val kind: UndoKind, val expiresAt: Instant)

/**
 * App-scoped session state (spec 6.4), kept in one [StateStore] so it outlives a screen's
 * ViewModel (rotation, backgrounding). The keys are private: the UI never reads the store
 * directly. Nothing here is persisted (no PersistToDataStore): session state is lost on
 * process death, which is accepted.
 */
class StreakSession(private val stateStore: StateStore) {

    // Read once: the store keeps one State per key, so these stay live, and the transform never
    // has to create a store entry while it composes.
    val undo: State<UndoEntry?> = stateStore.getState(UndoKey)
    val failedCommand: State<StreakCommand?> = stateStore.getState(FailedCommandKey)
    val draft: State<StreakDraft?> = stateStore.getState(SheetDraftKey)

    /** Celebrations already shown this session; guards a replay before `MarkCelebrated` lands. */
    val shownCelebrations: State<Set<String>> = stateStore.getState(ShownCelebrationsKey)
    val reorderMode: State<Boolean> = stateStore.getState(ReorderKey)

    fun offerUndo(entry: UndoEntry) = stateStore.setState(UndoKey, entry)
    fun clearUndo() = stateStore.setState(UndoKey, null)
    fun setFailed(command: StreakCommand?) = stateStore.setState(FailedCommandKey, command)
    fun setDraft(draft: StreakDraft?) = stateStore.setState(SheetDraftKey, draft)
    fun markShown(key: String) = stateStore.updateState(ShownCelebrationsKey) { it + key }
    fun setReorderMode(on: Boolean) = stateStore.setState(ReorderKey, on)

    @Serializable
    private data object UndoKey : StateStoreKey<UndoEntry?>(null)

    @Serializable
    private data object FailedCommandKey : StateStoreKey<StreakCommand?>(null)

    @Serializable
    private data object SheetDraftKey : StateStoreKey<StreakDraft?>(null)

    @Serializable
    private data object ShownCelebrationsKey : StateStoreKey<Set<String>>(emptySet())

    @Serializable
    private data object ReorderKey : StateStoreKey<Boolean>(false)
}
