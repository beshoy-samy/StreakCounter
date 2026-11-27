package com.streakcounter.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.streakcounter.core.domain.Result
import com.streakcounter.core.domain.Today
import com.streakcounter.feature.streaks.domain.StreakCommand
import com.streakcounter.feature.streaks.domain.StreakRepository
import com.streakcounter.feature.streaks.domain.StreakType
import com.streakcounter.feature.streaks.domain.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * A widget's + or − tap. Glance creates it by reflection, so it reads its dependencies from Koin
 * (started in Application.onCreate, before any widget code runs).
 */
class StreakWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val koin = GlobalContext.get()
        runWidgetCommand(
            repository = koin.get<StreakRepository>(),
            today = koin.get<Today>(),
            widgets = koin.get<WidgetUpdater>(),
            streakId = parameters[streakIdKey],
            op = parameters[opKey],
        )
    }

    companion object {
        val streakIdKey = ActionParameters.Key<String>("streakId")
        val opKey = ActionParameters.Key<String>("op")
        const val OP_INC = "inc"
        const val OP_DEC = "dec"
    }
}

private val COMMAND_TIMEOUT = 8.seconds

/**
 * Re-reads the file first and writes only when that read succeeded and the streak still exists and
 * has buttons. A tap on a widget that shows "Open StreakCounter" therefore never writes, even if
 * the drawn widget is stale. Redraws every widget afterwards, whatever happened.
 */
internal suspend fun runWidgetCommand(
    repository: StreakRepository,
    today: Today,
    widgets: WidgetUpdater,
    streakId: String?,
    op: String?,
) {
    val id = streakId?.let { Uuid.parseOrNull(it) } ?: return
    val command = when (op) {
        StreakWidgetAction.OP_INC -> StreakCommand.Increment(id)
        StreakWidgetAction.OP_DEC -> StreakCommand.Decrement(id)
        else -> return
    }
    // The process may have slept through midnight: count against the real date.
    today.refresh()
    try {
        withTimeoutOrNull(COMMAND_TIMEOUT) {
            val read = repository.streaks.first()
            if (read !is Result.Success) return@withTimeoutOrNull
            val streak = read.data.firstOrNull { it.id == id } ?: return@withTimeoutOrNull
            if (streak.type == StreakType.DAYS_SINCE) return@withTimeoutOrNull
            // A failed write leaves the file unchanged; the redraw shows what's saved.
            repository.execute(command)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Disk stays the truth. Never log streak content.
    }
    widgets.updateAll()
}
