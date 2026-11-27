package com.streakcounter.core.testing

import kotlinx.coroutines.test.TestScope
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * A settable [Clock]. With [elapsedMillis] wired to a test scheduler the clock moves together with
 * virtual time (`delay`, `advanceTimeBy`); [set] jumps it like a manual clock change.
 */
class TestClock(
    start: Instant,
    private val elapsedMillis: () -> Long = { 0L },
) : Clock {
    private var base: Instant = start - elapsedMillis().milliseconds

    override fun now(): Instant = base + elapsedMillis().milliseconds

    /** Makes [now] return [instant] from here on, then keep following virtual time. */
    fun set(instant: Instant) {
        base = instant - elapsedMillis().milliseconds
    }

    companion object {
        /** A clock that starts at [start] and advances with this scope's virtual time. */
        fun TestScope.testClock(start: Instant): TestClock =
            TestClock(start) { testScheduler.currentTime }
    }
}
