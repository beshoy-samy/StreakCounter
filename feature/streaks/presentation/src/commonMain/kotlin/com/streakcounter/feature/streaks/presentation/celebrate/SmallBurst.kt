package com.streakcounter.feature.streaks.presentation.celebrate

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal const val SMALL_BURST_MILLIS = 1_000
internal const val SMALL_BURST_FADE_MILLIS = 300
internal const val SMALL_BURST_TAG = "small_burst"

/**
 * The ~1 s sparkle around a card whose daily goal was just reached. Fill it into a Box the size of
 * the card: it draws a little outside that box and ignores touches. It is on a fixed clock: after
 * about 1 s it fades out and is gone for good ([playKey] is the celebration's identity, so it does
 * not come back after a rotation), whether or not anything else is still waiting. With
 * [reducedMotion] it shows the still last frame for that same second.
 */
@Composable
fun SmallBurst(visible: Boolean, reducedMotion: Boolean, modifier: Modifier = Modifier, playKey: String = "") {
    var fading by rememberSaveable(playKey) { mutableStateOf(false) }
    var gone by rememberSaveable(playKey) { mutableStateOf(false) }
    LaunchedEffect(visible, playKey) {
        if (!visible || gone) return@LaunchedEffect
        delay(SMALL_BURST_MILLIS.toLong())
        fading = true
        delay(SMALL_BURST_FADE_MILLIS.toLong())
        gone = true
    }
    if (!visible || gone) return
    val alpha by animateFloatAsState(
        targetValue = if (fading) 0f else 1f,
        animationSpec = tween(SMALL_BURST_FADE_MILLIS),
        label = "burstFade",
    )
    LottieConfetti(
        file = "files/celebrate_small.json",
        durationMillis = SMALL_BURST_MILLIS,
        reducedMotion = reducedMotion,
        playKey = "small:$playKey",
        modifier = modifier.fillMaxSize().bleed(horizontal = 16.dp, top = 16.dp, bottom = 16.dp).alpha(alpha)
            .testTag(SMALL_BURST_TAG),
    )
}
