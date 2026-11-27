package com.streakcounter.feature.streaks.presentation.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streakcounter.core.domain.Today
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Wires the list screen to its ViewModel; the header date follows [Today] across midnight. */
@Composable
fun StreakListRoot(
    viewModel: StreakListViewModel = koinViewModel(),
    today: Today = koinInject(),
) {
    val state by viewModel.state
    val date by today.date.collectAsStateWithLifecycle()
    StreakListScreen(state = state, onAction = viewModel::onAction, today = date)
}
