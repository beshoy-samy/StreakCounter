package com.streakcounter.core.testing

import com.streakcounter.feature.streaks.domain.WidgetUpdater

class FakeWidgetUpdater : WidgetUpdater {
    var updateAllCalls: Int = 0
        private set

    override suspend fun updateAll() {
        updateAllCalls++
    }
}
