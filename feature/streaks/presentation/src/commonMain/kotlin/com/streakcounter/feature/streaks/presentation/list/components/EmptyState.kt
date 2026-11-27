package com.streakcounter.feature.streaks.presentation.list.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streakcounter.core.designsystem.components.OutlineProgress
import com.streakcounter.core.designsystem.theme.ControlSize
import com.streakcounter.core.designsystem.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import streakcounter.feature.streaks.presentation.generated.resources.Res
import streakcounter.feature.streaks.presentation.generated.resources.empty_body
import streakcounter.feature.streaks.presentation.generated.resources.empty_title
import streakcounter.feature.streaks.presentation.generated.resources.new_streak

/** First use: a small card-shaped "0" with a partly drawn outline, what a streak can be, and one action. */
@Composable
internal fun EmptyState(onNewClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.s6, vertical = Spacing.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s3, Alignment.CenterVertically),
    ) {
        EmptyArt()
        Text(
            text = stringResource(Res.string.empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        Button(onClick = onNewClick, modifier = Modifier.heightIn(min = ControlSize.touch)) {
            Text(stringResource(Res.string.new_streak), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun EmptyArt() {
    val radius = 26.dp
    Box(Modifier.padding(vertical = 22.dp).size(width = 116.dp, height = 76.dp)) {
        Box(
            Modifier.matchParentSize().clip(RoundedCornerShape(radius))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
        OutlineProgress(
            progress = 0.62f,
            cornerRadius = radius,
            trackColor = Color.Transparent,
            progressColor = MaterialTheme.colorScheme.primary,
            strokeWidth = 6.dp,
            modifier = Modifier.matchParentSize(),
        )
        Text(
            text = "0",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 26.dp),
        )
    }
}
