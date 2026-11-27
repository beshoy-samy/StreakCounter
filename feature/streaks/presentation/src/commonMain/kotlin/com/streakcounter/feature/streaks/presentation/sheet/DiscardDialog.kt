package com.streakcounter.feature.streaks.presentation.sheet

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import streakcounter.feature.streaks.presentation.generated.resources.Res
import streakcounter.feature.streaks.presentation.generated.resources.discard
import streakcounter.feature.streaks.presentation.generated.resources.discard_body
import streakcounter.feature.streaks.presentation.generated.resources.discard_title
import streakcounter.feature.streaks.presentation.generated.resources.keep_editing

/**
 * T6: closing a sheet with typed changes. The safe choice is the text button and the default
 * (back, tap outside); the destructive verb is on the filled error button.
 */
@Composable
internal fun DiscardDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(Res.string.discard_title), style = MaterialTheme.typography.headlineLarge) },
        text = { Text(stringResource(Res.string.discard_body), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Button(
                onClick = onDiscard,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(Res.string.discard), style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) {
                Text(stringResource(Res.string.keep_editing), style = MaterialTheme.typography.labelLarge)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    )
}
