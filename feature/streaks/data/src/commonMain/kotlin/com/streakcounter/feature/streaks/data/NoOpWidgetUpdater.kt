package com.streakcounter.feature.streaks.data

import com.streakcounter.feature.streaks.domain.WidgetUpdater

/** iOS has no widgets; Android replaces this binding with its Glance updater. */
object NoOpWidgetUpdater : WidgetUpdater {
    override suspend fun updateAll() = Unit
}
