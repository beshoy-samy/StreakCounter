package com.streakcounter.feature.streaks.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streakcounter.core.designsystem.theme.ControlSize
import com.streakcounter.core.designsystem.theme.Spacing
import com.streakcounter.core.designsystem.theme.StreakTheme
import com.streakcounter.feature.streaks.presentation.format.DateSkeleton
import com.streakcounter.feature.streaks.presentation.format.formatDate
import com.streakcounter.feature.streaks.presentation.format.formatNumber
import com.streakcounter.feature.streaks.presentation.list.components.EmptyState
import com.streakcounter.feature.streaks.presentation.list.components.OpeningSkeleton
import com.streakcounter.feature.streaks.presentation.list.components.ReorderList
import com.streakcounter.feature.streaks.presentation.list.components.ReorderTopBar
import com.streakcounter.feature.streaks.presentation.list.components.StreakCard
import com.streakcounter.feature.streaks.presentation.list.components.SwipeToDelete
import com.streakcounter.feature.streaks.presentation.list.components.UnreadableState
import com.streakcounter.feature.streaks.presentation.list.components.streakDisplayName
import com.streakcounter.feature.streaks.presentation.session.UndoKind
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import streakcounter.core.designsystem.generated.resources.ic_add
import streakcounter.core.designsystem.generated.resources.ic_error
import streakcounter.core.designsystem.generated.resources.ic_swap_vert
import streakcounter.feature.streaks.presentation.generated.resources.Res
import streakcounter.feature.streaks.presentation.generated.resources.list_title
import streakcounter.feature.streaks.presentation.generated.resources.new_streak
import streakcounter.feature.streaks.presentation.generated.resources.reorder_streaks
import streakcounter.feature.streaks.presentation.generated.resources.saving_stuck
import streakcounter.feature.streaks.presentation.generated.resources.snackbar_deleted
import streakcounter.feature.streaks.presentation.generated.resources.snackbar_reset
import streakcounter.feature.streaks.presentation.generated.resources.snackbar_save_failed
import streakcounter.feature.streaks.presentation.generated.resources.snackbar_undo
import streakcounter.feature.streaks.presentation.generated.resources.summary_all_done
import streakcounter.feature.streaks.presentation.generated.resources.summary_check_ins_left
import streakcounter.feature.streaks.presentation.generated.resources.summary_welcome_back
import streakcounter.feature.streaks.presentation.generated.resources.try_again
import streakcounter.core.designsystem.generated.resources.Res as DsRes

/**
 * The only screen: header, streak cards, FAB "New streak", T3 snackbars above the FAB, and the
 * Opening / Empty / Can't-open / Reorder states. [today] drives the header date and the
 * "since 3 Jul 2023" year rule; null hides the date.
 */
