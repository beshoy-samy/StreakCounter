package com.streakcounter.core.designsystem.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.streakcounter.core.designsystem.UiTestBase
import kotlin.math.abs
import kotlin.test.Test

/**
 * Geometry tests run against the same path the card draws: `outlineSegmentPath`, measured with
 * `PathMeasure`. They extend UiTestBase only to get real graphics (Robolectric native) on Android.
 */
class OutlineProgressTest : UiTestBase() {
    // 300 x 100 card, corner radius 32: perimeter = 2*236 + 2*36 + 2*pi*32 ~ 745.06
    private val size = Size(300f, 100f)
    private val radius = 32f

    private fun segmentPath(progress: Float, direction: LayoutDirection = LayoutDirection.Ltr) =
        outlineSegmentPath(size, radius, outlineSegment(progress, direction))

    private fun length(progress: Float): Float =
        PathMeasure().apply { setPath(segmentPath(progress), false) }.length

    private fun endPoint(progress: Float): Offset {
        val m = PathMeasure().apply { setPath(segmentPath(progress), false) }
        return m.getPosition(m.length)
    }

    private fun startPoint(progress: Float): Offset {
        val m = PathMeasure().apply { setPath(segmentPath(progress), false) }
        return m.getPosition(0f)
    }

    private fun assertNear(expected: Offset, actual: Offset) {
        assertThat(abs(expected.x - actual.x) < 1f && abs(expected.y - actual.y) < 1f).isTrue()
    }

    @Test
    fun segmentStartsAtTopStartCornerEnd() {
        assertNear(Offset(radius, 0f), startPoint(0.3f))
    }

    @Test
    fun quarterEndsOnTopEdgeHeadingRight() {
        // 0.25 * 745.06 = 186.3 < 236: still on the top edge.
        assertNear(Offset(32f + 186.3f, 0f), endPoint(0.25f))
    }

    @Test
    fun halfEndsWhereBottomEndArcFinishes() {
        assertNear(Offset(268f, 100f), endPoint(0.5f))
    }

    @Test
    fun sixtyPercentEndsOnBottomEdgeRightHalf() {
        // 447.0 along: past top, top-end arc, right edge, bottom-end arc, 74.5 into the bottom edge.
        assertNear(Offset(193.5f, 100f), endPoint(0.6f))
        assertThat(outlineSegment(0.6f, LayoutDirection.Ltr).mirrored).isFalse()
    }

    @Test
    fun segmentLengthIsProportionalToProgress() {
        val full = length(1f)
        assertThat(abs(length(0.6f) - 0.6f * full) < 1f).isTrue()
    }

    @Test
    fun rtlIsAMirrorFlagNotADifferentPath() {
        // The canvas flips x at draw time, so the path is identical and only the flag differs.
        val rtl = outlineSegment(0.6f, LayoutDirection.Rtl)
        assertThat(rtl.mirrored).isTrue()
        assertThat(rtl.endFraction).isEqualTo(0.6f)
        assertNear(endPoint(0.6f), PathMeasure().apply {
            setPath(segmentPath(0.6f, LayoutDirection.Rtl), false)
        }.let { it.getPosition(it.length) })
    }

    @Test
    fun fullProgressIsClosed() {
        assertThat(outlineSegment(1f, LayoutDirection.Ltr).isFull).isTrue()
        val m = PathMeasure().apply { setPath(segmentPath(1f), false) }
        assertNear(m.getPosition(0f), m.getPosition(m.length))
    }

    @Test
    fun overOneClampsToFull() {
        assertThat(outlineSegment(1.7f, LayoutDirection.Ltr).endFraction).isEqualTo(1f)
        assertThat(abs(length(1.7f) - length(1f)) < 0.5f).isTrue()
    }

    @Test
    fun negativeAndNaNClampToEmpty() {
        assertThat(outlineSegment(-0.3f, LayoutDirection.Ltr).endFraction).isEqualTo(0f)
        assertThat(outlineSegment(Float.NaN, LayoutDirection.Ltr).endFraction).isEqualTo(0f)
        assertThat(length(-0.3f) < 0.5f).isTrue()
        assertThat(length(Float.NaN) < 0.5f).isTrue()
    }
}
