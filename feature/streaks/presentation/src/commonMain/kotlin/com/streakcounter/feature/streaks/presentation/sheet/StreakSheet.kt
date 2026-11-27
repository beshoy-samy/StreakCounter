package com.streakcounter.feature.streaks.presentation.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
import com.streakcounter.core.designsystem.theme.ControlSize
import com.streakcounter.core.designsystem.theme.FullShape
import com.streakcounter.core.designsystem.theme.Spacing
import com.streakcounter.core.designsystem.theme.StreakTheme
import com.streakcounter.feature.streaks.domain.FieldError
import com.streakcounter.feature.streaks.domain.MAX_VALUE
import com.streakcounter.feature.streaks.domain.StreakDraft
import com.streakcounter.feature.streaks.domain.StreakType
import com.streakcounter.feature.streaks.domain.validate
import com.streakcounter.feature.streaks.presentation.format.DateSkeleton
import com.streakcounter.feature.streaks.presentation.format.formatDate
import com.streakcounter.feature.streaks.presentation.format.formatNumber
import com.streakcounter.feature.streaks.presentation.list.SheetUi
import com.streakcounter.feature.streaks.presentation.list.StreakListAction
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import streakcounter.core.designsystem.generated.resources.ic_check_circle
import streakcounter.core.designsystem.generated.resources.ic_close
import streakcounter.core.designsystem.generated.resources.ic_delete
import streakcounter.core.designsystem.generated.resources.ic_error
import streakcounter.core.designsystem.generated.resources.ic_info
import streakcounter.core.designsystem.generated.resources.ic_lock
import streakcounter.feature.streaks.presentation.generated.resources.Res
import streakcounter.feature.streaks.presentation.generated.resources.callout_since_body
import streakcounter.feature.streaks.presentation.generated.resources.callout_since_title
import streakcounter.feature.streaks.presentation.generated.resources.error_enter_number
import streakcounter.feature.streaks.presentation.generated.resources.error_goal_not_above_count
import streakcounter.feature.streaks.presentation.generated.resources.error_goal_not_number
import streakcounter.feature.streaks.presentation.generated.resources.error_goal_zero
import streakcounter.feature.streaks.presentation.generated.resources.error_over_max
import streakcounter.feature.streaks.presentation.generated.resources.error_whole_number
import streakcounter.feature.streaks.presentation.generated.resources.field_name
import streakcounter.feature.streaks.presentation.generated.resources.field_name_optional
import streakcounter.feature.streaks.presentation.generated.resources.field_name_placeholder
import streakcounter.feature.streaks.presentation.generated.resources.field_type
import streakcounter.feature.streaks.presentation.generated.resources.goal_helper_check_in
import streakcounter.feature.streaks.presentation.generated.resources.goal_helper_days
import streakcounter.feature.streaks.presentation.generated.resources.goal_helper_per_day
import streakcounter.feature.streaks.presentation.generated.resources.goal_label
import streakcounter.feature.streaks.presentation.generated.resources.goal_label_daily
import streakcounter.feature.streaks.presentation.generated.resources.goal_placeholder
import streakcounter.feature.streaks.presentation.generated.resources.goal_reached_hint
import streakcounter.feature.streaks.presentation.generated.resources.goal_to_go
import streakcounter.feature.streaks.presentation.generated.resources.locked_type
import streakcounter.feature.streaks.presentation.generated.resources.new_streak
import streakcounter.feature.streaks.presentation.generated.resources.sheet_close
import streakcounter.feature.streaks.presentation.generated.resources.sheet_delete
import streakcounter.feature.streaks.presentation.generated.resources.sheet_edit_title
import streakcounter.feature.streaks.presentation.generated.resources.sheet_save_edit
import streakcounter.feature.streaks.presentation.generated.resources.sheet_save_new
import streakcounter.feature.streaks.presentation.generated.resources.start_check_in
import streakcounter.feature.streaks.presentation.generated.resources.start_days_since
import streakcounter.feature.streaks.presentation.generated.resources.start_days_since_helper
import streakcounter.feature.streaks.presentation.generated.resources.start_edit_count
import streakcounter.feature.streaks.presentation.generated.resources.start_edit_days_so_far
import streakcounter.feature.streaks.presentation.generated.resources.start_times
import streakcounter.feature.streaks.presentation.generated.resources.switch_break_body
import streakcounter.feature.streaks.presentation.generated.resources.switch_break_title
import streakcounter.feature.streaks.presentation.generated.resources.switch_midnight_body
import streakcounter.feature.streaks.presentation.generated.resources.switch_midnight_title
import streakcounter.feature.streaks.presentation.generated.resources.type_check_in
import streakcounter.feature.streaks.presentation.generated.resources.type_days_since
import streakcounter.feature.streaks.presentation.generated.resources.type_times
import streakcounter.core.designsystem.generated.resources.Res as DsRes

