package com.streakcounter.feature.streaks.presentation.list.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.streakcounter.core.designsystem.components.MinusButton
import com.streakcounter.core.designsystem.components.PlusButton
import com.streakcounter.core.designsystem.components.PlusKind
import com.streakcounter.core.designsystem.components.StreakCardFrame
import com.streakcounter.core.designsystem.theme.Spacing
import com.streakcounter.feature.streaks.domain.StreakType
import com.streakcounter.feature.streaks.presentation.format.formatNumber
import com.streakcounter.feature.streaks.presentation.list.StreakCardUi
import com.streakcounter.feature.streaks.presentation.list.StreakListAction
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.uuid.Uuid
import streakcounter.core.designsystem.generated.resources.ic_history
import streakcounter.core.designsystem.generated.resources.ic_restart_alt
import streakcounter.feature.streaks.presentation.generated.resources.Res
import streakcounter.feature.streaks.presentation.generated.resources.action_add
import streakcounter.feature.streaks.presentation.generated.resources.action_add_day
import streakcounter.feature.streaks.presentation.generated.resources.action_check_in
import streakcounter.feature.streaks.presentation.generated.resources.action_delete
import streakcounter.feature.streaks.presentation.generated.resources.action_move_down
import streakcounter.feature.streaks.presentation.generated.resources.action_move_up
import streakcounter.feature.streaks.presentation.generated.resources.action_reorder
import streakcounter.feature.streaks.presentation.generated.resources.action_reset
import streakcounter.feature.streaks.presentation.generated.resources.action_take_one
import streakcounter.feature.streaks.presentation.generated.resources.action_undo_check_in
import streakcounter.feature.streaks.presentation.generated.resources.card_add
import streakcounter.feature.streaks.presentation.generated.resources.card_add_day
import streakcounter.feature.streaks.presentation.generated.resources.card_add_label
import streakcounter.feature.streaks.presentation.generated.resources.card_check_in
import streakcounter.feature.streaks.presentation.generated.resources.card_check_in_label
import streakcounter.feature.streaks.presentation.generated.resources.card_done
import streakcounter.feature.streaks.presentation.generated.resources.card_done_label
import streakcounter.feature.streaks.presentation.generated.resources.card_edit
import streakcounter.feature.streaks.presentation.generated.resources.card_reset
import streakcounter.feature.streaks.presentation.generated.resources.card_take_one
import streakcounter.core.designsystem.generated.resources.Res as DsRes

/** Test tag on the name text, so tests can read its layout (no truncation, Review Focus 3). */
internal fun nameTag(id: Uuid) = "streak_name_$id"

/**
 * One streak: name (wraps fully, never truncated), subtitle, reset, the big number with its unit,
 * minus and the type's plus button, and the one-time ended note. The whole card opens the edit
 * sheet; long-press starts reorder mode. Every button is also a custom accessibility action.
 */