@Composable
fun StreakListScreen(
    state: StreakListState,
    onAction: (StreakListAction) -> Unit,
    today: LocalDate? = null,
) {
    val snackbarHost = remember { SnackbarHostState() }
    val ready = state as? StreakListState.Ready
    val reorderMode = ready?.reorderMode == true
    val showFab = ready != null && ready.cards.isNotEmpty() && !reorderMode

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { if (reorderMode) ReorderTopBar(onDone = { onAction(StreakListAction.OnReorderDone) }) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            if (showFab) NewStreakFab(onClick = { onAction(StreakListAction.OnNewClick) })
        },
    ) { padding ->
        when (state) {
            StreakListState.Opening -> Column(Modifier.fillMaxSize().padding(padding)) {
                ListHeader(today = today)
                Spacer(Modifier.height(Spacing.s4))
                OpeningSkeleton()
            }
            is StreakListState.Unreadable -> Column(Modifier.fillMaxSize().padding(padding)) {
                ListHeader(today = today)
                UnreadableState(
                    error = state.error,
                    canStartFresh = state.canStartFresh,
                    onRetry = { onAction(StreakListAction.OnRetryOpen) },
                    onStartFreshConfirm = { onAction(StreakListAction.OnStartFreshConfirm) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
            is StreakListState.Ready -> when {
                state.reorderMode -> ReorderList(
                    cards = state.cards,
                    today = today,
                    onMove = { from, to -> onAction(StreakListAction.OnMove(from, to)) },
                    contentPadding = padding.plus(top = Spacing.s3, bottom = Spacing.s6),
                    modifier = Modifier.fillMaxSize(),
                )
                state.cards.isEmpty() -> Column(Modifier.fillMaxSize().padding(padding)) {
                    ListHeader(today = today)
                    EmptyState(
                        onNewClick = { onAction(StreakListAction.OnNewClick) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
                else -> StreakList(state, today, onAction, padding)
            }
        }
    }

    SnackbarEffect(ready, snackbarHost, onAction)

    // Task 10 renders the create/edit sheet here from `ready.sheet`.
    // Task 11 renders the celebration overlay (and the daily-goal snackbar) here from
    // `ready.celebration` / `ready.dailyGoalHit`.
}

@Composable
private fun StreakList(
    state: StreakListState.Ready,
    today: LocalDate?,
    onAction: (StreakListAction) -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // The list ends with room for the pinned FAB plus 48 dp, so the last card clears it.
        contentPadding = padding.plus(bottom = ControlSize.control + Spacing.s4 + Spacing.s12),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        item(key = "header") {
            Column {
                ListHeader(today = today, onReorder = { onAction(StreakListAction.OnReorderStart) })
                summary(state)?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.s5),
                    )
                }
                if (state.savingStuck) SavingStuckBanner(Modifier.padding(start = Spacing.s4, end = Spacing.s4, top = Spacing.s3))
                Spacer(Modifier.height(Spacing.s1))
            }
        }
        itemsIndexed(state.cards, key = { _, card -> card.id }) { index, card ->
            SwipeToDelete(
                onDelete = { onAction(StreakListAction.OnDelete(card.id)) },
                modifier = Modifier.padding(horizontal = Spacing.s4).animateItem(),
            ) {
                StreakCard(card = card, today = today, index = index, count = state.cards.size, onAction = onAction)
            }
        }
    }
}

/** Date, "Your streaks" and (when there is something to order) the reorder button. */
@Composable
private fun ListHeader(today: LocalDate?, onReorder: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = Spacing.s5, end = Spacing.s2, top = Spacing.s8, bottom = Spacing.s1),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Column(Modifier.weight(1f)) {
            if (today != null) {
                Text(
                    text = formatDate(today, DateSkeleton.HEADER),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(Res.string.list_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 40.sp),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (onReorder != null) {
            IconButton(onClick = onReorder) {
                Icon(
                    imageVector = vectorResource(DsRes.drawable.ic_swap_vert),
                    contentDescription = stringResource(Res.string.reorder_streaks),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Spacer(Modifier.height(Spacing.s1))
}

/**
 * "Welcome back." after a streak ended while away; otherwise the check-in count for today.
 * Lists with no check-in streaks have no summary.
 */
@Composable
private fun summary(state: StreakListState.Ready): String? = when {
    state.cards.any { it.endedNote != null } -> stringResource(Res.string.summary_welcome_back)
    !state.hasCheckIns -> null
    state.checkInsLeft == 0 -> stringResource(Res.string.summary_all_done)
    else -> pluralStringResource(Res.plurals.summary_check_ins_left, state.checkInsLeft, formatNumber(state.checkInsLeft))
}

/** T4: repeated save failures are a lasting condition, not a one-off snackbar. */
@Composable
private fun SavingStuckBanner(modifier: Modifier = Modifier) {
    val colors = StreakTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.warningContainer)
            .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Icon(
            imageVector = vectorResource(DsRes.drawable.ic_error),
            contentDescription = null,
            tint = colors.onWarningContainer,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(Res.string.saving_stuck),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onWarningContainer,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun NewStreakFab(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        icon = { Icon(vectorResource(DsRes.drawable.ic_add), contentDescription = null) },
        text = { Text(stringResource(Res.string.new_streak), style = MaterialTheme.typography.labelLarge) },
    )
}

/** One T3 snackbar at a time; a failed save outranks an Undo offer. */
private sealed interface SnackbarRequest {
    val message: String
    val action: String

    data class Undo(override val message: String, override val action: String) : SnackbarRequest
    data class SaveFailed(override val message: String, override val action: String) : SnackbarRequest
}

/**
 * Shows the T3 snackbar the state asks for. The Undo offer stays until the ViewModel withdraws it
 * (it owns the 8 s / 10 s expiry); the save-failed one times out on its own and then reports
 * OnSaveFailedDismiss (R17). A state change cancels the effect, which takes the snackbar down.
 */
@Composable
private fun SnackbarEffect(
    ready: StreakListState.Ready?,
    host: SnackbarHostState,
    onAction: (StreakListAction) -> Unit,
) {
    val currentOnAction by rememberUpdatedState(onAction)
    val undo = ready?.undo
    val request: SnackbarRequest? = when {
        ready?.saveFailed == true -> SnackbarRequest.SaveFailed(
            stringResource(Res.string.snackbar_save_failed),
            stringResource(Res.string.try_again),
        )
        undo != null -> {
            val name = streakDisplayName(undo.name, undo.type)
            SnackbarRequest.Undo(
                stringResource(
                    if (undo.kind == UndoKind.RESET) Res.string.snackbar_reset else Res.string.snackbar_deleted,
                    name,
                ),
                stringResource(Res.string.snackbar_undo),
            )
        }
        else -> null
    }
    LaunchedEffect(request) {
        request ?: return@LaunchedEffect
        val result = host.showSnackbar(
            message = request.message,
            actionLabel = request.action,
            duration = if (request is SnackbarRequest.Undo) SnackbarDuration.Indefinite else SnackbarDuration.Long,
        )
        when (request) {
            is SnackbarRequest.Undo ->
                if (result == SnackbarResult.ActionPerformed) currentOnAction(StreakListAction.OnUndo)
            is SnackbarRequest.SaveFailed -> currentOnAction(
                if (result == SnackbarResult.ActionPerformed) {
                    StreakListAction.OnRetry
                } else {
                    StreakListAction.OnSaveFailedDismiss
                },
            )
        }
    }
}

@Composable
private fun PaddingValues.plus(top: Dp = 0.dp, bottom: Dp = 0.dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        end = calculateEndPadding(direction),
        top = calculateTopPadding() + top,
        bottom = calculateBottomPadding() + bottom,
    )
}