internal const val SHEET_NAME_TAG = "sheet_name"
internal const val SHEET_START_TAG = "sheet_start"
internal const val SHEET_GOAL_TAG = "sheet_goal"

/**
 * Create / edit sheet (spec 4.4). The draft lives in the ViewModel: every edit goes out as
 * OnDraftChange and comes back in [sheet]. Compose owns only focus, scroll and the sheet's own
 * animation state. [sheet] is only composed while a draft is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakSheet(sheet: SheetUi, onAction: (StreakListAction) -> Unit) {
    // A swipe-down or scrim tap hides the Material sheet before the ViewModel answers. When the
    // answer is the discard dialog ("Keep editing"), the sheet has to come back: a new key makes
    // the sheet appear again with the draft the ViewModel still holds.
    var generation by remember { mutableIntStateOf(0) }
    var hiddenByGesture by remember { mutableStateOf(false) }

    key(generation) {
        AdaptiveBottomSheet(
            onDismissRequest = {
                hiddenByGesture = true
                onAction(StreakListAction.OnSheetDismiss)
            },
            adaptiveSheetState = rememberAdaptiveSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.extraLarge.copy(
                bottomStart = ZeroCornerSize,
                bottomEnd = ZeroCornerSize,
            ),
        ) {
            StreakSheetContent(sheet, onAction)
        }
    }

    StreakSheetDialogs(
        sheet = sheet,
        onAction = onAction,
        onKeepEditing = {
            onAction(StreakListAction.OnKeepEditing)
            if (hiddenByGesture) generation++
            hiddenByGesture = false
        },
    )
}

/** T6 discard confirm. Kept out of the sheet window so it also works when the sheet is native. */
@Composable
internal fun StreakSheetDialogs(sheet: SheetUi, onAction: (StreakListAction) -> Unit, onKeepEditing: () -> Unit) {
    if (sheet.showDiscard) {
        DiscardDialog(onKeepEditing = onKeepEditing, onDiscard = { onAction(StreakListAction.OnDiscard) })
    }
}

