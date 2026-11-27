package com.streakcounter.feature.streaks.presentation.list.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.streakcounter.core.designsystem.theme.ControlSize
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

/** Test tag on a reorder row's drag handle. */
internal fun handleTag(id: Uuid) = "drag_handle_$id"

/** True on the item being dragged (lifted); for tests and future TalkBack state. */
internal val LiftedKey = SemanticsPropertyKey<Boolean>("Lifted")
internal var SemanticsPropertyReceiver.lifted by LiftedKey

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
 * Drag-to-reorder for the one streak list (custom, no library, spec 8). It lives as long as the
 * list does, across normal and reorder mode, so a long-press on a card can lift that card and keep
 * dragging it while the screen switches to compact rows.
 *
 * Each swap is applied to [order] at once and reported through `onMove(from, to)`, so the dragged
 * row never waits for the next state. Swaps are worked out from the lazy list's layout.
 */
@Stable
internal class ReorderState(val listState: LazyListState, initial: List<StreakCardUi>) {
    /** The order shown in reorder mode; follows the stored order while nothing is dragged. */
    var order by mutableStateOf(initial)
    var draggingId by mutableStateOf<Uuid?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    internal var latestCards: List<StreakCardUi> = initial
    internal var onMove: (from: Int, to: Int) -> Unit = { _, _ -> }

    fun start(id: Uuid) {
        if (draggingId == null) order = latestCards
        draggingId = id
        dragOffset = 0f
    }

    fun stop() {
        draggingId = null
        dragOffset = 0f
    }

    fun move(from: Int, to: Int) {
        if (from == to || from !in order.indices || to !in order.indices) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        onMove(from, to)
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
}

@Composable
internal fun rememberReorderState(cards: List<StreakCardUi>, onMove: (from: Int, to: Int) -> Unit): ReorderState {
    val listState = rememberLazyListState()
    val state = remember { ReorderState(listState, cards) }
    SideEffect {
        state.latestCards = cards
        state.onMove = onMove
    }
    LaunchedEffect(cards, state.draggingId) { if (state.draggingId == null) state.order = cards }
    return state
}

/**
 * Long-press anywhere on the item, then drag. [onLift] runs when the press turns into a lift
 * (the screen uses it to enter reorder mode). The dragged item floats above the others.
 */
internal fun Modifier.reorderItem(
    reorder: ReorderState,
    id: Uuid,
    onLift: () -> Unit,
): Modifier {
    val dragging = reorder.draggingId == id
    return this
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            translationY = if (reorder.draggingId == id) reorder.dragOffset else 0f
            rotationZ = if (reorder.draggingId == id) -1f else 0f
        }
        .semantics { lifted = dragging }
        .pointerInput(id) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    onLift()
                    reorder.start(id)
                },
                onDragEnd = reorder::stop,
                onDragCancel = reorder::stop,
                onDrag = { change, amount ->
                    change.consume()
                    reorder.dragBy(id, amount.y)
                },
            )
        }
}

private class SwapTarget(val key: Any, val shift: Float)

/**
 * The row the dragged row's center is now over, and how far the dragged row's layout slot moves
 * when they swap (so the drag offset can be corrected). Null while it is over its own slot, or
 * while the layout still shows the row at an older [index] (it hasn't caught up with the last
 * swap, or the list header is still there right after a long-press lift).
 */
private fun swapTarget(listState: LazyListState, id: Uuid, index: Int, dragOffset: Float): SwapTarget? {
    val items = listState.layoutInfo.visibleItemsInfo
    val dragged = items.firstOrNull { it.key == id } ?: return null
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

/**
 * A compact reorder row: 48 dp drag handle (drag starts at once), name and subtitle, number.
 * TalkBack reads it as one item with Move up / Move down.
 */
@Composable
internal fun ReorderRow(
    card: StreakCardUi,
    today: LocalDate?,
    index: Int,
    count: Int,
    reorder: ReorderState,
    modifier: Modifier = Modifier,
) {
    val lifted = reorder.draggingId == card.id
    val moveUp = stringResource(Res.string.action_move_up)
    val moveDown = stringResource(Res.string.action_move_down)
    val shape = MaterialTheme.shapes.extraLarge
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (lifted) Modifier.shadow(8.dp, shape) else Modifier)
            .clip(shape)
            .background(
                if (lifted) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
            )
            // 4 dp + the 48 dp handle box puts the 24 dp icon 16 dp from the edge and 12 dp from
            // the name, as designed.
            .padding(start = Spacing.s1, end = Spacing.s4, top = 9.dp, bottom = 9.dp)
            .semantics(mergeDescendants = true) {
                customActions = buildList {
                    if (index > 0) add(CustomAccessibilityAction(moveUp) { reorder.move(index, index - 1); true })
                    if (index < count - 1) add(CustomAccessibilityAction(moveDown) { reorder.move(index, index + 1); true })
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(ControlSize.touch)
                .testTag(handleTag(card.id))
                .pointerInput(card.id) {
                    detectDragGestures(
                        onDragStart = { reorder.start(card.id) },
                        onDragEnd = reorder::stop,
                        onDragCancel = reorder::stop,
                        onDrag = { change, amount ->
                            change.consume()
                            reorder.dragBy(card.id, amount.y)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = vectorResource(DsRes.drawable.ic_drag_indicator),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f).padding(end = Spacing.s3)) {
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
