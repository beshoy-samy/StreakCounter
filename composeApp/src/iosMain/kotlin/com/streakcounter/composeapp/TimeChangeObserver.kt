package com.streakcounter.composeapp

import com.streakcounter.core.domain.Today
import platform.Foundation.NSCalendarDayChangedNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSystemTimeZoneDidChangeNotification

/**
 * Refreshes [today] when the calendar day or the system time zone changes. iOS does not deliver
 * these while suspended; the ON_START refresh in `App()` catches up on resume.
 */
class TimeChangeObserver(private val today: Today) {
    private val tokens = mutableListOf<Any>()

    fun start() {
        if (tokens.isNotEmpty()) return
        val center = NSNotificationCenter.defaultCenter
        listOf(NSCalendarDayChangedNotification, NSSystemTimeZoneDidChangeNotification).forEach { name ->
            tokens += center.addObserverForName(name, null, NSOperationQueue.mainQueue) { today.refresh() }
        }
    }

    fun stop() {
        tokens.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        tokens.clear()
    }
}
