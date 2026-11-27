package com.streakcounter.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Roles from tokens.json that have no M3 slot. */
@Immutable
data class StreakColors(
    val confetti1: Color,
    val confetti2: Color,
    val confetti3: Color,
    val wallpaper: Color,
    val onWallpaper: Color,
    /** Unfilled part of the card outline progress (`outline-variant`). */
    val outlineTrack: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val focusRing: Color,
)

internal val LightStreakColors = LightPalette.run {
    StreakColors(
        confetti1, confetti2, confetti3, wallpaper, onWallpaper, outlineVariant,
        warningContainer, onWarningContainer, successContainer, onSuccessContainer,
        infoContainer, onInfoContainer, focusRing,
    )
}

internal val DarkStreakColors = DarkPalette.run {
    StreakColors(
        confetti1, confetti2, confetti3, wallpaper, onWallpaper, outlineVariant,
        warningContainer, onWarningContainer, successContainer, onSuccessContainer,
        infoContainer, onInfoContainer, focusRing,
    )
}

// M3 slots tokens.json does not define are derived from the nearest token role.
internal val LightColorScheme: ColorScheme = LightPalette.run {
    lightColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = primary, onSecondary = onPrimary,
        secondaryContainer = primaryContainer, onSecondaryContainer = onPrimaryContainer,
        tertiary = primary, onTertiary = onPrimary,
        tertiaryContainer = primaryContainer, onTertiaryContainer = onPrimaryContainer,
        error = error, onError = onError,
        errorContainer = errorContainer, onErrorContainer = onErrorContainer,
        background = background, onBackground = onBackground,
        surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceContainerHigh, onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = inverseSurface, inverseOnSurface = onInverseSurface, inversePrimary = inversePrimary,
        outline = outline, outlineVariant = outlineVariant, scrim = scrim,
        surfaceBright = surface, surfaceDim = background,
        surfaceContainerLowest = surface, surfaceContainerLow = surface,
        surfaceContainer = surfaceContainer, surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHigh,
    )
}

internal val DarkColorScheme: ColorScheme = DarkPalette.run {
    darkColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = primary, onSecondary = onPrimary,
        secondaryContainer = primaryContainer, onSecondaryContainer = onPrimaryContainer,
        tertiary = primary, onTertiary = onPrimary,
        tertiaryContainer = primaryContainer, onTertiaryContainer = onPrimaryContainer,
        error = error, onError = onError,
        errorContainer = errorContainer, onErrorContainer = onErrorContainer,
        background = background, onBackground = onBackground,
        surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceContainerHigh, onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = inverseSurface, inverseOnSurface = onInverseSurface, inversePrimary = inversePrimary,
        outline = outline, outlineVariant = outlineVariant, scrim = scrim,
        surfaceBright = surfaceContainerHigh, surfaceDim = background,
        surfaceContainerLowest = background, surfaceContainerLow = surface,
        surfaceContainer = surfaceContainer, surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHigh,
    )
}

val LocalStreakColors = staticCompositionLocalOf<StreakColors> {
    error("StreakTheme not provided")
}

object StreakTheme {
    val colors: StreakColors
        @Composable @ReadOnlyComposable get() = LocalStreakColors.current
}

@Composable
fun StreakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val typography = streakTypography(rememberReadexProFamily())
    CompositionLocalProvider(LocalStreakColors provides if (darkTheme) DarkStreakColors else LightStreakColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = typography,
            shapes = StreakShapes,
            content = content,
        )
    }
}
