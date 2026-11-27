package com.streakcounter.feature.streaks.presentation.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streakcounter.core.designsystem.theme.ControlSize
import com.streakcounter.core.designsystem.theme.Spacing
import com.streakcounter.core.presentation.format.formatCount
import com.streakcounter.feature.streaks.domain.Streak
import com.streakcounter.feature.streaks.presentation.list.StreakCardUi
import com.streakcounter.feature.streaks.presentation.list.components.streakDisplayName
import com.streakcounter.feature.streaks.presentation.list.components.subtitle
import com.streakcounter.feature.streaks.presentation.list.toCardUi
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import streakcounter.core.designsystem.generated.resources.ic_close
import streakcounter.feature.streaks.presentation.generated.resources.Res
import streakcounter.feature.streaks.presentation.generated.resources.new_streak
import streakcounter.feature.streaks.presentation.generated.resources.picker_add_widget
import streakcounter.feature.streaks.presentation.generated.resources.picker_cancel
import streakcounter.feature.streaks.presentation.generated.resources.picker_empty_body
import streakcounter.feature.streaks.presentation.generated.resources.picker_empty_title
import streakcounter.feature.streaks.presentation.generated.resources.picker_title
import streakcounter.feature.streaks.presentation.generated.resources.unreadable_title
import streakcounter.core.designsystem.generated.resources.Res as DsRes
import kotlin.uuid.Uuid

/** What the Android widget setup screen shows. */
sealed interface WidgetPickerState {
    data object Opening : WidgetPickerState

    /** The file can't be read: nothing to pick; the app explains and offers the fixes. */
    data object Unreadable : WidgetPickerState

    data class Ready(val streaks: List<Streak>, val today: LocalDate) : WidgetPickerState
}

/**
 * Widget setup (design `widgets/setup` (pick, none)): pick which streak a small widget shows, then "Add
 * widget". No streaks: "Create a streak first" with "New streak", which opens the app.
 */
@Composable
fun WidgetPickerScreen(
    state: WidgetPickerState,
    selectedId: Uuid?,
    onSelect: (Uuid) -> Unit,
    onAddWidget: () -> Unit,
    onCancel: () -> Unit,
    onNewStreak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.s1, vertical = Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancel) {
                    Icon(vectorResource(DsRes.drawable.ic_close), contentDescription = stringResource(Res.string.picker_cancel))
                }
                Text(
                    stringResource(Res.string.picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            when (state) {
                WidgetPickerState.Opening -> Unit
                WidgetPickerState.Unreadable -> Message(title = stringResource(Res.string.unreadable_title), body = null)
                is WidgetPickerState.Ready ->
                    if (state.streaks.isEmpty()) {
                        Message(
                            title = stringResource(Res.string.picker_empty_title),
                            body = stringResource(Res.string.picker_empty_body),
                            action = stringResource(Res.string.new_streak),
                            onAction = onNewStreak,
                        )
                    } else {
                        Picker(state, selectedId, onSelect, onAddWidget)
                    }
            }
        }
    }
}

@Composable
private fun Picker(
    state: WidgetPickerState.Ready,
    selectedId: Uuid?,
    onSelect: (Uuid) -> Unit,
    onAddWidget: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().selectableGroup(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.s4, vertical = Spacing.s2),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            items(state.streaks, key = { it.id.toString() }) { streak ->
                PickerRow(streak.toCardUi(state.today), state.today, streak.id == selectedId) { onSelect(streak.id) }
            }
        }
        Button(
            onClick = onAddWidget,
            enabled = selectedId != null && state.streaks.any { it.id == selectedId },
            modifier = Modifier.fillMaxWidth().padding(Spacing.s4).heightIn(min = ControlSize.control),
        ) {
            Text(stringResource(Res.string.picker_add_widget), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PickerRow(card: StreakCardUi, today: LocalDate, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.s2, vertical = Spacing.s2).heightIn(min = ControlSize.control),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The row is the radio (selectable above); the dot is drawn only.
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = Spacing.s2))
            Column(Modifier.weight(1f)) {
                Text(
                    streakDisplayName(card.name, card.type),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    card.subtitle(today),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatCount(card.value),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.s3),
            )
        }
    }
}

@Composable
private fun Message(title: String, body: String?, action: String? = null, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(Spacing.s6), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 360.dp),
                )
            }
            if (action != null) {
                Button(onClick = onAction, modifier = Modifier.heightIn(min = ControlSize.touch)) {
                    Text(action, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
