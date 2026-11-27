package com.streakcounter.feature.streaks.presentation.list

import androidx.compose.runtime.Composable
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
    ) = StreakListState.Ready(
        cards = cards,
        checkInsLeft = cards.count { it.type == StreakType.CHECK_IN && !it.doneToday },
        hasCheckIns = cards.any { it.type == StreakType.CHECK_IN },
        undo = undo,
        saveFailed = false,
        celebration = null,
        sheet = null,
        reorderMode = false,
        dailyGoalHit = null,
        savingStuck = false,
    )

    @Composable
    private fun Screen(state: StreakListState, actions: MutableList<StreakListAction>) {
        StreakTheme(darkTheme = false) {
            StreakListScreen(state = state, onAction = { actions += it }, today = today)
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
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
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
        setContent { Screen(ready(cards = cards), mutableListOf()) }

        onNodeWithText("Check-in · 12 of 30, 18 to go").assertExists()
        onNodeWithText("Times · goal 30 reached").assertExists()
        // Unnamed shows the type name.
        onNodeWithText("Count").assertExists()
        onNodeWithText("One check-in left today. You’ve got this.").assertExists()
    }

    @Test
    fun all_done_summary() = runComposeUiTest {
        setContent { Screen(ready(cards = listOf(card(doneToday = true))), mutableListOf()) }
        onNodeWithText("All checked in today. Nice and steady.").assertExists()
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
    fun short_swipe_springs_back() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent { Screen(ready(), actions) }

        // Slow, so Material's fling-velocity dismiss stays out of it: only the 40 % rule decides.
        onNode(hasText("Gym") and hasClickAction()).performTouchInput {
            swipeLeft(startX = right - 1f, endX = right - width * 0.2f, durationMillis = 3_000)
        }
        waitForIdle()

        // (Robolectric's synthetic slow swipe can also count as a long-press, which only starts
        // reorder mode; a real device doesn't, checked on the emulator.)
        assertTrue(actions.none { it is StreakListAction.OnDelete }, "a swipe under 40 % does not delete: $actions")
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
