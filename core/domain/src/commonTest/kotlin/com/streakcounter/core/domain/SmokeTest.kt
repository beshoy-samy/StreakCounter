package com.streakcounter.core.domain

import androidx.appstate.statestore.StateStore
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import kotlin.test.Test

class SmokeTest {
    @Test
    fun smokeTest() {
        assertThat(1 + 1).isEqualTo(2)
    }

    @Test
    fun stateStoreResolvesAndLinksOnEveryTarget() {
        assertThat(StateStore()).isNotNull()
    }
}
