package com.streakcounter.feature.streaks.presentation.list

import androidx.appstate.statestore.StateStore
import androidx.appstate.transform.testing.TransformTestScope
import androidx.appstate.transform.testing.transformTest
import androidx.lifecycle.ViewModelStore
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.streakcounter.core.domain.DataError
import com.streakcounter.core.testing.FakeStreakRepository
import com.streakcounter.core.testing.FakeToday
import com.streakcounter.core.testing.FakeWidgetUpdater
import com.streakcounter.core.testing.TestClock
import com.streakcounter.feature.streaks.domain.CelebrationMark
import com.streakcounter.feature.streaks.domain.CelebrationSize
import com.streakcounter.feature.streaks.domain.FieldError
import com.streakcounter.feature.streaks.domain.Streak
import com.streakcounter.feature.streaks.domain.StreakCommand
import com.streakcounter.feature.streaks.domain.StreakEdit
import com.streakcounter.feature.streaks.domain.StreakType
import com.streakcounter.feature.streaks.presentation.session.StreakSession
import com.streakcounter.feature.streaks.presentation.session.UndoKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class StreakListViewModelTest {

    private val day0 = LocalDate(2026, 11, 27)
    private val day1 = LocalDate(2026, 11, 28)
    private val idA = Uuid.fromLongs(0L, 1L)
    private val idB = Uuid.fromLongs(0L, 2L)
    private val idNew = Uuid.fromLongs(0L, 99L)

    private val store = ViewModelStore()

    @AfterTest
    fun tearDown() {
        // Cancels viewModelScope, which stops the transform and any undo timers.
        store.clear()
        Dispatchers.resetMain()
    }

    private class Harness(
        val vm: StreakListViewModel,
        val repo: FakeStreakRepository,
        val today: FakeToday,
        val widgets: FakeWidgetUpdater,
        val session: StreakSession,
    ) {
        val state: StreakListState get() = vm.state.value
        val ready: StreakListState.Ready get() = state as StreakListState.Ready
        fun card(id: Uuid) = ready.cards.first { it.id == id }
    }

    private fun TransformTestScope.harness(
        streaks: List<Streak> = emptyList(),
        readError: DataError.Local? = null,
    ): Harness {
        Dispatchers.setMain(testDispatcher)
        val today = FakeToday(day0)
        val repo = FakeStreakRepository(today, streaks, newId = { idNew })
        readError?.let(repo::emitReadError)
        val widgets = FakeWidgetUpdater()
        val session = StreakSession(StateStore())
        val clock = TestClock(Instant.fromEpochSeconds(1_800_000_000)) { testCoroutineScheduler.currentTime }
        val vm = StreakListViewModel(repo, today, session, clock, widgets, dispatcher = testDispatcher)
        store.put("list", vm)
        return Harness(vm, repo, today, widgets, session)
    }

    /** Runs queued work, applies snapshot writes and lets the headless composition recompose. */
    private fun TransformTestScope.settle() = repeat(4) { advance() }

    private fun Harness.act(scope: TransformTestScope, action: StreakListAction) {
        vm.onAction(action)
        scope.settle()
    }

    private fun times(
        id: Uuid = idA,
        count: Int = 5,
        position: Int = 0,
        goal: Int? = null,
        autoReset: Boolean = false,
        name: String? = "Gym",
    ) = Streak(
        id = id, name = name, type = StreakType.TIMES, autoReset = autoReset, goal = goal, position = position,
        count = count, lastCheckIn = null, startDate = null, countDate = day0, goalReachedOn = null,
        celebrated = null, endedNote = null,
    )

    private fun checkIn(
        id: Uuid = idB,
        count: Int = 3,
        position: Int = 1,
        goal: Int? = null,
        lastCheckIn: LocalDate? = day0,
        celebrated: CelebrationMark? = null,
    ) = Streak(
        id = id, name = "Read", type = StreakType.CHECK_IN, autoReset = false, goal = goal, position = position,
        count = count, lastCheckIn = lastCheckIn, startDate = null, countDate = null, goalReachedOn = null,
        celebrated = celebrated, endedNote = null,
    )

    // ---- Opening and read errors ----

    @Test
    fun starts_opening_then_ready() = transformTest {
        val h = harness(listOf(times(), checkIn()))
        assertThat(h.state).isEqualTo(StreakListState.Opening)

        settle()

        assertThat(h.ready.cards.map { it.id }).containsExactly(idA, idB)
        assertThat(h.card(idA).value).isEqualTo(5)
        assertThat(h.card(idB).doneToday).isTrue()
        assertThat(h.ready.hasCheckIns).isTrue()
        assertThat(h.ready.checkInsLeft).isEqualTo(0)
    }

    @Test
    fun unreadable_repository_gives_Unreadable() = transformTest {
        val h = harness(readError = DataError.Local.UNREADABLE)
        settle()
        assertThat(h.state).isEqualTo(StreakListState.Unreadable(DataError.Local.UNREADABLE, canStartFresh = true))
    }

    @Test
    fun key_unavailable_and_unknown_offer_only_try_again() = transformTest {
        val h = harness(readError = DataError.Local.KEY_UNAVAILABLE)
        settle()
        assertThat(h.state).isEqualTo(StreakListState.Unreadable(DataError.Local.KEY_UNAVAILABLE, canStartFresh = false))

        h.repo.emitReadError(DataError.Local.UNKNOWN)
        h.act(this, StreakListAction.OnRetryOpen)
        assertThat(h.state).isEqualTo(StreakListState.Unreadable(DataError.Local.UNKNOWN, canStartFresh = false))
    }

    @Test
    fun key_unavailable_after_three_retries_offers_start_fresh() = transformTest {
        val h = harness(readError = DataError.Local.KEY_UNAVAILABLE)
        settle()
        repeat(2) { h.act(this, StreakListAction.OnRetryOpen) }
        assertThat(h.state).isEqualTo(StreakListState.Unreadable(DataError.Local.KEY_UNAVAILABLE, canStartFresh = false))

        h.act(this, StreakListAction.OnRetryOpen)
        assertThat(h.state).isEqualTo(StreakListState.Unreadable(DataError.Local.KEY_UNAVAILABLE, canStartFresh = true))
    }

    @Test
    fun a_different_error_restarts_the_key_retry_count() = transformTest {
        val h = harness(readError = DataError.Local.KEY_UNAVAILABLE)
        settle()
        repeat(2) { h.act(this, StreakListAction.OnRetryOpen) }
        h.repo.emitReadError(DataError.Local.UNKNOWN)
        h.act(this, StreakListAction.OnRetryOpen)
        h.repo.emitReadError(DataError.Local.KEY_UNAVAILABLE)
        h.act(this, StreakListAction.OnRetryOpen)
        assertThat(h.state).isEqualTo(StreakListState.Unreadable(DataError.Local.KEY_UNAVAILABLE, canStartFresh = false))
    }

    @Test
    fun retry_open_resubscribes_after_error() = transformTest {
        val h = harness(readError = DataError.Local.UNKNOWN)
        settle()
        assertThat(h.state).isInstanceOf<StreakListState.Unreadable>()

        h.repo.setStreaks(listOf(times()))
        h.act(this, StreakListAction.OnRetryOpen)

        assertThat(h.ready.cards.map { it.id }).containsExactly(idA)
    }

    @Test
    fun start_fresh_confirm_empties_the_list() = transformTest {
        val h = harness(readError = DataError.Local.UNREADABLE)
        settle()

        h.act(this, StreakListAction.OnStartFreshConfirm)

        assertThat(h.repo.startFreshCalls).isEqualTo(1)
        assertThat(h.ready.cards).isEmpty()
    }

    // ---- Undo ----

    @Test
    fun reset_offers_undo_that_expires_after_8s() = transformTest {
        val h = harness(listOf(times()))
        settle()

        h.act(this, StreakListAction.OnReset(idA))

        assertThat(h.card(idA).value).isEqualTo(0)
        assertThat(h.ready.undo).isEqualTo(UndoUi(UndoKind.RESET, "Gym", StreakType.TIMES))
        advanceTimeBy(7_999.milliseconds)
        settle()
        assertThat(h.ready.undo).isNotNull()
        advanceTimeBy(1.milliseconds)
        settle()
        assertThat(h.ready.undo).isNull()
    }

    @Test
    fun delete_undo_10s() = transformTest {
        val h = harness(listOf(times(), checkIn()))
        settle()

        h.act(this, StreakListAction.OnDelete(idA))

        assertThat(h.ready.cards.map { it.id }).containsExactly(idB)
        assertThat(h.ready.undo).isEqualTo(UndoUi(UndoKind.DELETE, "Gym", StreakType.TIMES))
        advanceTimeBy(9_999.milliseconds)
        settle()
        assertThat(h.ready.undo).isNotNull()
        advanceTimeBy(1.milliseconds)
        settle()
        assertThat(h.ready.undo).isNull()
    }

    @Test
    fun undo_restores_snapshot() = transformTest {
        val h = harness(listOf(times(), checkIn()))
        settle()
        h.act(this, StreakListAction.OnDelete(idA))

        h.act(this, StreakListAction.OnUndo)

        assertThat(h.ready.cards.map { it.id }).containsExactly(idA, idB)
        assertThat(h.card(idA).value).isEqualTo(5)
        assertThat(h.ready.undo).isNull()
        assertThat(h.repo.executed.last()).isInstanceOf<StreakCommand.Restore>()
    }

    @Test
    fun undo_after_widget_change_restores_snapshot() = transformTest {
        val h = harness(listOf(times(count = 7)))
        settle()
        h.act(this, StreakListAction.OnReset(idA))
        assertThat(h.card(idA).value).isEqualTo(0)

        // A widget tap writes straight to the repository.
        h.repo.execute(StreakCommand.Increment(idA))
        settle()
        assertThat(h.card(idA).value).isEqualTo(1)

        h.act(this, StreakListAction.OnUndo)

        assertThat(h.card(idA).value).isEqualTo(7)
        assertThat(h.repo.current!!.single().count).isEqualTo(7)
    }

    // ---- Write failures ----

    @Test
    fun write_failure_sets_saveFailed_and_retry_replays_same_command() = transformTest {
        val h = harness(listOf(times()))
        settle()
        h.repo.shouldReturnError = DataError.Local.WRITE_FAILED

        h.act(this, StreakListAction.OnIncrement(idA))

        assertThat(h.ready.saveFailed).isTrue()
        assertThat(h.card(idA).value).isEqualTo(5)
        assertThat(h.widgets.updateAllCalls).isEqualTo(0)

        h.repo.shouldReturnError = null
        h.act(this, StreakListAction.OnRetry)

        assertThat(h.repo.executed).containsExactly(StreakCommand.Increment(idA), StreakCommand.Increment(idA))
        assertThat(h.card(idA).value).isEqualTo(6)
        assertThat(h.ready.saveFailed).isFalse()
    }

    @Test
    fun three_failures_set_savingStuck() = transformTest {
        val h = harness(listOf(times()))
        settle()
        h.repo.shouldReturnError = DataError.Local.WRITE_FAILED

        repeat(2) { h.act(this, StreakListAction.OnIncrement(idA)) }
        assertThat(h.ready.savingStuck).isFalse()
        h.act(this, StreakListAction.OnRetry)
        assertThat(h.ready.savingStuck).isTrue()

        h.repo.shouldReturnError = null
        h.act(this, StreakListAction.OnRetry)
        assertThat(h.ready.savingStuck).isFalse()
        assertThat(h.ready.saveFailed).isFalse()
    }

    @Test
    fun success_calls_widgetUpdater() = transformTest {
        val h = harness(listOf(times()))
        settle()

        h.act(this, StreakListAction.OnIncrement(idA))
        h.act(this, StreakListAction.OnDecrement(idA))

        assertThat(h.widgets.updateAllCalls).isEqualTo(2)
    }

    // ---- Celebrations ----

    @Test
    fun celebration_queue_big_before_small_and_shown_once() = transformTest {
        val dailyGoal = times(id = idA, count = 8, goal = 8, autoReset = true, position = 0, name = "Water")
        val totalGoal = checkIn(id = idB, count = 5, goal = 5, position = 1)
        val h = harness(listOf(dailyGoal, totalGoal))
        settle()

        // BIG first, even though its card is lower in the list.
        assertThat(h.ready.celebration).isEqualTo(CelebrationUi(idB, CelebrationSize.BIG, 5, "Read", StreakType.CHECK_IN))
        assertThat(h.ready.dailyGoalHit).isNull()

        // The mark fails to save: the session still remembers it was shown.
        h.repo.shouldReturnError = DataError.Local.WRITE_FAILED
        h.act(this, StreakListAction.OnCelebrationDismiss)
        assertThat(h.repo.executed.last()).isEqualTo(StreakCommand.MarkCelebrated(idB))

        assertThat(h.ready.celebration).isEqualTo(CelebrationUi(idA, CelebrationSize.SMALL, 8, "Water", StreakType.TIMES))
        assertThat(h.ready.dailyGoalHit).isEqualTo(DailyGoalUi(idA, "Water", 8))

        h.repo.shouldReturnError = null
        h.act(this, StreakListAction.OnCelebrationDismiss)
        assertThat(h.repo.executed.last()).isEqualTo(StreakCommand.MarkCelebrated(idA))
        assertThat(h.ready.celebration).isNull()
        assertThat(h.ready.dailyGoalHit).isNull()
    }

    @Test
    fun set_new_goal_dismisses_celebration_and_opens_edit_sheet() = transformTest {
        val h = harness(listOf(checkIn(id = idB, count = 5, goal = 5)))
        settle()

        h.act(this, StreakListAction.OnSetNewGoal)

        assertThat(h.ready.celebration).isNull()
        assertThat(h.repo.executed.last()).isEqualTo(StreakCommand.MarkCelebrated(idB))
        val sheet = h.ready.sheet!!
        assertThat(sheet.isEdit).isTrue()
        assertThat(sheet.draft.editingId).isEqualTo(idB)
        assertThat(sheet.lockedType).isEqualTo(StreakType.CHECK_IN)
        assertThat(sheet.goalReachedOn).isEqualTo(day0)
    }

    @Test
    fun open_sheet_holds_the_celebration_until_it_closes() = transformTest {
        val h = harness(listOf(times(id = idA), checkIn(id = idB, count = 5, goal = 5)))
        settle()
        h.act(this, StreakListAction.OnCardClick(idA))

        assertThat(h.ready.celebration).isNull()

        h.act(this, StreakListAction.OnSheetDismiss)
        assertThat(h.ready.celebration).isNotNull()
    }

    // ---- Sheet ----

    @Test
    fun save_invalid_draft_keeps_sheet_with_errors() = transformTest {
        val h = harness()
        settle()
        h.act(this, StreakListAction.OnNewClick)
        val draft = h.ready.sheet!!.draft

        h.act(this, StreakListAction.OnDraftChange(draft.copy(startValue = "1.5")))
        h.act(this, StreakListAction.OnSave)

        val sheet = h.ready.sheet!!
        assertThat(sheet.validation!!.startError).isEqualTo(FieldError.WHOLE_NUMBER)
        assertThat(h.repo.executed).isEmpty()

        // Fixing the field clears the error without another save.
        h.act(this, StreakListAction.OnDraftChange(sheet.draft.copy(startValue = "2")))
        assertThat(h.ready.sheet!!.validation!!.isValid).isTrue()
    }

    @Test
    fun save_new_draft_creates_streak_with_normalized_name() = transformTest {
        val h = harness()
        settle()
        h.act(this, StreakListAction.OnNewClick)
        val draft = h.ready.sheet!!.draft
        assertThat(draft.editingId).isNull()
        assertThat(h.ready.sheet!!.isEdit).isFalse()

        h.act(this, StreakListAction.OnDraftChange(draft.copy(name = "  Gym  ", type = StreakType.TIMES, startValue = "3", goal = "10")))
        h.act(this, StreakListAction.OnSave)

        assertThat(h.ready.sheet).isNull()
        val stored = h.repo.current!!.single()
        assertThat(stored.name).isEqualTo("Gym")
        assertThat(stored.count).isEqualTo(3)
        assertThat(stored.goal).isEqualTo(10)
        assertThat(h.card(idNew).value).isEqualTo(3)
        assertThat(h.widgets.updateAllCalls).isEqualTo(1)
    }

    @Test
    fun blank_name_is_saved_as_unnamed() = transformTest {
        val h = harness()
        settle()
        h.act(this, StreakListAction.OnNewClick)
        h.act(this, StreakListAction.OnDraftChange(h.ready.sheet!!.draft.copy(name = "   ")))
        h.act(this, StreakListAction.OnSave)

        assertThat(h.repo.current!!.single().name).isNull()
    }

    @Test
    fun edit_save_sends_update_with_edit() = transformTest {
        val h = harness(listOf(times(goal = 10)))
        settle()
        h.act(this, StreakListAction.OnCardClick(idA))
        val draft = h.ready.sheet!!.draft
        assertThat(draft.startValue).isEqualTo("5")
        assertThat(draft.goal).isEqualTo("10")
        assertThat(h.ready.sheet!!.lockedType).isEqualTo(StreakType.TIMES)

        h.act(this, StreakListAction.OnDraftChange(draft.copy(name = " Lifts ", goal = "12")))
        h.act(this, StreakListAction.OnSave)

        assertThat(h.repo.executed.single()).isEqualTo(
            StreakCommand.Update(idA, StreakEdit(name = "Lifts", count = 5, goal = 12, autoReset = false, goalChanged = true)),
        )
        assertThat(h.ready.sheet).isNull()
    }

    @Test
    fun dismiss_touched_draft_asks_discard() = transformTest {
        val h = harness()
        settle()
        h.act(this, StreakListAction.OnNewClick)
        h.act(this, StreakListAction.OnDraftChange(h.ready.sheet!!.draft.copy(name = "Gym")))

        h.act(this, StreakListAction.OnSheetDismiss)
        assertThat(h.ready.sheet!!.showDiscard).isTrue()

        h.act(this, StreakListAction.OnKeepEditing)
        assertThat(h.ready.sheet!!.showDiscard).isFalse()
        assertThat(h.ready.sheet!!.draft.name).isEqualTo("Gym")

        h.act(this, StreakListAction.OnSheetDismiss)
        h.act(this, StreakListAction.OnDiscard)
        assertThat(h.ready.sheet).isNull()
        assertThat(h.session.draft.value).isNull()
    }

    @Test
    fun dismiss_untouched_draft_closes_silently() = transformTest {
        val h = harness()
        settle()
        h.act(this, StreakListAction.OnNewClick)

        h.act(this, StreakListAction.OnSheetDismiss)

        assertThat(h.ready.sheet).isNull()
    }

    @Test
    fun delete_from_edit_sheet_closes_it() = transformTest {
        val h = harness(listOf(times()))
        settle()
        h.act(this, StreakListAction.OnCardClick(idA))

        h.act(this, StreakListAction.OnDelete(idA))

        assertThat(h.ready.sheet).isNull()
        assertThat(h.ready.cards).isEmpty()
        assertThat(h.ready).prop(StreakListState.Ready::undo).isNotNull()
    }

    @Test
    fun midnight_tick_recomputes_cards_and_save_uses_new_day() = transformTest {
        val h = harness(listOf(times(id = idA, count = 4), checkIn(id = idB, lastCheckIn = day0)))
        settle()
        assertThat(h.card(idB).doneToday).isTrue()
        h.act(this, StreakListAction.OnCardClick(idA))

        h.today.advanceDays()
        settle()

        assertThat(h.card(idB).doneToday).isFalse()
        assertThat(h.ready.checkInsLeft).isEqualTo(1)
        assertThat(h.ready.sheet).isNotNull()

        h.act(this, StreakListAction.OnDraftChange(h.ready.sheet!!.draft.copy(startValue = "6")))
        h.act(this, StreakListAction.OnSave)

        val saved = h.repo.current!!.first { it.id == idA }
        assertThat(saved.count).isEqualTo(6)
        assertThat(saved.countDate).isEqualTo(day1)
    }

    // ---- Reorder ----

    @Test
    fun reorder_moves_locally_and_saves_on_done() = transformTest {
        val h = harness(listOf(times(), checkIn()))
        settle()

        h.act(this, StreakListAction.OnReorderStart)
        assertThat(h.ready.reorderMode).isTrue()
        h.act(this, StreakListAction.OnMove(0, 1))
        assertThat(h.ready.cards.map { it.id }).containsExactly(idB, idA)
        assertThat(h.repo.executed).isEmpty()

        h.act(this, StreakListAction.OnReorderDone)

        assertThat(h.repo.executed.single()).isEqualTo(StreakCommand.Reorder(listOf(idB, idA)))
        assertThat(h.ready.reorderMode).isFalse()
        assertThat(h.repo.current!!.map { it.id }).containsExactly(idB, idA)
        assertThat(h.ready.cards.map { it.id }).containsExactly(idB, idA)
    }
}
