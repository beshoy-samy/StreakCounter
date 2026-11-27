package com.streakcounter.feature.streaks.domain

/** Redraws every home-screen widget. Called after each successful command. */
interface WidgetUpdater {
    suspend fun updateAll()
}
