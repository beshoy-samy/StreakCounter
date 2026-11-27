package com.streakcounter.core.designsystem

/**
 * Base class for `runComposeUiTest` tests. On Android host tests it carries the Robolectric runner,
 * on device the AndroidJUnit4 runner, on iOS it is plain.
 */
expect abstract class UiTestBase()
