package com.streakcounter.core.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.streakcounter.core.designsystem.components.MinusButton
import com.streakcounter.core.designsystem.components.PlusButton
import com.streakcounter.core.designsystem.components.PlusKind
import com.streakcounter.core.designsystem.components.StreakCardFrame
import com.streakcounter.core.designsystem.theme.StreakTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
private fun PreviewCard(progress: Float?, kind: PlusKind) {
    StreakCardFrame(progress = progress, onClick = {}, onClickLabel = "Edit Gym", modifier = Modifier.fillMaxWidth()) {
        Text("Gym", style = MaterialTheme.typography.titleMedium)
        Text(
            "Best 21 days",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("12", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MinusButton(enabled = true, contentDescription = "Remove one", onClick = {})
                PlusButton(kind = kind, label = if (kind == PlusKind.DONE) "Done" else "Check in", onClick = {})
            }
        }
    }
}

@Composable
private fun PreviewSheet(dark: Boolean) {
    StreakTheme(darkTheme = dark) {
        Column(
            Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PreviewCard(progress = 0.6f, kind = PlusKind.CHECK_IN)
            PreviewCard(progress = 1f, kind = PlusKind.DONE)
            PreviewCard(progress = null, kind = PlusKind.CHECK_IN)
        }
    }
}

@Preview(widthDp = 380, heightDp = 640, name = "Streak card light")
@Composable
fun StreakCardLightPreview() = PreviewSheet(dark = false)

@Preview(widthDp = 380, heightDp = 640, name = "Streak card dark")
@Composable
fun StreakCardDarkPreview() = PreviewSheet(dark = true)
