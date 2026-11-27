package com.streakcounter.composeapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.streakcounter.core.domain.Today

/**
 * Refreshes [today] on clock, time-zone and date changes while the owner is started.
 * Attach with `lifecycle.addObserver(TimeChangeObserver(context, today))`. While stopped, the
 * ON_START refresh in `App()` catches up on anything missed.
 */
class TimeChangeObserver(
    context: Context,
    private val today: Today,
) : DefaultLifecycleObserver {
    private val appContext = context.applicationContext
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            today.refresh()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        // System broadcasts: no need to be exported to other apps.
        appContext.registerReceiver(receiver, filter)
        registered = true
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!registered) return
        appContext.unregisterReceiver(receiver)
        registered = false
    }
}
