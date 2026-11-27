package com.streakcounter.feature.streaks.presentation.celebrate

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

internal const val SMALL_BURST_MILLIS = 1_000
internal const val SMALL_BURST_TAG = "small_burst"

/**
 * The ~1 s sparkle around a card whose daily goal was just reached. Fill it into a Box the size of
 * the card: it draws a little outside that box and ignores touches. It fades out once played;
 * with [reducedMotion] it is the still last frame for as long as [visible] stays true.
 */
@Composable
fun SmallBurst(visible: Boolean, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    var finished by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (finished) 0f else 1f,
        animationSpec = tween(300),
        label = "burstFade",
    )
    LottieConfetti(
        file = "files/celebrate_small.json",
        durationMillis = SMALL_BURST_MILLIS,
        reducedMotion = reducedMotion,
        onFinished = { finished = true },
        modifier = modifier.fillMaxSize().bleed(horizontal = 16.dp, top = 16.dp, bottom = 16.dp).alpha(alpha),
    )
}
