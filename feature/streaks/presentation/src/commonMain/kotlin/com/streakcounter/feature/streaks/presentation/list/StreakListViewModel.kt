package com.streakcounter.feature.streaks.presentation.list

import androidx.appstate.transform.transform
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streakcounter.core.domain.DataError
import com.streakcounter.core.domain.Result
import com.streakcounter.core.domain.Today
import com.streakcounter.feature.streaks.domain.Celebration
import com.streakcounter.feature.streaks.domain.CelebrationSize
import com.streakcounter.feature.streaks.domain.DraftValidation
import com.streakcounter.feature.streaks.domain.NewStreak
import com.streakcounter.feature.streaks.domain.Streak
import com.streakcounter.feature.streaks.domain.StreakCommand
import com.streakcounter.feature.streaks.domain.StreakDraft
import com.streakcounter.feature.streaks.domain.StreakEdit
import com.streakcounter.feature.streaks.domain.StreakRepository
import com.streakcounter.feature.streaks.domain.StreakType
import com.streakcounter.feature.streaks.domain.WidgetUpdater
import com.streakcounter.feature.streaks.domain.isDoneToday
import com.streakcounter.feature.streaks.domain.normalizeName
import com.streakcounter.feature.streaks.domain.pendingCelebration
import com.streakcounter.feature.streaks.domain.progressOn
import com.streakcounter.feature.streaks.domain.validate
import com.streakcounter.feature.streaks.domain.valueOn
import com.streakcounter.feature.streaks.presentation.session.StreakSession
import com.streakcounter.feature.streaks.presentation.session.UndoEntry
import com.streakcounter.feature.streaks.presentation.session.UndoKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * The streak list, its create/edit sheet and its celebrations.
 *
 * Deliberate deviation from the MVI skill's `MutableStateFlow` state: the owner requires Jetpack
 * AppState, so [state] is a Compose [State] produced by AppState `transform` (spec 6.4). The
 * transform reads the repository, [Today] and the [StreakSession] and recomposes whenever any of
 * them changes; actions never write screen state directly. The disk stays the only truth: a
 * failed write leaves the list showing what is stored.
 */
