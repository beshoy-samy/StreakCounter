package com.streakcounter.feature.streaks.presentation.celebrate

import androidx.compose.runtime.Composable

/**
 * True when the person asked the phone for less motion (Android: animations turned off or
 * "Remove animations"; iOS: Reduce Motion). Celebrations then show their last frame and never play.
 */
@Composable
internal expect fun rememberReducedMotion(): Boolean
