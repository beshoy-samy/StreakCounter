package com.streakcounter

import android.app.Application
import com.streakcounter.composeapp.initKoin
import org.koin.android.ext.koin.androidContext

class StreakCounterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@StreakCounterApplication)
        }
    }
}
