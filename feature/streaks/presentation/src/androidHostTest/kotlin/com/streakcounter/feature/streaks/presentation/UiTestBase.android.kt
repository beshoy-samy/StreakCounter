package com.streakcounter.feature.streaks.presentation

import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
actual abstract class UiTestBase {
    // Robolectric does not start the library's resource ContentProvider, so hand over the context.
    @OptIn(ExperimentalResourceApi::class)
    @Before
    fun setUpResourceContext() {
        setResourceReaderAndroidContext(RuntimeEnvironment.getApplication())
    }
}
