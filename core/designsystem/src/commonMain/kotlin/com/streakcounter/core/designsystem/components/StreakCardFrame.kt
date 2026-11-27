package com.streakcounter.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.streakcounter.core.designsystem.theme.Radius
import com.streakcounter.core.designsystem.theme.Spacing
import com.streakcounter.core.designsystem.theme.StreakTheme

/**
 * Streak card: xl-radius `surfaceContainer` surface, 20 dp padding, whole card clickable
 * ([onClickLabel] is announced, e.g. "Edit Gym"). Buttons inside [content] sit above the card click
 * and handle their own taps. [progress] draws the outline goal ring (track + progress, 3 dp);
 * `null` draws none. [contentPadding] defaults to 20 dp all round; a caller can trim it to give a
 * corner button its full 48 dp target inside the card.
 */
@Composable
fun StreakCardFrame(
    progress: Float?,
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Spacing.s5),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick),
    ) {
        // The card is not a Surface, so provide the content color text and icons default to.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.padding(contentPadding), content = content)
        }
        // Drawn last so it overlays the surface; a Canvas takes no pointer input.
        OutlineProgress(
            progress = progress,
            cornerRadius = Radius.xl,
            trackColor = StreakTheme.colors.outlineTrack,
            progressColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.matchParentSize(),
        )
    }
}
