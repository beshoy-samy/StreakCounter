package com.streakcounter.feature.streaks.presentation.list.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.streakcounter.core.designsystem.theme.Spacing
import com.streakcounter.feature.streaks.domain.StreakType
import com.streakcounter.feature.streaks.presentation.format.formatNumber
import com.streakcounter.feature.streaks.presentation.list.StreakCardUi
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.uuid.Uuid
import streakcounter.core.designsystem.generated.resources.ic_drag_indicator
import streakcounter.feature.streaks.presentation.generated.resources.Res
import streakcounter.feature.streaks.presentation.generated.resources.action_move_down
import streakcounter.feature.streaks.presentation.generated.resources.action_move_up
import streakcounter.feature.streaks.presentation.generated.resources.reorder_done
import streakcounter.feature.streaks.presentation.generated.resources.reorder_title
import streakcounter.core.designsystem.generated.resources.Res as DsRes

/** Top bar of reorder mode: "Drag to reorder" and Done. */
@Composable
internal fun ReorderTopBar(onDone: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 64.dp)
            .padding(start = Spacing.s5, end = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.reorder_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDone) {
            Text(stringResource(Res.string.reorder_done), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Reorder mode (custom, no library, spec 8): compact one-line rows. Drag a row by its handle, or
 * long-press anywhere on it and drag. Each swap is sent as `onMove(from, to)` at once and also
 * applied to a local copy, so the dragged row never waits for the next state. TalkBack gets
 * Move up / Move down.
 */
@Composable
internal fun ReorderList(
    cards: List<StreakCardUi>,
    today: LocalDate?,
    onMove: (from: Int, to: Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var order by remember { mutableStateOf(cards) }
    var draggingId by remember { mutableStateOf<Uuid?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val currentOnMove by rememberUpdatedState(onMove)

    // Follow the stored order whenever nothing is being dragged.
    LaunchedEffect(cards, draggingId) { if (draggingId == null) order = cards }

    fun move(from: Int, to: Int) {
        if (from == to || from !in order.indices || to !in order.indices) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        currentOnMove(from, to)
    }

    fun dragBy(id: Uuid, deltaY: Float) {
        dragOffset += deltaY
        val from = order.indexOfFirst { it.id == id }
        val target = swapTarget(listState, id, from, dragOffset) ?: return
        val to = order.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0) return
        // The row's layout slot jumps to the target's; keep it under the finger.
        dragOffset -= target.shift
        move(from, to)
    }

    val moveUpLabel = stringResource(Res.string.action_move_up)
    val moveDownLabel = stringResource(Res.string.action_move_down)

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        itemsIndexed(order, key = { _, card -> card.id }) { index, card ->
            val dragging = card.id == draggingId
            val start = { _: Offset ->
                draggingId = card.id
                dragOffset = 0f
            }
            val stop = {
                draggingId = null
                dragOffset = 0f
            }
            ReorderRow(
                card = card,
                today = today,
                lifted = dragging,
                handleModifier = Modifier.pointerInput(card.id) {
                    detectDragGestures(
                        onDragStart = start,
                        onDragEnd = stop,
                        onDragCancel = stop,
                        onDrag = { change, amount ->
                            change.consume()
                            dragBy(card.id, amount.y)
                        },
                    )
                },
                modifier = Modifier
                    .padding(horizontal = Spacing.s4)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) dragOffset else 0f
                        rotationZ = if (dragging) -1f else 0f
                    }
                    .then(if (dragging) Modifier else Modifier.animateItem())
                    .pointerInput(card.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = start,
                            onDragEnd = stop,
                            onDragCancel = stop,
                            onDrag = { change, amount ->
                                change.consume()
                                dragBy(card.id, amount.y)
                            },
                        )
                    }
                    .semantics {
                        customActions = buildList {
                            if (index > 0) {
                                add(CustomAccessibilityAction(moveUpLabel) { move(index, index - 1); true })
                            }
                            if (index < order.lastIndex) {
                                add(CustomAccessibilityAction(moveDownLabel) { move(index, index + 1); true })
                            }
                        }
                    },
            )
        }
    }
}

private class SwapTarget(val key: Any, val shift: Float)

/**
 * The row the dragged row's center is now over, and how far the dragged row's layout slot moves
 * when they swap (so the drag offset can be corrected). Null while it is over its own slot, or
 * while the layout still shows the row at an older [index].
 */
private fun swapTarget(listState: LazyListState, id: Uuid, index: Int, dragOffset: Float): SwapTarget? {
    val items = listState.layoutInfo.visibleItemsInfo
    val dragged = items.firstOrNull { it.key == id } ?: return null
    // Layout hasn't caught up with the last swap yet; its offsets would undo it.
    if (dragged.index != index) return null
    val center = dragged.offset + dragOffset + dragged.size / 2f
    val target = items.firstOrNull { it.key != id && center >= it.offset && center <= it.offset + it.size }
        ?: return null
    val newOffset = if (target.index > dragged.index) {
        target.offset + target.size - dragged.size
    } else {
        target.offset
    }
    return SwapTarget(target.key, (newOffset - dragged.offset).toFloat())
}

@Composable
private fun ReorderRow(
    card: StreakCardUi,
    today: LocalDate?,
    lifted: Boolean,
    handleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.extraLarge
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (lifted) Modifier.shadow(8.dp, shape) else Modifier)
            .clip(shape)
            .background(
                if (lifted) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
            )
            .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = vectorResource(DsRes.drawable.ic_drag_indicator),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = handleModifier,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = streakDisplayName(card.name, card.type),
                style = MaterialTheme.typography.titleMedium,
                color = if (card.name == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = card.subtitle(today),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatNumber(card.value),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
            color = if (card.type == StreakType.CHECK_IN) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
        )
    }
}
