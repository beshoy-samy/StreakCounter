package com.streakcounter.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import streakcounter.core.designsystem.generated.resources.Res
import streakcounter.core.designsystem.generated.resources.ic_add
import streakcounter.core.designsystem.generated.resources.ic_check
import streakcounter.core.designsystem.generated.resources.ic_remove
import com.streakcounter.core.designsystem.theme.ControlSize
import com.streakcounter.core.designsystem.theme.FullShape
import com.streakcounter.core.designsystem.theme.Spacing
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource

enum class PlusKind {
    /** Primary pill: plus icon + label (design: "+ Check in"). */
    CHECK_IN,

    /** Already done today: primary-container pill, check icon + label. */
    DONE,

    /** Primary pill: plus icon + label. */
    ADD,

    /** 48 dp icon-only plus with a 1 dp outline; [label] becomes the content description. */
    QUIET,
}

@Composable
fun PlusButton(
    kind: PlusKind,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container: Color
    val content: Color
    val icon: DrawableResource
    when (kind) {
        PlusKind.CHECK_IN -> { container = scheme.primary; content = scheme.onPrimary; icon = Res.drawable.ic_add }
        PlusKind.DONE -> {
            container = scheme.primaryContainer; content = scheme.onPrimaryContainer
            icon = Res.drawable.ic_check
        }
        PlusKind.ADD -> { container = scheme.primary; content = scheme.onPrimary; icon = Res.drawable.ic_add }
        PlusKind.QUIET -> { container = Color.Transparent; content = scheme.onSurface; icon = Res.drawable.ic_add }
    }
    if (kind == PlusKind.QUIET) {
        Row(
            modifier
                .size(ControlSize.touch)
                .clip(FullShape)
                .border(BorderStroke(1.dp, scheme.outline), FullShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(vectorResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        }
    } else {
        Row(
            modifier
                .height(ControlSize.control)
                .defaultMinSize(minWidth = ControlSize.touch)
                .clip(FullShape)
                .background(container)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = Spacing.s4, end = Spacing.s5),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(vectorResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        }
    }
}

/** 48 dp quiet minus: transparent, 1 dp outline, 12% outline / 38% icon when disabled. */
@Composable
fun MinusButton(
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val border = if (enabled) scheme.outline else scheme.onSurface.copy(alpha = 0.12f)
    val tint = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier
            .size(ControlSize.touch)
            .clip(FullShape)
            .border(BorderStroke(1.dp, border), FullShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(vectorResource(Res.drawable.ic_remove), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}
