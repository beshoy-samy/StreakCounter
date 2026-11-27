package com.streakcounter.feature.streaks.presentation.list

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.streakcounter.feature.streaks.domain.CelebrationSize
import com.streakcounter.feature.streaks.domain.EndedNote
import com.streakcounter.feature.streaks.presentation.list.components.LiftedKey
import com.streakcounter.feature.streaks.presentation.list.components.handleTag
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import com.streakcounter.core.designsystem.theme.StreakTheme
import com.streakcounter.core.domain.DataError
import com.streakcounter.feature.streaks.domain.StreakType
import com.streakcounter.feature.streaks.presentation.UiTestBase
import com.streakcounter.feature.streaks.presentation.celebrate.SMALL_BURST_TAG
import com.streakcounter.feature.streaks.presentation.list.components.OPENING_SKELETON_TAG
import com.streakcounter.feature.streaks.presentation.list.components.nameTag
import com.streakcounter.feature.streaks.presentation.session.UndoKind
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class StreakListScreenUiTest : UiTestBase() {

    private val gymId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val today = LocalDate(2026, 11, 27)

    private fun card(
        id: Uuid = gymId,
        name: String? = "Gym",
        type: StreakType = StreakType.CHECK_IN,
        value: Int = 3,
        doneToday: Boolean = false,
        goal: Int? = null,
    ) = StreakCardUi(
        id = id,
        name = name,
        type = type,
        value = value,
        doneToday = doneToday,
        goal = goal,
        progress = goal?.let { minOf(value.toFloat() / it, 1f) },
        goalReached = goal != null && value >= goal,
        minusEnabled = value > 0,
        endedNote = null,
        startDate = null,
        autoReset = false,
    )

    private fun ready(
        cards: List<StreakCardUi> = listOf(card()),
        undo: UndoUi? = null,
        summary: ListSummary = ListSummary.None,
        celebration: CelebrationUi? = null,
        dailyGoalHit: DailyGoalUi? = null,
    ) = StreakListState.Ready(
        cards = cards,
        checkInsLeft = cards.count { it.type == StreakType.CHECK_IN && !it.doneToday },
        hasCheckIns = cards.any { it.type == StreakType.CHECK_IN },
        undo = undo,
        saveFailed = false,
        celebration = celebration,
        sheet = null,
        reorderMode = false,
        dailyGoalHit = dailyGoalHit,
        savingStuck = false,
        summary = summary,
        failedWrites = 0,
    )

    @Composable
    private fun Screen(state: StreakListState, actions: MutableList<StreakListAction>, reducedMotion: Boolean = false) {
        StreakTheme(darkTheme = false) {
            StreakListScreen(state = state, onAction = { actions += it }, today = today, reducedMotion = reducedMotion)
        }
    }

    @Test
    fun card_click_sends_OnCardClick() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent { Screen(ready(), actions) }

        onNode(hasText("Gym") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf<StreakListAction>(StreakListAction.OnCardClick(gymId)), actions)
    }

    @Test
    fun plus_sends_OnIncrement() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent { Screen(ready(), actions) }

        onNode(hasContentDescription("Check in Gym")).performClick()

        assertEquals(listOf<StreakListAction>(StreakListAction.OnIncrement(gymId)), actions)
    }

    @Test
    fun reset_shows_undo_snackbar_text() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent { Screen(ready(undo = UndoUi(UndoKind.RESET, "Gym", StreakType.CHECK_IN)), actions) }

        onNode(hasContentDescription("Reset Gym")).performClick()
        assertEquals(listOf<StreakListAction>(StreakListAction.OnReset(gymId)), actions)

        waitForIdle()
        onNodeWithText("Gym reset to 0").assertExists()
        onNodeWithText("Undo").performClick()
        waitForIdle()
        assertEquals(StreakListAction.OnUndo, actions.last())
    }

    @Test
    fun big_celebration_overlay_actions_go_to_the_view_model() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        val big = CelebrationUi(gymId, CelebrationSize.BIG, 30, "Gym", StreakType.CHECK_IN)
        setContent { Screen(ready(celebration = big), actions) }

        onNodeWithText("Gym · 30 days").assertExists()
        onNodeWithText("Keep going").performClick()
        onNodeWithText("Set a new goal").performClick()

        assertEquals(listOf(StreakListAction.OnCelebrationDismiss, StreakListAction.OnSetNewGoal), actions)
    }

    @Test
    fun small_celebration_shows_the_daily_goal_snackbar_then_dismisses() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        val small = CelebrationUi(gymId, CelebrationSize.SMALL, 8, "Glasses of water", StreakType.TIMES)
        setContent {
            Screen(ready(celebration = small, dailyGoalHit = DailyGoalUi(gymId, "Glasses of water", 8)), actions)
        }

        waitForIdle()
        onNodeWithText("Glasses of water: 8 today. That’s your goal.").assertExists()
        // No overlay for the small one.
        onNodeWithText("Keep going").assertDoesNotExist()

        mainClock.advanceTimeBy(6_000)
        waitForIdle()
        assertEquals(listOf<StreakListAction>(StreakListAction.OnCelebrationDismiss), actions)
    }

    private val waterId = Uuid.parse("00000000-0000-0000-0000-000000000002")
    private val twoCards = listOf(
        card(),
        card(id = waterId, name = "Water", type = StreakType.TIMES, value = 8, goal = 8),
    )
    private val waterSmall = CelebrationUi(waterId, CelebrationSize.SMALL, 8, "Water", StreakType.TIMES)

    @Test
    fun list_is_unreachable_for_screen_readers_while_the_big_celebration_shows() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        val big = CelebrationUi(gymId, CelebrationSize.BIG, 30, "Gym", StreakType.CHECK_IN)
        setContent { Screen(ready(celebration = big), actions) }
        waitForIdle()

        onNode(hasContentDescription("Check in Gym")).assertDoesNotExist()
        onNode(hasContentDescription("Reset Gym")).assertDoesNotExist()
        onNodeWithText("Keep going").assertExists()
    }

    @Test
    fun list_is_reachable_without_a_celebration() = runComposeUiTest {
        setContent { Screen(ready(), mutableListOf()) }
        onNode(hasContentDescription("Check in Gym")).assertExists()
    }

    @Test
    fun small_burst_is_on_the_celebrated_card_only() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { Screen(ready(cards = twoCards, celebration = waterSmall), mutableListOf()) }
        waitForIdle()

        val burst = onNodeWithTag(SMALL_BURST_TAG).fetchSemanticsNode().boundsInRoot
        val water = onNode(hasText("Water") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val gym = onNode(hasText("Gym") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(burst.center.y in water.top..water.bottom, "burst $burst water $water")
        assertTrue(burst.center.y !in gym.top..gym.bottom, "burst $burst gym $gym")
    }

    @Test
    fun small_burst_is_gone_after_about_a_second() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { Screen(ready(cards = twoCards, celebration = waterSmall), mutableListOf()) }
        waitForIdle()
        onNodeWithTag(SMALL_BURST_TAG).assertExists()

        mainClock.advanceTimeBy(1_600)
        onNodeWithTag(SMALL_BURST_TAG).assertDoesNotExist()
    }

    @Test
    fun no_burst_for_a_big_celebration() = runComposeUiTest {
        val big = CelebrationUi(gymId, CelebrationSize.BIG, 30, "Gym", StreakType.CHECK_IN)
        setContent { Screen(ready(celebration = big), mutableListOf()) }
        waitForIdle()
        onNodeWithTag(SMALL_BURST_TAG).assertDoesNotExist()
    }

    @Test
    fun reduced_motion_small_celebration_still_ends_and_dismisses() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        mainClock.autoAdvance = false
        setContent { Screen(ready(cards = twoCards, celebration = waterSmall), actions, reducedMotion = true) }
        waitForIdle()
        // The still frame is shown for the same second, then it is gone.
        onNodeWithTag(SMALL_BURST_TAG).assertExists()

        mainClock.advanceTimeBy(1_600)
        onNodeWithTag(SMALL_BURST_TAG).assertDoesNotExist()
        mainClock.advanceTimeBy(6_000)
        waitForIdle()
        assertEquals(listOf<StreakListAction>(StreakListAction.OnCelebrationDismiss), actions)
    }

    @Test
    fun small_celebration_dismisses_even_without_a_daily_goal_hit() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        mainClock.autoAdvance = false
        setContent { Screen(ready(cards = twoCards, celebration = waterSmall, dailyGoalHit = null), actions) }
        waitForIdle()
        mainClock.advanceTimeBy(500)
        onNodeWithText("Water: 8 today. That’s your goal.").assertExists()

        mainClock.advanceTimeBy(6_000)
        waitForIdle()
        assertEquals(listOf<StreakListAction>(StreakListAction.OnCelebrationDismiss), actions)
    }

    @Test
    fun small_burst_does_not_hang_around_while_its_snackbar_waits_behind_an_undo() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        mainClock.autoAdvance = false
        val state = ready(
            cards = twoCards,
            celebration = waterSmall,
            undo = UndoUi(UndoKind.RESET, "Gym", StreakType.CHECK_IN),
        )
        setContent { Screen(state, actions) }
        waitForIdle()

        mainClock.advanceTimeBy(2_000)
        onNodeWithTag(SMALL_BURST_TAG).assertDoesNotExist()
    }

    @Test
    fun empty_state_shows_New_streak_button() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent { Screen(ready(cards = emptyList()), actions) }

        onNodeWithText("Start your first streak").assertExists()
        onNode(hasText("New streak") and hasClickAction()).performClick()

        assertEquals(listOf<StreakListAction>(StreakListAction.OnNewClick), actions)
    }

    @Test
    fun unreadable_shows_start_fresh_confirm_before_action() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent { Screen(StreakListState.Unreadable(DataError.Local.UNREADABLE, canStartFresh = true), actions) }

        onNodeWithText("Couldn’t open your streaks").assertExists()
        onNodeWithText("Start fresh").performClick()

        // Nothing deleted yet: the T6 dialog asks first.
        assertTrue(actions.isEmpty())
        onNodeWithText("Start fresh?").assertExists()
        onNodeWithText("Delete and start fresh").performClick()

        assertEquals(listOf<StreakListAction>(StreakListAction.OnStartFreshConfirm), actions)
    }

    @Test
    fun unreadable_without_start_fresh_offers_only_try_again() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent { Screen(StreakListState.Unreadable(DataError.Local.KEY_UNAVAILABLE, canStartFresh = false), actions) }

        onNodeWithText("Start fresh").assertDoesNotExist()
        onNodeWithText("Try again").performClick()

        assertEquals(listOf<StreakListAction>(StreakListAction.OnRetryOpen), actions)
    }

    @Test
    fun key_unavailable_start_fresh_says_streaks_cannot_be_recovered() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent { Screen(StreakListState.Unreadable(DataError.Local.KEY_UNAVAILABLE, canStartFresh = true), actions) }

        onNodeWithText("Start fresh").performClick()

        onNodeWithText(
            "The key that protects your streaks on this phone can’t be used. " +
                "Starting fresh deletes them, and they can’t be recovered.",
        ).assertExists()
        onNodeWithText("Keep file").performClick()
        onNodeWithText("Start fresh?").assertDoesNotExist()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun skeleton_hidden_before_300ms() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { Screen(StreakListState.Opening, mutableListOf()) }

        mainClock.advanceTimeBy(250)
        onNodeWithTag(OPENING_SKELETON_TAG).assertDoesNotExist()

        mainClock.advanceTimeBy(100)
        onNodeWithTag(OPENING_SKELETON_TAG).assertExists()
    }

    @Test
    fun long_name_is_not_truncated() = runComposeUiTest {
        val longName = "Meditate ten minutes ".repeat(15).take(300)
        setContent { Screen(ready(cards = listOf(card(name = longName))), mutableListOf()) }

        val text = onNode(hasText(longName, substring = false) and hasClickAction()).fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString { it.text }
        assertTrue(longName in text, "full 300-char name is in the card semantics")
        // Every line drawn: the name's own text layout reports no visual overflow.
        onNodeWithTag(nameTag(gymId), useUnmergedTree = true).assertExists()
        assertEquals(false, nameHasVisualOverflow(this, gymId))
    }

    @Test
    fun rtl_layout_puts_steppers_on_left() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Screen(ready(), mutableListOf())
            }
        }

        val rootWidth = onRoot().fetchSemanticsNode().boundsInRoot.width
        val plus = onNode(hasContentDescription("Check in Gym")).fetchSemanticsNode().boundsInRoot
        val minus = onNode(hasContentDescription("Take one from Gym")).fetchSemanticsNode().boundsInRoot
        val reset = onNode(hasContentDescription("Reset Gym")).fetchSemanticsNode().boundsInRoot

        val number = onNodeWithText("3", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val name = onNodeWithText("Gym", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        assertTrue(plus.left < rootWidth / 4, "plus near the left edge in RTL")
        assertTrue(plus.right <= minus.left, "plus sits further toward the end (left) than minus")
        assertTrue(minus.right < number.left, "steppers left of the number")
        assertTrue(number.left > rootWidth / 2 && name.left > rootWidth / 2, "number and name on the right")
        assertTrue(reset.right < rootWidth / 2, "reset on the left in RTL")
    }

    @Test
    fun done_check_in_tap_undoes_today() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent { Screen(ready(cards = listOf(card(doneToday = true))), actions) }

        onNode(hasContentDescription("Checked in today. Tap to undo.")).performClick()

        assertEquals(listOf<StreakListAction>(StreakListAction.OnDecrement(gymId)), actions)
    }

    @Test
    fun subtitle_and_summary_follow_spec_copy() = runComposeUiTest {
        val water = Uuid.parse("00000000-0000-0000-0000-000000000002")
        val cards = listOf(
            card(value = 12, goal = 30),
            card(id = water, name = null, type = StreakType.TIMES, value = 31, goal = 30),
        )
        setContent { Screen(ready(cards = cards, summary = ListSummary.CheckInsLeft(1)), mutableListOf()) }

        onNodeWithText("Check-in · 12 of 30, 18 to go").assertExists()
        onNodeWithText("Times · goal 30 reached").assertExists()
        // Unnamed shows the type name.
        onNodeWithText("Count").assertExists()
        onNodeWithText("One check-in left today. You’ve got this.").assertExists()
    }

    @Test
    fun all_done_summary() = runComposeUiTest {
        setContent {
            Screen(ready(cards = listOf(card(doneToday = true)), summary = ListSummary.AllCheckedIn), mutableListOf())
        }
        onNodeWithText("All checked in today. Nice and steady.").assertExists()
    }

    @Test
    fun day_and_welcome_back_summaries() = runComposeUiTest {
        var state by mutableStateOf(ready(summary = ListSummary.Day(4)))
        setContent { Screen(state, mutableListOf()) }
        onNodeWithText("Day 4. Keep it going.").assertExists()

        state = ready(summary = ListSummary.WelcomeBack)
        onNodeWithText("Welcome back. Pick up where it feels right.").assertExists()

        state = ready(summary = ListSummary.CheckInsLeft(3))
        onNodeWithText("3 check-ins left today. You’ve got this.").assertExists()
    }

    @Test
    fun ended_note_shows_the_year_only_when_it_is_not_this_year() = runComposeUiTest {
        var state by mutableStateOf(ready(cards = listOf(card(value = 0).copy(endedNote = EndedNote(LocalDate(2026, 11, 25), 12)))))
        setContent { Screen(state, mutableListOf()) }
        val thisYear = onNode(hasText("12 days", substring = true), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text
        assertTrue("2026" !in thisYear, thisYear)

        state = ready(cards = listOf(card(value = 0).copy(endedNote = EndedNote(LocalDate(2025, 12, 30), 12))))
        val lastYear = onNode(hasText("12 days", substring = true), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text
        assertTrue("2025" in lastYear, lastYear)
    }

    @Test
    fun card_has_custom_accessibility_actions() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        val second = Uuid.parse("00000000-0000-0000-0000-000000000003")
        setContent { Screen(ready(cards = listOf(card(), card(id = second, name = "Read"))), actions) }

        val custom = onNode(hasText("Gym") and hasClickAction()).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        assertEquals(listOf("Check in", "Take one", "Reset", "Delete", "Move down"), custom.map { it.label })

        runOnUiThread { custom.first { it.label == "Delete" }.action() }
        assertEquals(StreakListAction.OnDelete(gymId), actions.last())

        // Outside reorder mode a move is one action, saved at once.
        runOnUiThread { custom.first { it.label == "Move down" }.action() }
        assertEquals(StreakListAction.OnMoveOne(gymId, 1), actions.last())
        val readUp = onNode(hasText("Read") and hasClickAction()).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].first { it.label == "Move up" }
        runOnUiThread { readUp.action() }
        assertEquals(StreakListAction.OnMoveOne(second, -1), actions.last())
        // Long-press for TalkBack starts reorder mode.
        onNode(hasText("Gym") and hasClickAction()).performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(StreakListAction.OnReorderStart, actions.last())
    }

    @Test
    fun reorder_rows_move_up_and_down_for_talkback() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        val second = Uuid.parse("00000000-0000-0000-0000-000000000003")
        setContent {
            Screen(ready(cards = listOf(card(), card(id = second, name = "Read"))).copy(reorderMode = true), actions)
        }

        val gym = onNode(hasText("Gym") and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Move down"), gym.map { it.label })
        runOnUiThread { gym.single().action() }
        assertEquals(StreakListAction.OnMove(0, 1), actions.last())

        waitForIdle()
        // The local order already moved, so "Read" is now first and Gym can go back up.
        val gymNow = onNode(hasText("Gym") and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Move up"), gymNow.map { it.label })
        runOnUiThread { gymNow.single().action() }
        assertEquals(StreakListAction.OnMove(1, 0), actions.last())
    }

    @Test
    fun reorder_handle_is_a_48dp_target() = runComposeUiTest {
        setContent { Screen(ready().copy(reorderMode = true), mutableListOf()) }
        onNodeWithTag(handleTag(gymId), useUnmergedTree = true)
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun handle_drag_moves_the_row() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        val second = Uuid.parse("00000000-0000-0000-0000-000000000003")
        setContent {
            Screen(ready(cards = listOf(card(), card(id = second, name = "Read"))).copy(reorderMode = true), actions)
        }

        onNodeWithTag(handleTag(gymId), useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, 12.dp.toPx())) }
            up()
        }
        waitForIdle()

        assertEquals(listOf<StreakListAction>(StreakListAction.OnMove(0, 1)), actions)
    }

    @Test
    fun long_press_lifts_the_pressed_card_into_reorder_mode() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        val second = Uuid.parse("00000000-0000-0000-0000-000000000003")
        var state by mutableStateOf(ready(cards = listOf(card(), card(id = second, name = "Read"))))
        setContent {
            StreakTheme(darkTheme = false) {
                StreakListScreen(
                    state = state,
                    onAction = {
                        actions += it
                        if (it == StreakListAction.OnReorderStart) state = state.copy(reorderMode = true)
                    },
                    today = today,
                )
            }
        }

        onNode(hasText("Read") and hasClickAction()).performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveBy(Offset(0f, 2f))
        }
        waitForIdle()

        assertEquals(StreakListAction.OnReorderStart, actions.first())
        onNodeWithText("Drag to reorder").assertExists()
        onNode(SemanticsMatcher.expectValue(LiftedKey, true) and hasAnyDescendant(hasText("Read"))).assertExists()
        onNode(SemanticsMatcher.expectValue(LiftedKey, false) and hasAnyDescendant(hasText("Gym"))).assertExists()
        onRoot().performTouchInput { up() }
        waitForIdle()
        onNode(SemanticsMatcher.expectValue(LiftedKey, true)).assertDoesNotExist()
        assertTrue(actions.none { it is StreakListAction.OnCardClick }, "a lift is not a tap: $actions")
    }

    @Test
    fun reorder_mode_shows_top_bar_and_done() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent { Screen(ready().copy(reorderMode = true), actions) }

        onNodeWithText("Drag to reorder").assertExists()
        onNodeWithText("New streak").assertDoesNotExist()
        onNodeWithText("Done").performClick()

        assertEquals(listOf<StreakListAction>(StreakListAction.OnReorderDone), actions)
    }

    @Test
    fun save_failed_snackbar_times_out_with_OnSaveFailedDismiss() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent { Screen(ready().copy(saveFailed = true), actions) }

        onNodeWithText("Couldn’t save that change.").assertExists()
        onNodeWithText("Try again").assertExists()
        assertTrue(actions.isEmpty())

        mainClock.advanceTimeBy(60_000)
        waitForIdle()
        assertEquals(listOf<StreakListAction>(StreakListAction.OnSaveFailedDismiss), actions)
    }

    @Test
    fun save_failed_try_again_sends_OnRetry() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent { Screen(ready().copy(saveFailed = true), actions) }

        onNodeWithText("Try again").performClick()
        waitForIdle()

        assertEquals(listOf<StreakListAction>(StreakListAction.OnRetry), actions)
    }

    @Test
    fun swipe_toward_start_past_threshold_deletes() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent { Screen(ready(), actions) }

        onNode(hasText("Gym") and hasClickAction()).performTouchInput { swipeLeft(startX = right - 1f, endX = left) }
        waitForIdle()

        assertEquals(listOf<StreakListAction>(StreakListAction.OnDelete(gymId)), actions)
    }

    @Test
    fun rtl_swipe_toward_start_is_to_the_right() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Screen(ready(), actions) }
        }

        onNode(hasText("Gym") and hasClickAction()).performTouchInput { swipeLeft(startX = right - 1f, endX = left) }
        waitForIdle()
        assertTrue(actions.isEmpty(), "toward the end edge does nothing in RTL: $actions")

        onNode(hasText("Gym") and hasClickAction()).performTouchInput { swipeRight(startX = left + 1f, endX = right) }
        waitForIdle()
        assertEquals(listOf<StreakListAction>(StreakListAction.OnDelete(gymId)), actions)
    }

    @Test
    fun fast_short_fling_springs_back() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent { Screen(ready(), actions) }
        val card = onNode(hasText("Gym") and hasClickAction())
        val restLeft = card.fetchSemanticsNode().boundsInRoot.left

        // 30 % in 40 ms: a hard fling, but short of 40 %.
        card.performTouchInput { swipeLeft(startX = right - 1f, endX = right - width * 0.3f, durationMillis = 40) }
        waitForIdle()

        assertTrue(actions.isEmpty(), "a fling under 40 % does not delete: $actions")
        assertEquals(restLeft, card.fetchSemanticsNode().boundsInRoot.left)
    }

    @Test
    fun short_swipe_springs_back() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent { Screen(ready(), actions) }

        val card = onNode(hasText("Gym") and hasClickAction())
        val restLeft = card.fetchSemanticsNode().boundsInRoot.left

        card.performTouchInput {
            down(Offset(right - 1f, centerY))
            repeat(10) { moveBy(Offset(-width * 0.02f, 0f), delayMillis = 30) }
        }
        // It really is being swiped (not taken as a long-press).
        assertTrue(card.fetchSemanticsNode().boundsInRoot.left < restLeft - 10f, "card follows the finger")
        card.performTouchInput { up() }
        waitForIdle()

        assertTrue(actions.isEmpty(), "a swipe under 40 % does nothing: $actions")
        assertEquals(restLeft, card.fetchSemanticsNode().boundsInRoot.left)
    }

    @Test
    fun swiped_card_waits_for_the_delete_and_comes_back_when_it_fails() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        var state by mutableStateOf(ready())
        setContent { StreakTheme(darkTheme = false) { StreakListScreen(state, { actions += it }, today) } }
        val card = onNode(hasText("Gym") and hasClickAction())
        val restLeft = card.fetchSemanticsNode().boundsInRoot.left

        card.performTouchInput { swipeLeft(startX = right - 1f, endX = left) }
        waitForIdle()
        assertEquals(listOf<StreakListAction>(StreakListAction.OnDelete(gymId)), actions)

        // A slow write: no timer brings it back.
        mainClock.advanceTimeBy(10_000)
        waitForIdle()
        // positionInRoot is unclipped (boundsInRoot stops at the list's edge).
        assertTrue(card.fetchSemanticsNode().positionInRoot.x < restLeft - 100f, "still swiped away")

        // The write failed: the card is still listed, so it slides back.
        state = state.copy(saveFailed = true, failedWrites = 1)
        waitForIdle()
        assertEquals(restLeft, card.fetchSemanticsNode().boundsInRoot.left)
    }

    @Test
    fun reset_top_edge_is_the_reset_button_not_the_card() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent { Screen(ready(), actions) }
        val reset = onNode(hasContentDescription("Reset Gym"))

        reset.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        reset.performTouchInput { click(Offset(centerX, 2f)) }
        reset.performTouchInput { click(Offset(width - 2f, centerY)) }

        assertEquals(List<StreakListAction>(2) { StreakListAction.OnReset(gymId) }, actions)
    }

    @Test
    fun unreadable_buttons_reachable_with_large_text_on_a_small_screen() = runComposeUiTest {
        setContent {
            SmallScreenLargeText { Screen(StreakListState.Unreadable(DataError.Local.UNREADABLE, canStartFresh = true), mutableListOf()) }
        }
        onNodeWithText("Try again").performScrollTo().assertIsDisplayed()
        onNodeWithText("Start fresh").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun empty_button_reachable_with_large_text_on_a_small_screen() = runComposeUiTest {
        setContent { SmallScreenLargeText { Screen(ready(cards = emptyList()), mutableListOf()) } }
        onNode(hasText("New streak") and hasClickAction()).performScrollTo().assertIsDisplayed()
    }

    @Composable
    private fun SmallScreenLargeText(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
            Box(Modifier.size(width = 320.dp, height = 420.dp)) { content() }
        }
    }

    @Test
    fun saving_stuck_shows_banner() = runComposeUiTest {
        setContent { Screen(ready().copy(savingStuck = true), mutableListOf()) }
        onNodeWithText("Changes aren’t being saved. Free up some storage.").assertExists()
    }
}

/** Reads the name text's layout overflow through the node's GetTextLayoutResult action. */
@OptIn(ExperimentalTestApi::class)
private fun nameHasVisualOverflow(provider: SemanticsNodeInteractionsProvider, id: Uuid): Boolean {
    val node = provider.onNodeWithTag(nameTag(id), useUnmergedTree = true).fetchSemanticsNode()
    val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.single().hasVisualOverflow
}
