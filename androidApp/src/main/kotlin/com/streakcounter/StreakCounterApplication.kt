package com.streakcounter

import android.app.Application
import com.streakcounter.widget.MidnightRefresh

class StreakCounterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startAppKoin(this)
        // Arms the midnight widget refresh if any widget is placed (cheap, idempotent).
        MidnightRefresh.sync(this)
    }
}
