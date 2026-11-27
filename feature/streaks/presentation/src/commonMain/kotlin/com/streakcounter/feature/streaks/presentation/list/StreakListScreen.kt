package com.streakcounter.feature.streaks.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.streakcounter.feature.streaks.presentation.list.components.reorderItem
import com.streakcounter.feature.streaks.presentation.list.components.rememberReorderState
import com.streakcounter.feature.streaks.presentation.list.components.ReorderRow
import streakcounter.feature.streaks.presentation.generated.resources.summary_day
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
import com.streakcounter.feature.streaks.domain.CelebrationSize
import com.streakcounter.feature.streaks.domain.StreakType
import com.streakcounter.feature.streaks.presentation.celebrate.CelebrationOverlay
import com.streakcounter.feature.streaks.presentation.celebrate.SmallBurst
import com.streakcounter.feature.streaks.presentation.celebrate.rememberReducedMotion
import com.streakcounter.feature.streaks.presentation.list.components.unnamedName
import kotlin.uuid.Uuid
import com.streakcounter.feature.streaks.presentation.list.components.EmptyState
import com.streakcounter.feature.streaks.presentation.list.components.OpeningSkeleton
import com.streakcounter.feature.streaks.presentation.list.components.ReorderTopBar
import com.streakcounter.feature.streaks.presentation.list.components.StreakCard
import com.streakcounter.feature.streaks.presentation.list.components.SwipeToDelete
import com.streakcounter.feature.streaks.presentation.list.components.UnreadableState
import com.streakcounter.feature.streaks.presentation.list.components.streakDisplayName
import com.streakcounter.feature.streaks.presentation.session.UndoKind
import com.streakcounter.feature.streaks.presentation.sheet.StreakSheet
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
import streakcounter.feature.streaks.presentation.generated.resources.snackbar_daily_goal
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
    val reducedMotion = rememberReducedMotion()
    val burstId = ready?.celebration?.takeIf { it.size == CelebrationSize.SMALL }?.streakId

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { if (reorderMode) ReorderTopBar(onDone = { onAction(StreakListAction.OnReorderDone) }) },
            snackbarHost = { SnackbarHost(snackbarHost) },
            floatingActionButton = {
                if (showFab) NewStreakFab(onClick = { onAction(StreakListAction.OnNewClick) })
            },
        ) { padding ->
            when (state) {
                StreakListState.Opening -> ScrollableState(padding, today, centered = false) {
                    Spacer(Modifier.height(Spacing.s4))
                    OpeningSkeleton()
                }
                is StreakListState.Unreadable -> ScrollableState(padding, today) {
                    UnreadableState(
                        error = state.error,
                        canStartFresh = state.canStartFresh,
                        onRetry = { onAction(StreakListAction.OnRetryOpen) },
                        onStartFreshConfirm = { onAction(StreakListAction.OnStartFreshConfirm) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is StreakListState.Ready -> if (state.cards.isEmpty() && !state.reorderMode) {
                    ScrollableState(padding, today) {
                        EmptyState(
                            onNewClick = { onAction(StreakListAction.OnNewClick) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    StreakList(state, today, onAction, padding, burstId, reducedMotion)
                }
            }
        }

        SnackbarEffect(ready, snackbarHost, onAction)
        DailyGoalEffect(ready, snackbarHost, onAction)

        ready?.sheet?.let { StreakSheet(it, onAction) }

        // The big celebration covers the list; the small one is a burst on the card plus a snackbar.
        ready?.celebration?.takeIf { it.size == CelebrationSize.BIG }?.let {
            CelebrationOverlay(
                ui = it,
                reducedMotion = reducedMotion,
                onKeepGoing = { onAction(StreakListAction.OnCelebrationDismiss) },
                onSetNewGoal = { onAction(StreakListAction.OnSetNewGoal) },
            )
        }
    }
}

/**
 * Header plus one whole-screen state. At least screen-tall, with [content] centred in the space
 * under the header when [centered]; scrolls once large text or a small window makes it taller, so
 * Try again / Start fresh / New streak can always be reached.
 */
@Composable
private fun ScrollableState(
    padding: PaddingValues,
    today: LocalDate?,
    centered: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
        ) {
            ListHeader(today = today)
            // Weighted spacers share whatever height is left; with too little they are 0 and
            // the column grows past the screen instead of squeezing the content.
            if (centered) Spacer(Modifier.weight(1f))
            content()
            if (centered) Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * The streak list in both modes. One lazy list (and one [ReorderState]) serves normal and reorder
 * mode, so a long-press on a card can lift it and keep dragging while the cards turn into compact
 * rows (design: "Long-press on a card goes straight here with that card lifted").
 */
@Composable
private fun StreakList(
    state: StreakListState.Ready,
    today: LocalDate?,
    onAction: (StreakListAction) -> Unit,
    padding: PaddingValues,
    burstId: Uuid?,
    reducedMotion: Boolean,
) {
    val currentOnAction by rememberUpdatedState(onAction)
    val reorderMode by rememberUpdatedState(state.reorderMode)
    val reorder = rememberReorderState(state.cards) { from, to -> currentOnAction(StreakListAction.OnMove(from, to)) }
    val onLift = remember { { if (!reorderMode) currentOnAction(StreakListAction.OnReorderStart) } }
    val cards = if (state.reorderMode) reorder.order else state.cards

    LazyColumn(
        state = reorder.listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = if (state.reorderMode) {
            padding.plus(top = Spacing.s3, bottom = Spacing.s6)
        } else {
            // The list ends with room for the pinned FAB plus 48 dp, so the last card clears it.
            padding.plus(bottom = ControlSize.control + Spacing.s4 + Spacing.s12)
        },
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        if (!state.reorderMode) {
            item(key = "header") {
                Column {
                    ListHeader(today = today, onReorder = { onAction(StreakListAction.OnReorderStart) })
                    summaryText(state.summary)?.let { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.s5),
                        )
                    }
                    if (state.savingStuck) {
                        SavingStuckBanner(Modifier.padding(start = Spacing.s4, end = Spacing.s4, top = Spacing.s3))
                    }
                    Spacer(Modifier.height(Spacing.s1))
                }
            }
        }
        itemsIndexed(cards, key = { _, card -> card.id }) { index, card ->
            val dragging = reorder.draggingId == card.id
            Box(
                Modifier
                    .padding(horizontal = Spacing.s4)
                    .then(if (dragging) Modifier else Modifier.animateItem())
                    .reorderItem(reorder, card.id, onLift),
            ) {
                if (state.reorderMode) {
                    ReorderRow(card = card, today = today, index = index, count = cards.size, reorder = reorder)
                } else {
                    SwipeToDelete(
                        onDelete = { onAction(StreakListAction.OnDelete(card.id)) },
                        failedWrites = state.failedWrites,
                    ) {
                        StreakCard(card = card, today = today, index = index, count = cards.size, onAction = onAction)
                    }
                    SmallBurst(
                        visible = card.id == burstId,
                        reducedMotion = reducedMotion,
                        modifier = Modifier.matchParentSize(),
                    )
                }
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

/** The words for the summary the ViewModel picked. */
@Composable
private fun summaryText(summary: ListSummary): String? = when (summary) {
    ListSummary.None -> null
    is ListSummary.Day -> stringResource(Res.string.summary_day, formatNumber(summary.day))
    ListSummary.WelcomeBack -> stringResource(Res.string.summary_welcome_back)
    ListSummary.AllCheckedIn -> stringResource(Res.string.summary_all_done)
    is ListSummary.CheckInsLeft ->
        pluralStringResource(Res.plurals.summary_check_ins_left, summary.count, formatNumber(summary.count))
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

/**
 * The snackbar for a SMALL celebration ("Water: 8 today. That’s your goal."). It queues behind any
 * other snackbar; once it closes the celebration is reported as seen (OnCelebrationDismiss), which
 * is also what ends the burst.
 */
@Composable
private fun DailyGoalEffect(
    ready: StreakListState.Ready?,
    host: SnackbarHostState,
    onAction: (StreakListAction) -> Unit,
) {
    val currentOnAction by rememberUpdatedState(onAction)
    val hit = ready?.dailyGoalHit
    val message = hit?.let {
        stringResource(
            Res.string.snackbar_daily_goal,
            it.name ?: stringResource(unnamedName(ready.cards.firstOrNull { c -> c.id == it.streakId }?.type ?: StreakType.TIMES)),
            formatNumber(it.value),
        )
    }
    LaunchedEffect(hit?.streakId, hit?.value) {
        message ?: return@LaunchedEffect
        host.showSnackbar(message = message, duration = SnackbarDuration.Short)
        currentOnAction(StreakListAction.OnCelebrationDismiss)
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
