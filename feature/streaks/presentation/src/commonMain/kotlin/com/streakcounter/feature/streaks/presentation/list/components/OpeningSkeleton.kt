package com.streakcounter.feature.streaks.presentation.list.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.streakcounter.core.designsystem.theme.FullShape
import com.streakcounter.core.designsystem.theme.Spacing
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import streakcounter.feature.streaks.presentation.generated.resources.Res
import streakcounter.feature.streaks.presentation.generated.resources.opening
import kotlin.time.Duration.Companion.milliseconds

internal const val OPENING_SKELETON_TAG = "opening_skeleton"

/** Most reads finish well under this; only a slow decrypt shows the skeleton. */
internal val SKELETON_DELAY = 300.milliseconds

/** Card-shaped placeholders, shown only after [SKELETON_DELAY] so a fast read never flashes. */
@Composable
internal fun OpeningSkeleton(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SKELETON_DELAY)
        visible = true
    }
    if (!visible) return
    val description = stringResource(Res.string.opening)
    Column(
        modifier = modifier
            .testTag(OPENING_SKELETON_TAG)
            .semantics { contentDescription = description }
            .padding(horizontal = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        repeat(4) { SkeletonCard() }
    }
}

@Composable
private fun SkeletonCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Spacing.s5),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Bone(Modifier.fillMaxWidth(0.4f).height(12.dp))
        Bone(Modifier.fillMaxWidth(0.25f).height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Bone(Modifier.size(width = 64.dp, height = 48.dp))
            Bone(Modifier.width(120.dp).height(48.dp), FullShape)
        }
    }
}

@Composable
private fun Bone(modifier: Modifier, shape: Shape = MaterialTheme.shapes.small) {
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
}
