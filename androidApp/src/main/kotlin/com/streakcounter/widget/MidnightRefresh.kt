package com.streakcounter.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.streakcounter.core.domain.Result
import com.streakcounter.core.domain.Today
import com.streakcounter.feature.streaks.domain.StreakCommand
import com.streakcounter.feature.streaks.domain.StreakRepository
import com.streakcounter.feature.streaks.domain.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Redraws the widgets at local midnight (spec 6.5): an inexact RTC alarm (no exact-alarm
 * permission, no wake-up; a sleeping phone gets it when it wakes) at the next local midnight. It
 * normalizes the file and redraws, then re-arms. Boot, clock and time-zone changes re-arm it and
 * refresh too. Armed only while at least one widget is placed.
 */
internal object MidnightRefresh {
    const val ACTION_MIDNIGHT = "com.streakcounter.widget.MIDNIGHT"

    /** Arms the alarm when any widget is placed, cancels it otherwise. */
    fun sync(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context)
        if (!hasWidgets(context)) {
            alarms.cancel(pending)
            return
        }
        val trigger = nextLocalMidnight(Clock.System.now(), TimeZone.currentSystemDefault())
        // A second past midnight, so the new day is certainly "today" when it runs.
        alarms.set(AlarmManager.RTC, trigger.toEpochMilliseconds() + 1_000, pending)
    }

    private fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return listOf(SmallStreakWidgetReceiver::class.java, StreakListWidgetReceiver::class.java).any {
            manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, MidnightRefreshReceiver::class.java).setAction(ACTION_MIDNIGHT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Start of the day after [now]'s local date in [zone] (DST days are 23 or 25 hours long). */
internal fun nextLocalMidnight(now: Instant, zone: TimeZone): Instant =
    now.toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)

/**
 * Moves [today] to the real date, records what happened overnight (an ended check-in streak,
 * a reached goal) and redraws. Like the widget buttons, it writes only after a successful read.
 */
internal suspend fun runMidnightRefresh(repository: StreakRepository, today: Today, widgets: WidgetUpdater) {
    today.refresh()
    try {
        if (repository.streaks.first() is Result.Success) {
            repository.execute(StreakCommand.NormalizeAll)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // The redraw below shows whatever is saved. Never log streak content.
    }
    widgets.updateAll()
}

/** The midnight alarm, plus BOOT_COMPLETED, TIME_SET and TIMEZONE_CHANGED (all re-arm). */
class MidnightRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MidnightRefresh.ACTION_MIDNIGHT,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> Unit
            else -> return
        }
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            handleMidnightBroadcast(
                refresh = {
                    val deps = widgetDependencies(app)
                    runMidnightRefresh(deps.repository, deps.today, deps.widgets)
                },
                rearm = { MidnightRefresh.sync(app) },
                finish = pending::finish,
            )
        }
    }
}

/** Broadcasts get about 10 s: finish well before. */
private val BROADCAST_BUDGET = 8.seconds

/**
 * Runs [refresh] within the broadcast budget, then [rearm], then always [finish]. Nothing thrown
 * escapes: this runs in a background coroutine with no handler, where an exception would kill
 * the process while the user isn't even looking at the app.
 */
internal suspend fun handleMidnightBroadcast(refresh: suspend () -> Unit, rearm: () -> Unit, finish: () -> Unit) {
    try {
        withTimeoutOrNull(BROADCAST_BUDGET) { refresh() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // The widgets keep what they show; the next refresh catches up. Never log streak content.
    } finally {
        try {
            rearm()
        } catch (e: Exception) {
            // Boot or the next widget change arms it again.
        }
        finish()
    }
}
