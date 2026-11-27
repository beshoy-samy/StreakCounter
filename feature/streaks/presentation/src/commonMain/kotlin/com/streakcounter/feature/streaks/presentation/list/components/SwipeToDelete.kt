package com.streakcounter.feature.streaks.presentation.list.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import com.streakcounter.core.designsystem.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.math.abs
import kotlin.math.roundToInt
import streakcounter.core.designsystem.generated.resources.ic_delete
import streakcounter.feature.streaks.presentation.generated.resources.Res
import streakcounter.feature.streaks.presentation.generated.resources.swipe_delete
import streakcounter.core.designsystem.generated.resources.Res as DsRes

/** Share of the card width a swipe must pass before release deletes (design: 40 %). */
internal const val DELETE_THRESHOLD = 0.4f

/**
 * Swipe toward the start edge (left in LTR, right in RTL). Only the position at release decides:
 * past [DELETE_THRESHOLD] the card slides out and [onDelete] runs; short of it the card springs
 * back, however fast the fling (design: "before that it springs back"), so no fling-velocity rule.
 *
 * The card then stays out until the list drops it (the delete landed). If [failedWrites] grows
 * while it is still listed, the delete failed and the card slides back in: the screen shows what
 * is on disk. No timer, so a slow write never brings the card back and then removes it.
 */
@Composable
internal fun SwipeToDelete(
    onDelete: () -> Unit,
    failedWrites: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentFailedWrites by rememberUpdatedState(failedWrites)
    // Physical x of the card; toward the start edge only.
    var dragX by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    // failedWrites when this card's delete was sent; null while no delete is waiting.
    var waitingSince by remember { mutableStateOf<Int?>(null) }
    // Read in composition only through this, so a drag recomposes nothing per frame: the card
    // moves in the layout phase (absoluteOffset { }) and the gesture is never disturbed.
    val showBackground by remember { derivedStateOf { dragX != 0f } }
    val startward = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    val dragState = rememberDraggableState { delta ->
        val next = dragX + delta
        dragX = if (startward < 0) next.coerceIn(-width.toFloat(), 0f) else next.coerceIn(0f, width.toFloat())
    }

    // The drag is read on the box that stays put; only the card inside moves. (On the moving card
    // its own movement would eat into the finger's deltas, and fast swipes would fall short.)
    Box(
        modifier
            .onSizeChanged { width = it.width }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = waitingSince == null,
                onDragStopped = {
                    if (width > 0 && abs(dragX) >= width * DELETE_THRESHOLD) {
                        animate(dragX, startward * width) { value, _ -> dragX = value }
                        waitingSince = currentFailedWrites
                        currentOnDelete()
                    } else {
                        animate(dragX, 0f) { value, _ -> dragX = value }
                    }
                },
            ),
    ) {
        if (showBackground) DeleteBackground(Modifier.matchParentSize())
        Box(Modifier.absoluteOffset { IntOffset(dragX.roundToInt(), 0) }) { content() }
    }

    LaunchedEffect(waitingSince, failedWrites) {
        val since = waitingSince ?: return@LaunchedEffect
        if (failedWrites > since) {
            animate(dragX, 0f) { value, _ -> dragX = value }
            // Cleared last: it is this effect's key, so clearing it first would cancel the slide.
            waitingSince = null
        }
    }
}

@Composable
private fun DeleteBackground(modifier: Modifier) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = Spacing.s6),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = MaterialTheme.colorScheme.onErrorContainer
        Icon(vectorResource(DsRes.drawable.ic_delete), contentDescription = null, tint = color)
        Text(stringResource(Res.string.swipe_delete), style = MaterialTheme.typography.labelLarge, color = color)
    }
}
