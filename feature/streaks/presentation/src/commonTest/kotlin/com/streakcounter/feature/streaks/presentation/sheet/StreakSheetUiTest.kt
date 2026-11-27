package com.streakcounter.feature.streaks.presentation.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.streakcounter.core.designsystem.theme.StreakTheme
import com.streakcounter.feature.streaks.domain.DraftValidation
import com.streakcounter.feature.streaks.domain.FieldError
import com.streakcounter.feature.streaks.domain.StreakDraft
import com.streakcounter.feature.streaks.domain.StreakType
import com.streakcounter.feature.streaks.domain.validate
import com.streakcounter.feature.streaks.presentation.list.SheetField
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import com.streakcounter.feature.streaks.presentation.UiTestBase
import com.streakcounter.feature.streaks.presentation.list.SheetUi
import com.streakcounter.feature.streaks.presentation.list.StreakListAction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Calf's iOS sheet is a native view controller, which `runComposeUiTest` has no host for. The tests
 * therefore render the sheet content and its dialogs directly; the Calf window around them is
 * checked on a device.
 */
@Composable
private fun TestSheet(sheet: SheetUi, onAction: (StreakListAction) -> Unit) {
    StreakSheetContent(sheet, onAction)
    StreakSheetDialogs(sheet, onAction, onKeepEditing = { onAction(StreakListAction.OnKeepEditing) })
}

@OptIn(ExperimentalTestApi::class)
class StreakSheetUiTest : UiTestBase() {

    private val id = Uuid.parse("00000000-0000-0000-0000-000000000001")

    private fun draft(
        type: StreakType = StreakType.CHECK_IN,
        start: String = "0",
        goal: String = "",
        autoReset: Boolean = true,
        editingId: Uuid? = null,
        goalEdited: Boolean = false,
    ) = StreakDraft(
        editingId = editingId,
        name = "",
        type = type,
        startValue = start,
        goal = goal,
        autoReset = autoReset,
        goalEdited = goalEdited,
        touched = false,
    )

    private fun sheet(
        draft: StreakDraft = draft(),
        validation: DraftValidation? = null,
        isEdit: Boolean = false,
        goalReachedOn: LocalDate? = null,
        showDiscard: Boolean = false,
    ) = SheetUi(
        draft = draft,
        validation = validation,
        isEdit = isEdit,
        lockedType = if (isEdit) draft.type else null,
        goalReachedOn = goalReachedOn,
        showDiscard = showDiscard,
    )

    private fun hasError(text: String) = SemanticsMatcher("error = $text") {
        SemanticsProperties.Error in it.config && it.config[SemanticsProperties.Error] == text
    }

    @Test
    fun days_since_hides_switch_and_shows_callout() = runComposeUiTest {
        setContent {
            StreakTheme(darkTheme = false) {
                TestSheet(sheet(draft(type = StreakType.DAYS_SINCE)), onAction = {})
            }
        }

        onNodeWithText("Started how many days ago").assertExists()
        onNodeWithText("0 means today").assertExists()
        onNodeWithText("Counts on its own").assertExists()
        onNodeWithText("Break the streak if I miss a day").assertDoesNotExist()
        onNodeWithText("Reset at midnight").assertDoesNotExist()
    }

    @Test
    fun times_reset_switch_relabels_goal_live() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent {
            var state by remember { mutableStateOf(sheet(draft(type = StreakType.TIMES, autoReset = false))) }
            StreakTheme(darkTheme = false) {
                TestSheet(state, onAction = {
                    actions += it
                    if (it is StreakListAction.OnDraftChange) state = state.copy(draft = it.draft)
                })
            }
        }

        onNodeWithText("Start at").assertExists()
        onNodeWithText("Goal (optional)").assertExists()
        onNodeWithText("Daily goal (optional)").assertDoesNotExist()

        onNode(isToggleable()).performClick()