class StreakListViewModel(
    private val repository: StreakRepository,
    private val today: Today,
    private val session: StreakSession,
    private val clock: Clock,
    private val widgetUpdater: WidgetUpdater,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    // Screen-local state the transform reads. Declared before [state]: the transform may compose
    // synchronously while this object is being constructed.
    /** Bumped to collect [StreakRepository.streaks] again (its collection ends after an error). */
    private var openAttempt by mutableIntStateOf(0)

    /** "Try again" taps in a row that still ended in KEY_UNAVAILABLE (R15). */
    private var keyRetries by mutableIntStateOf(0)
    private var failuresInARow by mutableIntStateOf(0)
    private var failedWrites by mutableIntStateOf(0)
    private var validation by mutableStateOf<DraftValidation?>(null)
    private var showDiscard by mutableStateOf(false)

    /** The order being dragged in reorder mode; written to disk on "Done". */
    private var pendingOrder by mutableStateOf<List<Uuid>?>(null)

    private var undoTimer: Job? = null

    // In-flight guards: a second tap while the first write runs is ignored (no duplicate Create,
    // no second reset). Checked and set on the main thread, before anything is launched.
    private var saveJob: Job? = null
    private var startFreshJob: Job? = null

    val state: State<StreakListState> = transform<StreakListState>(
        initialValue = StreakListState.Opening,
        scope = viewModelScope,
        dispatcher = dispatcher,
    ) {
        val read by remember(openAttempt) {
            repository.streaks.onEach { if (it is Result.Success) keyRetries = 0 }
        }.collectAsState(initial = null)
        val date by today.date.collectAsState()

        when (val result = read) {
            null -> StreakListState.Opening
            is Result.Error -> StreakListState.Unreadable(result.error, canStartFresh(result.error))
            is Result.Success -> ready(result.data, date)
        }
    }

    init {
        // A new ViewModel (screen re-created) takes over the expiry of an undo still on offer.
        session.undo.value?.let(::expireUndo)
    }

    fun onAction(action: StreakListAction) {
        when (action) {
            is StreakListAction.OnIncrement -> launchCommand(StreakCommand.Increment(action.id))
            is StreakListAction.OnDecrement -> launchCommand(StreakCommand.Decrement(action.id))
            is StreakListAction.OnReset -> launchCommand(StreakCommand.Reset(action.id))
            is StreakListAction.OnDelete -> {
                if (session.draft.value?.editingId == action.id) closeSheet()
                launchCommand(StreakCommand.Delete(action.id))
            }
            StreakListAction.OnUndo -> undo()
            StreakListAction.OnRetry -> retry()
            StreakListAction.OnSaveFailedDismiss -> session.setFailed(null)
            is StreakListAction.OnCardClick -> openEdit(action.id)
            StreakListAction.OnNewClick -> openSheet(NEW_DRAFT)
            is StreakListAction.OnDraftChange -> changeDraft(action.draft)
            StreakListAction.OnSave -> save()
            is StreakListAction.OnFieldBlur -> blur(action.field)
            StreakListAction.OnSheetDismiss -> {
                val draft = session.draft.value ?: return
                if (draft.touched) showDiscard = true else closeSheet()
            }
            StreakListAction.OnKeepEditing -> showDiscard = false
            StreakListAction.OnDiscard -> closeSheet()
            StreakListAction.OnCelebrationDismiss -> dismissCelebration()
            StreakListAction.OnSetNewGoal -> dismissCelebration()?.let { openEdit(it.streakId) }
            StreakListAction.OnReorderStart -> {
                pendingOrder = readyOrNull()?.cards?.map { it.id }
                session.setReorderMode(true)
            }
            is StreakListAction.OnMove -> move(action.from, action.to)
            StreakListAction.OnReorderDone -> finishReorder()
            is StreakListAction.OnMoveOne -> moveOne(action.id, action.offset)
            StreakListAction.OnStartFreshConfirm -> startFresh()
            StreakListAction.OnRetryOpen -> {
                val current = state.value
                val stillNoKey = current is StreakListState.Unreadable &&
                    current.error == DataError.Local.KEY_UNAVAILABLE
                keyRetries = if (stillNoKey) keyRetries + 1 else 0
                openAttempt++
            }
        }
    }

    // ---- State ----

    private fun canStartFresh(error: DataError.Local): Boolean = when (error) {
        DataError.Local.UNREADABLE -> true
        DataError.Local.KEY_UNAVAILABLE -> keyRetries >= KEY_RETRIES_BEFORE_START_FRESH
        else -> false
    }

    private fun ready(stored: List<Streak>, date: LocalDate): StreakListState.Ready {
        val streaks = pendingOrder?.let { stored.inOrder(it) } ?: stored
        val cards = streaks.map { it.toCardUi(date) }
        val sheet = session.draft.value?.let { draft -> sheetUi(draft, stored) }
        // The sheet wins over a celebration; it plays once the sheet closes.
        val celebration = if (sheet == null) nextCelebration(stored, date) else null
        val byId = stored.associateBy { it.id }
        return StreakListState.Ready(
            cards = cards,
            checkInsLeft = cards.count { it.type == StreakType.CHECK_IN && !it.doneToday },
            hasCheckIns = cards.any { it.type == StreakType.CHECK_IN },
            undo = session.undo.value?.let { UndoUi(it.kind, it.snapshot.name, it.snapshot.type) },
            saveFailed = session.failedCommand.value != null,
            celebration = celebration?.let {
                val streak = byId.getValue(it.streakId)
                CelebrationUi(it.streakId, it.size, it.value, streak.name, streak.type)
            },
            sheet = sheet,
            reorderMode = session.reorderMode.value,
            dailyGoalHit = celebration?.takeIf { it.size == CelebrationSize.SMALL }?.let {
                DailyGoalUi(it.streakId, byId.getValue(it.streakId).name, it.value)
            },
            savingStuck = failuresInARow >= FAILURES_BEFORE_STUCK,
            summary = summaryOf(cards),
            failedWrites = failedWrites,
        )
    }

    /** BIG before SMALL; within a size, list order. Skips any already shown this session. */
    private fun nextCelebration(streaks: List<Streak>, date: LocalDate): Celebration? {
        val shown = session.shownCelebrations.value
        return streaks.mapNotNull { it.pendingCelebration(date) }
            .filter { celebrationKey(it.streakId, it.size, it.goal, date) !in shown }
            .minByOrNull { if (it.size == CelebrationSize.BIG) 0 else 1 }
    }

    private fun sheetUi(draft: StreakDraft, streaks: List<Streak>): SheetUi {
        val editing = draft.editingId?.let { id -> streaks.firstOrNull { it.id == id } }
        val isEdit = draft.editingId != null
        return SheetUi(
            draft = draft,
            validation = validation,
            isEdit = isEdit,
            lockedType = if (isEdit) editing?.type ?: draft.type else null,
            goalReachedOn = editing?.goalReachedOn,
            showDiscard = showDiscard,
        )
    }

    private fun readyOrNull() = state.value as? StreakListState.Ready

    // ---- Commands ----

    private fun launchCommand(command: StreakCommand, onSuccess: () -> Unit = {}): Job =
        viewModelScope.launch { if (execute(command)) onSuccess() }

    /**
     * Runs [command] against the stored file. Success refreshes the widgets and offers undo for a
     * reset or delete; failure keeps [command] for "Try again" (which replays it, never an
     * in-memory list) and counts towards the T4 banner.
     */
    private suspend fun execute(command: StreakCommand): Boolean =
        when (val result = repository.execute(command)) {
            is Result.Success -> {
                failuresInARow = 0
                val before = result.data
                when {
                    before == null -> Unit
                    command is StreakCommand.Reset -> offerUndo(before, UndoKind.RESET, RESET_UNDO)
                    command is StreakCommand.Delete -> offerUndo(before, UndoKind.DELETE, DELETE_UNDO)
                }
                // Session and state first; the widgets follow on their own.
                refreshWidgets()
                true
            }
            is Result.Error -> {
                failuresInARow++
                failedWrites++
                session.setFailed(command)
                false
            }
        }

    private fun retry() {
        val command = session.failedCommand.value ?: return
        session.setFailed(null)
        launchCommand(command) {
            // A replayed sheet save that now lands closes the sheet it came from.
            if (command is StreakCommand.Create || command is StreakCommand.Update) closeSheet()
        }
    }

    /**
     * Only when the screen offers it. UNREADABLE deletes the file; a key that stayed unavailable
     * (R15) also deletes the key (R16). Both erase every streak, so the UI confirms first (T6).
     */
    private fun startFresh() {
        val offered = state.value as? StreakListState.Unreadable ?: return
        if (!offered.canStartFresh || startFreshJob?.isActive == true) return
        startFreshJob = viewModelScope.launch {
            val result = when (offered.error) {
                DataError.Local.KEY_UNAVAILABLE -> repository.resetUnreadableStorage()
                else -> repository.startFresh()
            }
            if (result is Result.Success) {
                keyRetries = 0
                refreshWidgets()
            }
            // Read again either way: an emptied file shows the empty list, a refusal shows why.
            openAttempt++
        }
    }

    /**
     * Redraws the widgets in its own coroutine. A slow or failing widget update never delays or
     * breaks the command that was already saved (disk stays the truth; widgets catch up on their
     * next refresh).
     */
    private fun refreshWidgets() {
        viewModelScope.launch {
            try {
                widgetUpdater.updateAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing to show the user; never log streak content.
            }
        }
    }

    // ---- Undo ----

    private fun offerUndo(snapshot: Streak, kind: UndoKind, duration: Duration) {
        val entry = UndoEntry(snapshot, kind, clock.now() + duration)
        session.offerUndo(entry)
        expireUndo(entry)
    }

    private fun expireUndo(entry: UndoEntry) {
        undoTimer?.cancel()
        undoTimer = viewModelScope.launch {
            delay(entry.expiresAt - clock.now())
            if (session.undo.value == entry) session.clearUndo()
        }
    }

    private fun undo() {
        val entry = session.undo.value ?: return
        undoTimer?.cancel()
        session.clearUndo()
        launchCommand(StreakCommand.Restore(entry.snapshot))
    }

    // ---- Sheet ----

    private fun openSheet(draft: StreakDraft) {
        validation = null
        showDiscard = false
        session.setDraft(draft)
    }

    private fun openEdit(id: Uuid) {
        val card = readyOrNull()?.cards?.firstOrNull { it.id == id } ?: return
        openSheet(
            StreakDraft(
                editingId = id,
                name = card.name.orEmpty(),
                type = card.type,
                startValue = card.value.toString(),
                goal = card.goal?.toString().orEmpty(),
                autoReset = card.autoReset,
                goalEdited = false,
                touched = false,
            ),
        )
    }

    private fun closeSheet() {
        session.setDraft(null)
        validation = null
        showDiscard = false
    }

    private fun changeDraft(changed: StreakDraft) {
        val current = session.draft.value ?: return
        val isEdit = current.editingId != null
        val next = changed.copy(
            editingId = current.editingId,
            // Type is fixed after creation.
            type = if (isEdit) current.type else changed.type,
            goalEdited = current.goalEdited || changed.goal != current.goal,
            touched = current.touched ||
                changed.copy(goalEdited = current.goalEdited, touched = current.touched) != current,
        )
        session.setDraft(next)
        // Errors shown after a save attempt follow the fields as they are fixed.
        if (validation != null) validation = next.validate()
    }

    private fun blur(field: SheetField) {
        // Once errors are showing, every change re-checks them (changeDraft); nothing to add.
        if (validation != null) return
        val result = session.draft.value?.validate() ?: return
        // A field that is fine shows nothing (and does not switch on live checks of the others).
        validation = when (field) {
            SheetField.START -> result.startError?.let { DraftValidation(startError = it, goalError = null) }
            SheetField.GOAL -> result.goalError?.let { DraftValidation(startError = null, goalError = it) }
        }
    }

    private fun save() {
        val draft = session.draft.value ?: return
        val result = draft.validate()
        if (!result.isValid) {
            validation = result
            return
        }
        if (saveJob?.isActive == true) return
        // Repository commands apply `today` when they run, so a save after midnight uses the new day.
        saveJob = launchCommand(draft.toCommand()) { closeSheet() }
    }

    private fun StreakDraft.toCommand(): StreakCommand {
        val name = normalizeName(name)
        val count = startValue.trim().toInt()
        val goal = goal.trim().ifEmpty { null }?.toInt()
        val autoReset = autoReset && type != StreakType.DAYS_SINCE
        val id = editingId ?: return StreakCommand.Create(NewStreak(name, type, count, goal, autoReset))
        val storedGoal = readyOrNull()?.cards?.firstOrNull { it.id == id }?.goal
        return StreakCommand.Update(id, StreakEdit(name, count, goal, autoReset, goalChanged = goalEdited && goal != storedGoal))
    }

    // ---- Celebrations ----

    /** Marks the shown celebration in the session first (no replay if the save fails), then on disk. */
    private fun dismissCelebration(): CelebrationUi? {
        val celebration = readyOrNull()?.celebration ?: return null
        val goal = readyOrNull()?.cards?.firstOrNull { it.id == celebration.streakId }?.goal
        session.markShown(celebrationKey(celebration.streakId, celebration.size, goal, today.date.value))
        launchCommand(StreakCommand.MarkCelebrated(celebration.streakId))
        return celebration
    }

    // ---- Reorder ----

    private fun move(from: Int, to: Int) {
        val order = (pendingOrder ?: readyOrNull()?.cards?.map { it.id } ?: return).toMutableList()
        if (from !in order.indices || to !in order.indices) return
        order.add(to, order.removeAt(from))
        pendingOrder = order
    }

    private fun moveOne(id: Uuid, offset: Int) {
        val order = readyOrNull()?.cards?.map { it.id }?.toMutableList() ?: return
        val from = order.indexOf(id)
        val to = from + offset
        if (from < 0 || to !in order.indices) return
        order.add(to, order.removeAt(from))
        launchCommand(StreakCommand.Reorder(order))
    }

    private fun finishReorder() {
        val order = pendingOrder
        session.setReorderMode(false)
        if (order == null) return
        viewModelScope.launch {
            execute(StreakCommand.Reorder(order))
            // Show the stored order from here on; after a failure that is the old one.
            pendingOrder = null
        }
    }

    private companion object {
        val RESET_UNDO = 8.seconds
        val DELETE_UNDO = 10.seconds
        const val FAILURES_BEFORE_STUCK = 3
        const val KEY_RETRIES_BEFORE_START_FRESH = 3

        val NEW_DRAFT = StreakDraft(
            editingId = null,
            name = "",
            type = StreakType.CHECK_IN,
            startValue = "0",
            goal = "",
            // R19: a new Check-in or Times starts with its switch on.
            autoReset = true,
            goalEdited = false,
            touched = false,
        )
    }
}

