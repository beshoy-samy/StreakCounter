package com.streakcounter.feature.streaks.presentation.celebrate

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streakcounter.core.designsystem.theme.ControlSize
import com.streakcounter.core.designsystem.theme.Spacing
import com.streakcounter.feature.streaks.domain.StreakType
import com.streakcounter.core.presentation.format.formatCount
import com.streakcounter.feature.streaks.presentation.list.CelebrationUi
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import streakcounter.feature.streaks.presentation.generated.resources.Res
import streakcounter.feature.streaks.presentation.generated.resources.celebrate_amount
import streakcounter.feature.streaks.presentation.generated.resources.celebrate_body
import streakcounter.feature.streaks.presentation.generated.resources.celebrate_heading
import streakcounter.feature.streaks.presentation.generated.resources.celebrate_keep_going
import streakcounter.feature.streaks.presentation.generated.resources.celebrate_set_new_goal
import streakcounter.feature.streaks.presentation.generated.resources.unit_days
import streakcounter.feature.streaks.presentation.generated.resources.unit_times

internal const val CELEBRATION_ANIMATION_TAG = "celebration_animation"
internal const val BIG_CELEBRATION_MILLIS = 2_500

/**
 * Big celebration (T-goal reached): a dimmed screen with a centred card holding the confetti
 * animation, the number, "{name} · {value} days" ("{value} days" when unnamed, "times" for Times),
 * and two actions. The animation plays once and rests on its last frame; [reducedMotion] shows
 * that frame straight away. The scrim swallows taps, so the only ways out are the two buttons.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CelebrationOverlay(
    ui: CelebrationUi,
    reducedMotion: Boolean,
    onKeepGoing: () -> Unit,
    onSetNewGoal: () -> Unit,
) {
    val value = formatCount(ui.value)
    val unit = if (ui.type == StreakType.TIMES) {
        pluralStringResource(Res.plurals.unit_times, ui.value)
    } else {
        pluralStringResource(Res.plurals.unit_days, ui.value)
    }
    val amount = stringResource(Res.string.celebrate_amount, value, unit)
    val heading = ui.name?.let { stringResource(Res.string.celebrate_heading, it, amount) } ?: amount

    // Back (and Escape on a keyboard) means "Keep going": the overlay can't be left half-open.
    BackHandler(enabled = true, onBack = onKeepGoing)
    val overlayFocus = remember { FocusRequester() }
    // Focus moves into the overlay so keyboards and switch access start here (and Escape reaches it),
    // not on the list behind.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { overlayFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(overlayFocus)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape) {
                    if (event.type == KeyEventType.KeyUp) onKeepGoing()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
            // Swallows taps without adding semantics (a clickable would merge the buttons into one node).
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(Spacing.s6),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.widthIn(max = 330.dp).fillMaxWidth()) {
            Surface(
                modifier = Modifier.fillMaxWidth().semantics {
                    paneTitle = heading
                    isTraversalGroup = true
                },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(start = Spacing.s6, end = Spacing.s6, top = Spacing.s8, bottom = Spacing.s6),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                ) {
                    Text(
                        text = value,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 88.sp,
                            lineHeight = 88.sp,
                            letterSpacing = (-3).sp,
                        ),
                    )
                    Text(
                        text = heading,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                    Text(
                        text = stringResource(Res.string.celebrate_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 260.dp),
                    )
                    Button(
                        onClick = onKeepGoing,
                        modifier = Modifier.heightIn(min = ControlSize.touch),
                    ) {
                        Text(stringResource(Res.string.celebrate_keep_going), style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(
                        onClick = onSetNewGoal,
                        modifier = Modifier.heightIn(min = ControlSize.touch),
                    ) {
                        Text(stringResource(Res.string.celebrate_set_new_goal), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            // Confetti reaches past the card's top and sides, like the design (inset -40 -20, 170 high).
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(170.dp).bleed(horizontal = 20.dp, top = 40.dp)) {
                LottieConfetti(
                    file = "files/celebrate_big.json",
                    durationMillis = BIG_CELEBRATION_MILLIS,
                    reducedMotion = reducedMotion,
                    playKey = "big:${ui.streakId}:${ui.value}",
                    modifier = Modifier.fillMaxSize().testTag(CELEBRATION_ANIMATION_TAG),
                )
            }
        }
    }
}