        onNodeWithText("Daily goal (optional)").assertExists()
        onNodeWithText("Goal (optional)").assertDoesNotExist()
        val change = actions.filterIsInstance<StreakListAction.OnDraftChange>().last().draft
        assertTrue(change.autoReset)
        assertTrue(change.touched)
    }

    @Test
    fun invalid_start_shows_error_and_save_focuses_it() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        val invalid = draft(start = "12.5", goal = "30")
        setContent {
            StreakTheme(darkTheme = false) {
                TestSheet(sheet(invalid, validation = invalid.validate()), onAction = { actions += it })
            }
        }

        onNodeWithText("Use a whole number").assertExists()
        onNodeWithTag(SHEET_START_TAG).assert(hasError("Use a whole number"))

        onNodeWithText("Save streak").performScrollTo().performClick()

        onNodeWithTag(SHEET_START_TAG).assertIsFocused()
        assertEquals(listOf<StreakListAction>(StreakListAction.OnSave), actions)
    }

    @Test
    fun goal_not_above_count_shows_error_and_save_focuses_the_goal() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        val invalid = draft(start = "12", goal = "10", goalEdited = true)
        val validation = invalid.validate()
        assertEquals(FieldError.GOAL_NOT_ABOVE_COUNT, validation.goalError)
        assertEquals(null, validation.startError)
        setContent {
            StreakTheme(darkTheme = false) {
                TestSheet(sheet(invalid, validation = validation), onAction = { actions += it })
            }
        }

        onNodeWithText("Make it more than 12").assertExists()
        onNodeWithTag(SHEET_GOAL_TAG).assert(hasError("Make it more than 12"))

        onNodeWithText("Save streak").performScrollTo().performClick()

        onNodeWithTag(SHEET_GOAL_TAG).assertIsFocused()
        assertEquals(listOf<StreakListAction>(StreakListAction.OnSave), actions)
    }

    @Test
    fun leaving_a_typed_field_sends_blur_but_an_untouched_field_does_not() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent {
            StreakTheme(darkTheme = false) { TestSheet(sheet(), onAction = { actions += it }) }
        }

        // Focus and leave the goal without typing: nothing.
        onNodeWithTag(SHEET_GOAL_TAG).performClick()
        onNodeWithTag(SHEET_START_TAG).performClick()
        assertTrue(actions.none { it is StreakListAction.OnFieldBlur })

        // Type in the start field, then move to the goal: blur for START.
        onNodeWithTag(SHEET_START_TAG).performTextInput("1.5")
        onNodeWithTag(SHEET_GOAL_TAG).performClick()
        assertEquals(
            listOf<StreakListAction>(StreakListAction.OnFieldBlur(SheetField.START)),
            actions.filterIsInstance<StreakListAction.OnFieldBlur>(),
        )
    }

    @Test
    fun name_is_cut_at_200_characters() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent {
            StreakTheme(darkTheme = false) { TestSheet(sheet(), onAction = { actions += it }) }
        }

        onNodeWithTag(SHEET_NAME_TAG).performTextInput("x".repeat(300))

        assertEquals(200, (actions.single() as StreakListAction.OnDraftChange).draft.name.length)
    }

    @Test
    fun new_check_in_has_switch_on_and_switching_to_times_keeps_it_on() = runComposeUiTest {
        setContent {
            var state by remember { mutableStateOf(sheet()) }
            StreakTheme(darkTheme = false) {
                TestSheet(state, onAction = {
                    if (it is StreakListAction.OnDraftChange) state = state.copy(draft = it.draft)
                })
            }
        }

        onNode(isToggleable()).assertIsOn()
        onNodeWithText("Break the streak if I miss a day").assertExists()

        onNodeWithText("Times").performClick()
        onNode(isToggleable()).assertIsOn()
        onNodeWithText("Reset at midnight").assertExists()
        onNodeWithText("Daily goal (optional)").assertExists()

        onNodeWithText("Days since").performClick()
        onNode(isToggleable()).assertDoesNotExist()
    }

    @Test
    fun goal_edit_marks_goal_edited_and_touched() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent {
            StreakTheme(darkTheme = false) { TestSheet(sheet(), onAction = { actions += it }) }
        }

        onNodeWithTag(SHEET_GOAL_TAG).performTextInput("30")

        val change = (actions.single() as StreakListAction.OnDraftChange).draft
        assertEquals("30", change.goal)
        assertTrue(change.goalEdited)
        assertTrue(change.touched)
    }

    @Test
    fun edit_shows_locked_type_and_delete() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent {
            StreakTheme(darkTheme = false) {
                TestSheet(
                    sheet(
                        draft(start = "18", goal = "30", autoReset = true, editingId = id),
                        isEdit = true,
                        goalReachedOn = null,
                    ),
                    onAction = { actions += it },
                )
            }
        }

        onNodeWithText("Edit streak").assertExists()
        onNodeWithText("Type: Check-in. Can’t be changed after creating.").assertExists()
        onNodeWithText("Current count").assertExists()
        onNodeWithText("Save changes").assertExists()
        onNode(hasText("Days since")).assertDoesNotExist()

        onNodeWithText("Delete streak").performScrollTo().performClick()

        assertEquals(listOf<StreakListAction>(StreakListAction.OnDelete(id)), actions)
    }

    @Test
    fun edit_reached_shows_hint() = runComposeUiTest {
        setContent {
            StreakTheme(darkTheme = false) {
                TestSheet(
                    sheet(
                        draft(type = StreakType.DAYS_SINCE, start = "31", goal = "30", editingId = id),
                        isEdit = true,
                        goalReachedOn = LocalDate(2026, 11, 24),
                    ),
                    onAction = {},
                )
            }
        }

        onNodeWithText("Days so far").assertExists()
        onNode(hasText("Goal reached on", substring = true)).assertExists()
        onNode(hasText("Type a bigger number for a new one.", substring = true)).assertExists()
    }

    @Test
    fun close_with_changes_shows_discard() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent {
            StreakTheme(darkTheme = false) {
                TestSheet(sheet(showDiscard = true), onAction = { actions += it })
            }
        }

        onNodeWithText("Discard this streak?").assertExists()
        onNodeWithText("You’ve filled in some details. Closing now throws them away.").assertExists()

        onNodeWithText("Keep editing").performClick()
        onNodeWithText("Discard").performClick()

        assertEquals(listOf(StreakListAction.OnKeepEditing, StreakListAction.OnDiscard), actions)
    }

    @Test
    fun close_button_sends_dismiss() = runComposeUiTest {
        val actions = mutableListOf<StreakListAction>()
        setContent {
            StreakTheme(darkTheme = false) { TestSheet(sheet(), onAction = { actions += it }) }
        }

        onNode(androidx.compose.ui.test.hasContentDescription("Close")).performClick()

        assertEquals(listOf<StreakListAction>(StreakListAction.OnSheetDismiss), actions)
    }
}
