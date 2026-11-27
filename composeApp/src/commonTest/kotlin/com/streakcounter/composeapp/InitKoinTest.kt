package com.streakcounter.composeapp

import org.koin.core.context.stopKoin
import kotlin.test.Test

class InitKoinTest {
    @Test
    fun calling_initKoin_twice_does_not_throw() {
        try {
            initKoin()
            initKoin()
        } finally {
            stopKoin()
        }
    }
}
