package com.streakcounter.feature.streaks.presentation.list.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.streakcounter.core.designsystem.theme.Spacing
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import streakcounter.core.designsystem.generated.resources.ic_delete
import streakcounter.feature.streaks.presentation.generated.resources.Res
import streakcounter.feature.streaks.presentation.generated.resources.swipe_delete
import streakcounter.core.designsystem.generated.resources.Res as DsRes
import kotlin.time.Duration.Companion.milliseconds

/** Share of the card width a swipe must pass before release deletes (design: 40 %). */
internal const val DELETE_THRESHOLD = 0.4f

/**
 * Swipe toward the start edge (left in LTR, right in RTL) past [DELETE_THRESHOLD] deletes; less
 * springs back. The Undo snackbar comes from the screen state, not from here.
 */
@Composable
internal fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { total -> total * DELETE_THRESHOLD })
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
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
        },
    ) { content() }

    // A delete that landed removes this card from the list (and this effect with it). If it is
    // still here after the write, the save failed: the disk still has it, so bring it back.
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            delay(RESTORE_AFTER)
            state.reset()
        }
    }
}

private val RESTORE_AFTER = 1500.milliseconds
