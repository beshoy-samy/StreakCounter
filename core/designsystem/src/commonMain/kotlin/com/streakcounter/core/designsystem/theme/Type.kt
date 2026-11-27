package com.streakcounter.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import streakcounter.core.designsystem.generated.resources.Res
import streakcounter.core.designsystem.generated.resources.readex_pro_bold
import streakcounter.core.designsystem.generated.resources.readex_pro_medium
import streakcounter.core.designsystem.generated.resources.readex_pro_regular
import streakcounter.core.designsystem.generated.resources.readex_pro_semibold

/** Readex Pro (Latin + Arabic), static instances of 400/500/600/700. */
@Composable
fun rememberReadexProFamily(): FontFamily {
    val regular = Font(Res.font.readex_pro_regular, FontWeight.Normal)
    val medium = Font(Res.font.readex_pro_medium, FontWeight.Medium)
    val semiBold = Font(Res.font.readex_pro_semibold, FontWeight.SemiBold)
    val bold = Font(Res.font.readex_pro_bold, FontWeight.Bold)
    return remember(regular, medium, semiBold, bold) { FontFamily(regular, medium, semiBold, bold) }
}

/** Tabular figures so counters do not jitter while they change. */
const val TabularFigures = "tnum"

private fun style(
    family: FontFamily,
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: TextUnit = 0.sp,
    features: String? = null,
) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking,
    fontFeatureSettings = features,
)

/**
 * The 8 token styles mapped onto M3 slots:
 * display -> displayLarge, headline -> headlineLarge, title -> titleLarge, title-sm -> titleMedium,
 * body -> bodyLarge, body-sm -> bodyMedium, label -> labelLarge, caption -> labelMedium.
 * Remaining M3 slots reuse the nearest token so no baseline Roboto/M3 style leaks in.
 * The display (counter) and label styles use tabular figures.
 */
fun streakTypography(family: FontFamily): Typography {
    val display = style(family, 56, 56, FontWeight.Bold, (-1.5).sp, TabularFigures)
    val headline = style(family, 30, 36, FontWeight.Bold, (-0.5).sp)
    val title = style(family, 18, 24, FontWeight.SemiBold)
    val titleSm = style(family, 16, 22, FontWeight.SemiBold)
    val body = style(family, 16, 24, FontWeight.Normal)
    val bodySm = style(family, 14, 20, FontWeight.Normal)
    val label = style(family, 15, 20, FontWeight.SemiBold, features = TabularFigures)
    val caption = style(family, 13, 18, FontWeight.Medium, features = TabularFigures)
    return Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = display,
        headlineLarge = headline,
        headlineMedium = headline,
        headlineSmall = headline,
        titleLarge = title,
        titleMedium = titleSm,
        titleSmall = titleSm,
        bodyLarge = body,
        bodyMedium = bodySm,
        bodySmall = bodySm,
        labelLarge = label,
        labelMedium = caption,
        labelSmall = caption,
    )
}
