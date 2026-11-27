package com.streakcounter.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.streakcounter.feature.streaks.domain.WidgetUpdater
import kotlinx.coroutines.CancellationException

/**
 * Redraws both widget types. Never throws (cancellation aside): a failing redraw must not break
 * the command that was already saved; widgets catch up on their next update.
 */
class GlanceWidgetUpdater internal constructor(
    private val updates: List<suspend () -> Unit>,
) : WidgetUpdater {

    constructor(context: Context) : this(
        listOf(
            { SmallStreakWidget().updateAll(context) },
            { StreakListWidget().updateAll(context) },
        ),
    )

    override suspend fun updateAll() {
        for (update in updates) {
            try {
                update()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing to show; never log streak content.
            }
        }
    }
}
