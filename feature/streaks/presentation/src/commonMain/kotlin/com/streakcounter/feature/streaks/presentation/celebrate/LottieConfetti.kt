package com.streakcounter.feature.streaks.presentation.celebrate

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.streakcounter.core.designsystem.theme.StreakTheme
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import io.github.alexzhirkevich.compottie.dynamic.rememberLottieDynamicProperties
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.ExperimentalResourceApi
import streakcounter.feature.streaks.presentation.generated.resources.Res

/** Playback position (0..1) of a celebration animation, readable by tests. */
internal val CelebrationProgress = SemanticsPropertyKey<Float>("CelebrationProgress")
private var SemanticsPropertyReceiver.celebrationProgress by CelebrationProgress

/**
 * One of the bundled Lottie files, drawn once (linear, [durationMillis]) and then left on its last
 * frame. With [reducedMotion], or when the file can't be read, it is on the last frame from the
 * first composition and never plays. Layers named c1, c2, c3 and p take the theme's confetti1..3
 * and primary colors.
 *
 * The playback position is saved per [playKey] (the celebration's identity), so a rotation
 * continues where it was and a finished animation is never replayed.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun LottieConfetti(
    file: String,
    durationMillis: Int,
    reducedMotion: Boolean,
    playKey: String,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
) {
    // null = still loading; Result.failure = missing or unreadable file (show no animation).
    val json by produceState<Result<String>?>(null, file) {
        value = try {
            Result.success(Res.readBytes(file).decodeToString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    val loaded = json?.getOrNull()
    val failed = json?.isFailure == true

    var saved by rememberSaveable(playKey) { mutableStateOf(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    val still = reducedMotion || failed
    LaunchedEffect(playKey, loaded, still) {
        if (still || loaded == null || saved >= 1f) return@LaunchedEffect
        val remaining = (durationMillis * (1f - saved)).toInt().coerceAtLeast(1)
        animate(saved, 1f, animationSpec = tween(remaining, easing = LinearEasing)) { value, _ -> saved = value }
        saved = 1f
        currentOnFinished()
    }
    val progress = if (still) 1f else saved

    Box(modifier.semantics { celebrationProgress = progress }) {
        if (loaded != null) LottieImage(loaded, progress, Modifier.matchParentSize())
    }
}

@Composable
private fun LottieImage(json: String, progress: Float, modifier: Modifier) {
    val composition by rememberLottieComposition(json) { LottieCompositionSpec.JsonString(json) }
    val colors = StreakTheme.colors
    val primary = MaterialTheme.colorScheme.primary
    val tint = rememberLottieDynamicProperties {
        shapeLayer("c1") { fill("**") { color { colors.confetti1 } } }
        shapeLayer("c2") { fill("**") { color { colors.confetti2 } } }
        shapeLayer("c3") { fill("**") { color { colors.confetti3 } } }
        shapeLayer("p") { fill("**") { color { primary } } }
    }
    Image(
        painter = rememberLottiePainter(
            composition = composition,
            progress = { progress },
            dynamicProperties = tint,
        ),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier,
    )
}

/** Lays the child out [horizontal] / [top] / [bottom] larger than the space given, centred on it, without growing the parent. */
internal fun Modifier.bleed(horizontal: Dp = 0.dp, top: Dp = 0.dp, bottom: Dp = 0.dp): Modifier =
    layout { measurable, constraints ->
        val h = horizontal.roundToPx()
        val t = top.roundToPx()
        val b = bottom.roundToPx()
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val placeable = measurable.measure(Constraints.fixed(width + 2 * h, height + t + b))
        layout(width, height) { placeable.place(-h, -t) }
    }
