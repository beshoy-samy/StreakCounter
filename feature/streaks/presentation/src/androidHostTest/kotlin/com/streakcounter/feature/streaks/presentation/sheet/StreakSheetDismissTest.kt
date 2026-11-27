package com.streakcounter.feature.streaks.presentation.sheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.streakcounter.core.designsystem.theme.StreakTheme
import com.streakcounter.feature.streaks.domain.StreakDraft
import com.streakcounter.feature.streaks.domain.StreakType
import com.streakcounter.feature.streaks.presentation.UiTestBase
import com.streakcounter.feature.streaks.presentation.list.SheetUi
import com.streakcounter.feature.streaks.presentation.list.StreakListAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The real [StreakSheet] with Calf's Android sheet (a Material 3 modal sheet). A scrim tap hides
 * the sheet before the ViewModel answers; these tests mirror the ViewModel's answer
 * (touched -> discard dialog, untouched -> close) and check what the user sees.
 */
@OptIn(ExperimentalTestApi::class)
class StreakSheetDismissTest : UiTestBase() {

    private fun draft(touched: Boolean) = StreakDraft(
        editingId = null,
        name = "Gym",
        type = StreakType.CHECK_IN,
        startValue = "0",
        goal = "",
        autoReset = true,
        goalEdited = false,
        touched = touched,
    )

    /** Reduces the dismiss actions the way the ViewModel does; `null` means the sheet closed. */
    private fun androidx.compose.ui.test.ComposeUiTest.host(touched: Boolean, actions: MutableList<StreakListAction>) {
        setContent {
            var sheet by remember {
                mutableStateOf<SheetUi?>(SheetUi(draft(touched), null, false, null, null, false))
            }
            StreakTheme(darkTheme = false) {
                sheet?.let { current ->
                    StreakSheet(current) { action ->
                        actions += action
                        sheet = when (action) {
                            StreakListAction.OnSheetDismiss ->
                                if (current.draft.touched) current.copy(showDiscard = true) else null
                            StreakListAction.OnKeepEditing -> current.copy(showDiscard = false)
                            StreakListAction.OnDiscard -> null
                            else -> current
                        }
                    }
                }
            }
        }
    }

    /** Taps the scrim and lets the hide animation finish; the sheet then reports the dismiss. */
    private fun androidx.compose.ui.test.ComposeUiTest.tapScrim(actions: List<StreakListAction>) {
        waitForIdle()
        onNode(hasContentDescription("Close sheet")).performSemanticsAction(SemanticsActions.OnClick)
        mainClock.advanceTimeBy(2_000)
        waitUntil(timeoutMillis = 5_000) { actions.isNotEmpty() }
        waitForIdle()
    }

    @Test
    fun scrim_dismiss_with_changes_then_keep_editing_brings_the_sheet_back_with_the_draft() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        host(touched = true, actions = actions)

        tapScrim(actions)
        onNodeWithText("Discard this streak?").assertExists()

        onNodeWithText("Keep editing").performClick()
        waitForIdle()

        onNodeWithText("Discard this streak?").assertDoesNotExist()
        // The sheet is visible again, with what was typed.
        onNodeWithTag(SHEET_NAME_TAG).assertIsDisplayed()
        assertTrue(
            onNodeWithTag(SHEET_NAME_TAG).fetchSemanticsNode().config[SemanticsProperties.EditableText].text == "Gym",
        )
        assertEquals(listOf(StreakListAction.OnSheetDismiss, StreakListAction.OnKeepEditing), actions)
    }

    @Test
    fun scrim_dismiss_without_changes_closes_silently() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        host(touched = false, actions = actions)

        tapScrim(actions)

        onNodeWithText("Discard this streak?").assertDoesNotExist()
        onNodeWithTag(SHEET_NAME_TAG).assertDoesNotExist()
        assertEquals(listOf<StreakListAction>(StreakListAction.OnSheetDismiss), actions)
    }
}
