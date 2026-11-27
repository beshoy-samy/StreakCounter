package com.streakcounter.core.data.time

import com.streakcounter.core.domain.Today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The phone's local date, read from [clock] in the zone given by [timeZone].
 *
 * It emits the current date immediately and again at every local midnight. [refresh] re-reads the
 * clock and zone right away and re-arms the midnight timer, so a time-zone or clock change moves
 * the day at once and the next tick is computed for the new zone.
 */
class SystemToday(
    private val clock: Clock,
    private val timeZone: () -> TimeZone,
    scope: CoroutineScope,
) : Today {
    private val state = MutableStateFlow(read())
    override val date: StateFlow<LocalDate> = state

    // Conflated: any number of refreshes before the scheduler wakes collapse into one re-arm.
    private val rearm = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            while (isActive) {
                // Wakes at the next local midnight, or earlier when refresh() asks to re-arm.
                withTimeoutOrNull(untilNextMidnight()) { rearm.receive() }
                state.value = read()
            }
        }
    }

    override fun refresh() {
        state.value = read()
        rearm.trySend(Unit)
    }

    private fun read(): LocalDate = clock.now().toLocalDateTime(timeZone()).date

    private fun untilNextMidnight(): Duration {
        val now = clock.now()
        val zone = timeZone()
        // Start of tomorrow in the zone, so a 23 or 25 hour DST day ends at the right instant.
        val nextMidnight = now.toLocalDateTime(zone).date
            .plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(zone)
        return (nextMidnight - now).coerceAtLeast(1.milliseconds)
    }
}
