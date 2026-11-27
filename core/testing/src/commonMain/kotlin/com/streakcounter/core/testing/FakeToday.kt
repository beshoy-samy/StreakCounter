package com.streakcounter.core.testing

import com.streakcounter.core.domain.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** A [Today] the test moves by hand. */
class FakeToday(initial: LocalDate) : Today {
    private val state = MutableStateFlow(initial)
    override val date: StateFlow<LocalDate> = state

    var refreshCalls: Int = 0
        private set

    /** The current day; setting it emits like a midnight tick or a clock change. */
    var current: LocalDate
        get() = state.value
        set(value) {
            state.value = value
        }

    fun advanceDays(days: Int = 1) {
        current = current.plus(days, DateTimeUnit.DAY)
    }

    override fun refresh() {
        refreshCalls++
    }
}
