package com.streakcounter.feature.streaks.presentation.list.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.streakcounter.core.designsystem.theme.ControlSize
import com.streakcounter.core.designsystem.theme.Spacing
import com.streakcounter.core.domain.DataError
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import streakcounter.core.designsystem.generated.resources.ic_lock
import streakcounter.feature.streaks.presentation.generated.resources.Res
import streakcounter.feature.streaks.presentation.generated.resources.delete_and_start_fresh
import streakcounter.feature.streaks.presentation.generated.resources.keep_file
import streakcounter.feature.streaks.presentation.generated.resources.start_fresh
import streakcounter.feature.streaks.presentation.generated.resources.start_fresh_body
import streakcounter.feature.streaks.presentation.generated.resources.start_fresh_body_key
import streakcounter.feature.streaks.presentation.generated.resources.start_fresh_title
import streakcounter.feature.streaks.presentation.generated.resources.try_again
import streakcounter.feature.streaks.presentation.generated.resources.unreadable_body
import streakcounter.feature.streaks.presentation.generated.resources.unreadable_body_locked
import streakcounter.feature.streaks.presentation.generated.resources.unreadable_title
import streakcounter.core.designsystem.generated.resources.Res as DsRes

/**
 * T5 "Couldn't open your streaks". "Try again" always; "Start fresh" only when [canStartFresh],
 * and it deletes nothing until the T6 dialog is confirmed.
 */
@Composable
internal fun UnreadableState(
    error: DataError.Local,
    canStartFresh: Boolean,
    onRetry: () -> Unit,
    onStartFreshConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier.padding(horizontal = Spacing.s6, vertical = Spacing.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s3, Alignment.CenterVertically),
    ) {
        Icon(vectorResource(DsRes.drawable.ic_lock), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Text(
            text = stringResource(Res.string.unreadable_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            // Only UNREADABLE means the file is gone for good; a missing key or an unknown error
            // leaves the streaks on the phone, so that copy must not nudge towards deleting them.
            text = stringResource(
                if (error == DataError.Local.UNREADABLE) Res.string.unreadable_body else Res.string.unreadable_body_locked,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        Button(onClick = onRetry, modifier = Modifier.heightIn(min = ControlSize.touch)) {
            Text(stringResource(Res.string.try_again), style = MaterialTheme.typography.labelLarge)
        }
        if (canStartFresh) {
            TextButton(onClick = { confirming = true }, modifier = Modifier.heightIn(min = ControlSize.touch)) {
                Text(stringResource(Res.string.start_fresh), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (confirming && canStartFresh) {
        StartFreshDialog(
            keyUnavailable = error == DataError.Local.KEY_UNAVAILABLE,
            onConfirm = {
                confirming = false
                onStartFreshConfirm()
            },
            onDismiss = { confirming = false },
        )
    }
}

/** T6: irreversible. The verb is on the destructive button, the consequence in the body. */
@Composable
private fun StartFreshDialog(keyUnavailable: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.start_fresh_title), style = MaterialTheme.typography.headlineLarge) },
        text = {
            Text(
                stringResource(if (keyUnavailable) Res.string.start_fresh_body_key else Res.string.start_fresh_body),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(Res.string.delete_and_start_fresh), style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.keep_file), style = MaterialTheme.typography.labelLarge)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    )
}
