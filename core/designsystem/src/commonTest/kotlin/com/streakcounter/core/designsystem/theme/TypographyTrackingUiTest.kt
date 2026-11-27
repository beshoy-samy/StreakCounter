package com.streakcounter.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.streakcounter.core.designsystem.UiTestBase
import kotlin.test.Test
import kotlin.test.assertEquals

/** Negative letter spacing pulls Arabic letters apart at their joins, so RTL text gets none. */
@OptIn(ExperimentalTestApi::class)
class TypographyTrackingUiTest : UiTestBase() {

    private fun Typography.allStyles() = listOf(
        displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall,
        titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall, labelLarge, labelMedium, labelSmall,
    )

    private fun themeTypography(direction: LayoutDirection): Typography {
        lateinit var typography: Typography
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    StreakTheme(darkTheme = false) { typography = MaterialTheme.typography }
                }
            }
            waitForIdle()
        }
        return typography
    }

    @Test
    fun rtl_theme_has_no_letter_spacing() {
        val spacing = themeTypography(LayoutDirection.Rtl).allStyles().map { it.letterSpacing }
        assertEquals(List(spacing.size) { 0.sp }, spacing)
    }

    @Test
    fun ltr_theme_keeps_the_design_tracking() {
        val typography = themeTypography(LayoutDirection.Ltr)
        assertEquals((-1.5).sp, typography.displayLarge.letterSpacing)
        assertEquals((-0.5).sp, typography.headlineLarge.letterSpacing)
    }

    @Test
    fun tracking_is_zero_only_in_rtl() {
        var rtl = TextUnit.Unspecified
        var ltr = TextUnit.Unspecified
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { rtl = tracking((-3).sp) }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { ltr = tracking((-3).sp) }
            }
            waitForIdle()
        }
        assertEquals(0.sp, rtl)
        assertEquals((-3).sp, ltr)
    }
}