/**
 * R18 first: a single day streak (Check-in or Days since) at 1 or more counts its days. Then an
 * ended note welcomes the user back; then today's check-ins; lists without check-ins have none.
 */
private fun summaryOf(cards: List<StreakCardUi>): ListSummary {
    val only = cards.singleOrNull()
    val checkIns = cards.filter { it.type == StreakType.CHECK_IN }
    val left = checkIns.count { !it.doneToday }
    return when {
        only != null && only.type != StreakType.TIMES && only.value >= 1 -> ListSummary.Day(only.value)
        cards.any { it.endedNote != null } -> ListSummary.WelcomeBack
        checkIns.isEmpty() -> ListSummary.None
        left == 0 -> ListSummary.AllCheckedIn
        else -> ListSummary.CheckInsLeft(left)
    }
}

/** BIG: once per goal value. SMALL (daily goal): once per day. */
private fun celebrationKey(id: Uuid, size: CelebrationSize, goal: Int?, date: LocalDate): String =
    if (size == CelebrationSize.BIG) "$id:goal:$goal" else "$id:day:$date"

/** [order] first; streaks it doesn't list keep their place after them. */
private fun List<Streak>.inOrder(order: List<Uuid>): List<Streak> {
    val rank = order.withIndex().associate { (index, id) -> id to index }
    return sortedBy { rank[it.id] ?: (order.size + it.position) }
}

private fun Streak.toCardUi(date: LocalDate): StreakCardUi {
    val value = valueOn(date)
    return StreakCardUi(
        id = id,
        name = name,
        type = type,
        value = value,
        doneToday = isDoneToday(date),
        goal = goal,
        progress = progressOn(date),
        goalReached = goal?.let { value >= it } ?: false,
        minusEnabled = value > 0,
        endedNote = endedNote,
        startDate = startDate,
        autoReset = autoReset,
    )
}