@Composable
internal fun StreakCard(
    card: StreakCardUi,
    today: LocalDate?,
    index: Int,
    count: Int,
    onAction: (StreakListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = streakDisplayName(card.name, card.type)
    val plus = plusSpec(card, name)
    val actions = cardActions(card, index, count, onAction)
    StreakCardFrame(
        progress = card.progress,
        onClick = { onAction(StreakListAction.OnCardClick(card.id)) },
        onClickLabel = stringResource(Res.string.card_edit, name),
        onLongClick = { onAction(StreakListAction.OnReorderStart) },
        onLongClickLabel = stringResource(Res.string.action_reorder),
        modifier = modifier.semantics { customActions = actions },
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (card.name == null) FontWeight.Medium else null,
                    color = if (card.name == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.testTag(nameTag(card.id)),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = card.subtitle(today),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { onAction(StreakListAction.OnReset(card.id)) },
                // Design: the 48 dp target pulls 12 dp up and 8 dp toward the end, into the padding.
                modifier = Modifier.pullIntoPadding(top = 12, end = 8),
            ) {
                Icon(
                    imageVector = vectorResource(DsRes.drawable.ic_restart_alt),
                    contentDescription = stringResource(Res.string.card_reset, name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = Spacing.s2),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Text(
                    text = formatNumber(card.value),
                    style = MaterialTheme.typography.displayLarge,
                    color = if (card.type == StreakType.CHECK_IN) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = card.unit(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                MinusButton(
                    enabled = card.minusEnabled,
                    contentDescription = stringResource(Res.string.card_take_one, name),
                    onClick = { onAction(StreakListAction.OnDecrement(card.id)) },
                )
                PlusButton(
                    kind = plus.kind,
                    label = plus.label,
                    onClick = { onAction(plus.action) },
                    modifier = if (plus.kind == PlusKind.QUIET) {
                        Modifier
                    } else {
                        Modifier.semantics {
                            this.contentDescription = plus.spoken
                        }
                    },
                )
            }
        }
        card.endedNote?.let { note ->
            Row(
                modifier = Modifier
                    .padding(top = Spacing.s3)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Icon(
                    imageVector = vectorResource(DsRes.drawable.ic_history),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(note.text(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private class PlusSpec(val kind: PlusKind, val label: String, val spoken: String, val action: StreakListAction)

@Composable
private fun plusSpec(card: StreakCardUi, name: String): PlusSpec = when (card.type) {
    StreakType.CHECK_IN -> if (card.doneToday) {
        // Spec 4.1: minus (and the Done pill) undo today's check-in.
        PlusSpec(
            PlusKind.DONE,
            stringResource(Res.string.card_done_label),
            stringResource(Res.string.card_done),
            StreakListAction.OnDecrement(card.id),
        )
    } else {
        PlusSpec(
            PlusKind.CHECK_IN,
            stringResource(Res.string.card_check_in_label),
            stringResource(Res.string.card_check_in, name),
            StreakListAction.OnIncrement(card.id),
        )
    }
    StreakType.DAYS_SINCE -> {
        val spoken = stringResource(Res.string.card_add_day, name)
        PlusSpec(PlusKind.QUIET, spoken, spoken, StreakListAction.OnIncrement(card.id))
    }
    StreakType.TIMES -> PlusSpec(
        PlusKind.ADD,
        stringResource(Res.string.card_add_label),
        stringResource(Res.string.card_add, name),
        StreakListAction.OnIncrement(card.id),
    )
}

/** Check in / Undo check-in / Take one / Reset / Delete / Move up / Move down. */
@Composable
private fun cardActions(
    card: StreakCardUi,
    index: Int,
    count: Int,
    onAction: (StreakListAction) -> Unit,
): List<CustomAccessibilityAction> {
    val checkIn = stringResource(Res.string.action_check_in)
    val undoCheckIn = stringResource(Res.string.action_undo_check_in)
    val addDay = stringResource(Res.string.action_add_day)
    val add = stringResource(Res.string.action_add)
    val takeOne = stringResource(Res.string.action_take_one)
    val reset = stringResource(Res.string.action_reset)
    val delete = stringResource(Res.string.action_delete)
    val moveUp = stringResource(Res.string.action_move_up)
    val moveDown = stringResource(Res.string.action_move_down)
    fun action(label: String, block: () -> Unit) = CustomAccessibilityAction(label) { block(); true }
    fun send(action: StreakListAction) = { onAction(action) }
    fun move(to: Int) = {
        // Outside reorder mode a single move is saved straight away.
        onAction(StreakListAction.OnReorderStart)
        onAction(StreakListAction.OnMove(index, to))
        onAction(StreakListAction.OnReorderDone)
    }
    return buildList {
        val checkedIn = card.type == StreakType.CHECK_IN && card.doneToday
        if (checkedIn) {
            add(action(undoCheckIn, send(StreakListAction.OnDecrement(card.id))))
        } else {
            val plusLabel = when (card.type) {
                StreakType.CHECK_IN -> checkIn
                StreakType.DAYS_SINCE -> addDay
                StreakType.TIMES -> add
            }
            add(action(plusLabel, send(StreakListAction.OnIncrement(card.id))))
            if (card.minusEnabled) add(action(takeOne, send(StreakListAction.OnDecrement(card.id))))
        }
        add(action(reset, send(StreakListAction.OnReset(card.id))))
        add(action(delete, send(StreakListAction.OnDelete(card.id))))
        if (index > 0) add(action(moveUp, move(index - 1)))
        if (index < count - 1) add(action(moveDown, move(index + 1)))
    }
}

/**
 * Like CSS negative margins: reports a size [top] dp shorter and [end] dp narrower and draws the
 * content shifted up and toward the end, so a 48 dp target doesn't push the card's rows apart.
 */
private fun Modifier.pullIntoPadding(top: Int, end: Int): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val topPx = top.dp.roundToPx()
    val endPx = end.dp.roundToPx()
    layout(placeable.width - endPx, placeable.height - topPx) {
        placeable.placeRelative(0, -topPx)
    }
}
