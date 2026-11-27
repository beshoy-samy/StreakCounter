package com.streakcounter.feature.streaks.presentation.celebrate

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
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
import org.jetbrains.compose.resources.ExperimentalResourceApi
import streakcounter.feature.streaks.presentation.generated.resources.Res

/** Playback position (0..1) of a celebration animation, readable by tests. */
internal val CelebrationProgress = SemanticsPropertyKey<Float>("CelebrationProgress")
private var SemanticsPropertyReceiver.celebrationProgress by CelebrationProgress

/**
 * One of the bundled Lottie files, drawn once (linear, [durationMillis]) and then left on its last
 * frame. With [reducedMotion] it is on the last frame from the first composition and never plays.
 * Layers named c1, c2, c3 and p take the theme's confetti1..3 and primary colors.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun LottieConfetti(
    file: String,
    durationMillis: Int,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
) {
    val composition by rememberLottieComposition(file) {
        LottieCompositionSpec.JsonString(Res.readBytes(file).decodeToString())
    }
    val colors = StreakTheme.colors
    val primary = MaterialTheme.colorScheme.primary
    val tint = rememberLottieDynamicProperties {
        shapeLayer("c1") { fill("**") { color { colors.confetti1 } } }
        shapeLayer("c2") { fill("**") { color { colors.confetti2 } } }
        shapeLayer("c3") { fill("**") { color { colors.confetti3 } } }
        shapeLayer("p") { fill("**") { color { primary } } }
    }

    val progress = remember(file, reducedMotion) { Animatable(if (reducedMotion) 1f else 0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(progress, composition) {
        if (reducedMotion || composition == null) return@LaunchedEffect
        progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
        currentOnFinished()
    }

    Image(
        painter = rememberLottiePainter(
            composition = composition,
            progress = { progress.value },
            dynamicProperties = tint,
        ),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier.semantics { celebrationProgress = progress.value },
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
