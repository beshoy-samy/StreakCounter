package com.streakcounter.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.color.ColorProvider
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.streakcounter.core.designsystem.theme.DarkPalette
import com.streakcounter.core.designsystem.theme.LightPalette
import com.streakcounter.core.designsystem.theme.Radius

/**
 * Widget colors: the app's Dusk Ember tokens as day/night pairs, so the launcher switches them with
 * the system theme (Android 12+ without a redraw; older versions on the next update).
 */
internal object WidgetColors {
    private fun pair(day: Color, night: Color): ColorProvider = ColorProvider(day = day, night = night)

    val surfaceContainer = pair(LightPalette.surfaceContainer, DarkPalette.surfaceContainer)
    val surface = pair(LightPalette.surface, DarkPalette.surface)
    val onSurface = pair(LightPalette.onSurface, DarkPalette.onSurface)
    val onSurfaceVariant = pair(LightPalette.onSurfaceVariant, DarkPalette.onSurfaceVariant)
    val outline = pair(LightPalette.outline, DarkPalette.outline)
    val outlineVariant = pair(LightPalette.outlineVariant, DarkPalette.outlineVariant)
    val primary = pair(LightPalette.primary, DarkPalette.primary)
    val onPrimary = pair(LightPalette.onPrimary, DarkPalette.onPrimary)
    val primaryContainer = pair(LightPalette.primaryContainer, DarkPalette.primaryContainer)
    val onPrimaryContainer = pair(LightPalette.onPrimaryContainer, DarkPalette.onPrimaryContainer)
}

/**
 * Type and sizes from tokens.json. RemoteViews can't load Readex Pro, so text uses the system font
 * at the token sizes and weights.
 */
internal object WidgetType {
    val name = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = WidgetColors.onSurfaceVariant)
    val caption = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = WidgetColors.onSurfaceVariant)
    val label = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = WidgetColors.onSurface)
    val numberSmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = WidgetColors.primary)
    val numberLarge = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold, color = WidgetColors.primary)
    val rowName = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = WidgetColors.onSurface)
    val rowNumber = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WidgetColors.primary)
    val emptyTitle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = WidgetColors.onSurface)
}

internal object WidgetDimens {
    val widgetRadius = Radius.xl
    val rowRadius = Radius.md
    val touch = 48.dp
    val icon = 24.dp
    val badge = 24.dp
    val outlineStroke = 3.dp
}