/** The sheet's fields and buttons, without the Calf window around them. */
@Composable
internal fun StreakSheetContent(sheet: SheetUi, onAction: (StreakListAction) -> Unit) {
    val draft = sheet.draft
    val validation = sheet.validation
    val startFocus = remember { FocusRequester() }
    val goalFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun change(next: StreakDraft) = onAction(
        StreakListAction.OnDraftChange(
            next.copy(goalEdited = draft.goalEdited || next.goal != draft.goal, touched = true),
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(start = Spacing.s4, end = Spacing.s4, bottom = Spacing.s6),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        SheetHeader(isEdit = sheet.isEdit, onClose = { onAction(StreakListAction.OnSheetDismiss) })

        NameField(draft.name, optional = !sheet.isEdit) { change(draft.copy(name = it)) }

        if (sheet.isEdit) {
            LockedType(sheet.lockedType ?: draft.type)
        } else {
            TypePicker(draft.type) { type ->
                // One switch serves two meanings (break on a missed day / reset at midnight), so a
                // new type starts from off rather than carrying the other meaning over.
                change(draft.copy(type = type, autoReset = false))
            }
        }

        val dailyGoal = draft.type == StreakType.TIMES && draft.autoReset
        val count = countText(draft.startValue)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.Top) {
            SheetField(
                value = draft.startValue,
                onValueChange = { change(draft.copy(startValue = it)) },
                label = stringResource(startLabel(draft.type, sheet.isEdit)),
                tag = SHEET_START_TAG,
                focusRequester = startFocus,
                error = validation?.startError?.let { fieldErrorText(it, count) },
                helper = if (!sheet.isEdit && draft.type == StreakType.DAYS_SINCE) {
                    stringResource(Res.string.start_days_since_helper)
                } else {
                    null
                },
                imeAction = ImeAction.Next,
                onImeAction = { goalFocus.requestFocus() },
                modifier = Modifier.weight(1f),
            )
            SheetField(
                value = draft.goal,
                onValueChange = { change(draft.copy(goal = it)) },
                label = stringResource(if (dailyGoal) Res.string.goal_label_daily else Res.string.goal_label),
                placeholder = stringResource(Res.string.goal_placeholder),
                tag = SHEET_GOAL_TAG,
                focusRequester = goalFocus,
                error = validation?.goalError?.let { fieldErrorText(it, count) },
                helper = goalHelper(draft, sheet, dailyGoal),
                imeAction = ImeAction.Done,
                onImeAction = { focusManager.clearFocus() },
                modifier = Modifier.weight(1f),
            )
        }

        sheet.goalReachedOn?.let { GoalReachedHint(formatDate(it, DateSkeleton.DAY_MONTH)) }

        when (draft.type) {
            StreakType.CHECK_IN -> ResetSwitch(
                title = Res.string.switch_break_title,
                body = Res.string.switch_break_body,
                checked = draft.autoReset,
                onCheckedChange = { change(draft.copy(autoReset = it)) },
            )
            StreakType.TIMES -> ResetSwitch(
                title = Res.string.switch_midnight_title,
                body = Res.string.switch_midnight_body,
                checked = draft.autoReset,
                onCheckedChange = { change(draft.copy(autoReset = it)) },
            )
            StreakType.DAYS_SINCE -> SinceCallout()
        }

        Button(
            onClick = {
                // Focus goes to the first invalid field straight away; the ViewModel then shows
                // the messages through `validation`.
                val result = draft.validate()
                when {
                    result.startError != null -> startFocus.requestFocus()
                    result.goalError != null -> goalFocus.requestFocus()
                }
                onAction(StreakListAction.OnSave)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = ControlSize.control),
            shape = FullShape,
        ) {
            Text(
                stringResource(if (sheet.isEdit) Res.string.sheet_save_edit else Res.string.sheet_save_new),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        if (sheet.isEdit && draft.editingId != null) {
            TextButton(
                onClick = { onAction(StreakListAction.OnDelete(draft.editingId!!)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = ControlSize.touch),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(vectorResource(DsRes.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(Spacing.s2))
                Text(stringResource(Res.string.sheet_delete), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SheetHeader(isEdit: Boolean, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(if (isEdit) Res.string.sheet_edit_title else Res.string.new_streak),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose) {
            Icon(
                imageVector = vectorResource(DsRes.drawable.ic_close),
                contentDescription = stringResource(Res.string.sheet_close),
            )
        }
    }
}

@Composable
private fun NameField(value: String, optional: Boolean, onValueChange: (String) -> Unit) {
    SheetField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(if (optional) Res.string.field_name_optional else Res.string.field_name),
        placeholder = stringResource(Res.string.field_name_placeholder),
        tag = SHEET_NAME_TAG,
        keyboardType = KeyboardType.Text,
        capitalization = KeyboardCapitalization.Sentences,
        imeAction = ImeAction.Next,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Label above an outlined field, with the T1 message (error icon + text) or a helper line under
 * it. The error is also set as the field's error semantics so a screen reader announces it.
 */
@Composable
private fun SheetField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    focusRequester: FocusRequester? = null,
    error: String? = null,
    helper: String? = null,
    keyboardType: KeyboardType = KeyboardType.Number,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = Spacing.s1),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .testTag(tag)
                .semantics {
                    contentDescription = label
                    if (error != null) error(error)
                },
            placeholder = placeholder?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            isError = error != null,
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = capitalization,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = { if (onImeAction != null) onImeAction() else focusManager.moveFocus(FocusDirection.Next) },
                onDone = { onImeAction?.invoke() },
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                errorContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
            supportingText = when {
                error != null -> {
                    {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = vectorResource(DsRes.drawable.ic_error),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 2.dp).size(16.dp),
                            )
                            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                helper != null -> {
                    { Text(helper, style = MaterialTheme.typography.labelMedium) }
                }
                else -> null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypePicker(selected: StreakType, onSelect: (StreakType) -> Unit) {
    val types = StreakType.entries
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Text(stringResource(Res.string.field_type), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().heightIn(min = ControlSize.touch)) {
            types.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = type == selected,
                    onClick = { onSelect(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, types.size),
                    modifier = Modifier.heightIn(min = ControlSize.touch),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        activeBorderColor = MaterialTheme.colorScheme.outline,
                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                        inactiveBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    icon = {},
                    label = { Text(stringResource(typeName(type)), style = MaterialTheme.typography.labelLarge, maxLines = 1) },
                )
            }
        }
    }
}

@Composable
private fun LockedType(type: StreakType) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = vectorResource(DsRes.drawable.ic_lock),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(Res.string.locked_type, stringResource(typeName(type))),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GoalReachedHint(date: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = vectorResource(DsRes.drawable.ic_check_circle),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(Res.string.goal_reached_hint, date),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResetSwitch(
    title: StringResource,
    body: StringResource,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ControlSize.touch)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(body),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Days since has no switch: the count grows by itself. */
@Composable
private fun SinceCallout() {
    val colors = StreakTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.infoContainer)
            .padding(Spacing.s3),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Icon(
            imageVector = vectorResource(DsRes.drawable.ic_info),
            contentDescription = null,
            tint = colors.onInfoContainer,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                stringResource(Res.string.callout_since_title),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onInfoContainer,
            )
            Text(
                stringResource(Res.string.callout_since_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onInfoContainer,
            )
        }
    }
}

private fun typeName(type: StreakType): StringResource = when (type) {
    StreakType.CHECK_IN -> Res.string.type_check_in
    StreakType.DAYS_SINCE -> Res.string.type_days_since
    StreakType.TIMES -> Res.string.type_times
}

private fun startLabel(type: StreakType, isEdit: Boolean): StringResource = when {
    isEdit && type == StreakType.DAYS_SINCE -> Res.string.start_edit_days_so_far
    isEdit -> Res.string.start_edit_count
    type == StreakType.CHECK_IN -> Res.string.start_check_in
    type == StreakType.DAYS_SINCE -> Res.string.start_days_since
    else -> Res.string.start_times
}

/** Under the goal field: what the goal counts (new) or how far away it is (edit). */
@Composable
private fun goalHelper(draft: StreakDraft, sheet: SheetUi, dailyGoal: Boolean): String? {
    if (sheet.isEdit) {
        if (sheet.goalReachedOn != null) return null
        val goal = draft.goal.trim().toIntOrNull() ?: return null
        val count = draft.startValue.trim().toIntOrNull() ?: return null
        return if (goal > count) stringResource(Res.string.goal_to_go, formatNumber(goal - count)) else null
    }
    return when {
        dailyGoal -> stringResource(Res.string.goal_helper_per_day)
        draft.type == StreakType.CHECK_IN -> stringResource(Res.string.goal_helper_check_in)
        draft.type == StreakType.DAYS_SINCE -> stringResource(Res.string.goal_helper_days)
        else -> null
    }
}

/** The whole-number part of what is typed in the count field, for "Make it more than 12". */
private fun countText(raw: String): String = formatNumber(raw.trim().takeWhile { it in '0'..'9' }.toIntOrNull() ?: 0)

@Composable
private fun fieldErrorText(error: FieldError, count: String): String = when (error) {
    FieldError.ENTER_NUMBER -> stringResource(Res.string.error_enter_number)
    FieldError.WHOLE_NUMBER -> stringResource(Res.string.error_whole_number)
    FieldError.OVER_MAX -> stringResource(Res.string.error_over_max, formatNumber(MAX_VALUE))
    FieldError.GOAL_NOT_ABOVE_COUNT -> stringResource(Res.string.error_goal_not_above_count, count)
    FieldError.GOAL_ZERO -> stringResource(Res.string.error_goal_zero)
    FieldError.GOAL_NOT_NUMBER -> stringResource(Res.string.error_goal_not_number)
}
