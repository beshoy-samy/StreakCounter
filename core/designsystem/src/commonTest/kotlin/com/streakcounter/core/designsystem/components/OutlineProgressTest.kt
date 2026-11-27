package com.streakcounter.core.designsystem.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.math.abs
import kotlin.test.Test

class OutlineProgressTest {
    // 300 x 100 card, corner radius 32: perimeter = 2*236 + 2*36 + 2*pi*32 ~ 745.06
    private val size = Size(300f, 100f)
    private val radius = 32f

    private fun assertNear(expected: Offset, actual: Offset) {
        assertThat(abs(expected.x - actual.x) < 0.5f && abs(expected.y - actual.y) < 0.5f)
            .isTrue()
    }

    @Test
    fun zeroProgressIsEmptyAndStartsAtTopStartCornerEnd() {
        val segment = outlineSegment(0f, LayoutDirection.Ltr)
        assertThat(segment.startFraction).isEqualTo(0f)
        assertThat(segment.endFraction).isEqualTo(0f)
        assertNear(Offset(radius, 0f), outlinePoint(0f, size, radius, LayoutDirection.Ltr))
    }

    @Test
    fun ltrSixtyPercentEndsOnBottomEdgeRightHalf() {
        val segment = outlineSegment(0.6f, LayoutDirection.Ltr)
        assertThat(segment.endFraction).isEqualTo(0.6f)
        assertThat(segment.mirrored).isFalse()
        // 0.6 * 745.06 = 447.0: past top, top-end arc, right edge and bottom-end arc, 74.5 into the bottom edge.
        assertNear(Offset(193.5f, 100f), outlinePoint(0.6f, size, radius, LayoutDirection.Ltr))
    }

    @Test
    fun rtlIsMirroredHorizontally() {
        val segment = outlineSegment(0.6f, LayoutDirection.Rtl)
        assertThat(segment.mirrored).isTrue()
        assertNear(Offset(106.5f, 100f), outlinePoint(0.6f, size, radius, LayoutDirection.Rtl))
        assertNear(Offset(300f - radius, 0f), outlinePoint(0f, size, radius, LayoutDirection.Rtl))
    }

    @Test
    fun quarterAndHalfProgressRunClockwise() {
        // 0.25 * 745.06 = 186.3 < 236: still on the top edge, heading right.
        assertNear(Offset(32f + 186.3f, 0f), outlinePoint(0.25f, size, radius, LayoutDirection.Ltr))
        // Half way round is exactly where the bottom-end arc finishes and the bottom edge starts.
        assertNear(Offset(268f, 100f), outlinePoint(0.5f, size, radius, LayoutDirection.Ltr))
    }

    @Test
    fun fullProgressIsClosed() {
        val segment = outlineSegment(1f, LayoutDirection.Ltr)
        assertThat(segment.endFraction).isEqualTo(1f)
        assertThat(segment.isFull).isTrue()
        assertNear(
            outlinePoint(0f, size, radius, LayoutDirection.Ltr),
            outlinePoint(1f, size, radius, LayoutDirection.Ltr),
        )
    }

    @Test
    fun overOneClampsToFull() {
        assertThat(outlineSegment(1.7f, LayoutDirection.Ltr).endFraction).isEqualTo(1f)
        assertThat(outlineSegment(1.7f, LayoutDirection.Ltr).isFull).isTrue()
    }

    @Test
    fun negativeAndNaNClampToEmpty() {
        assertThat(outlineSegment(-0.3f, LayoutDirection.Ltr).endFraction).isEqualTo(0f)
        assertThat(outlineSegment(Float.NaN, LayoutDirection.Ltr).endFraction).isEqualTo(0f)
    }
}
