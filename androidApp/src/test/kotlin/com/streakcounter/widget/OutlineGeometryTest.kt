package com.streakcounter.widget

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import org.junit.Test
import kotlin.math.PI

class OutlineGeometryTest {
    // The smallest 2×1 small widget (110×40 dp) at 2 px/dp with the xl radius (32 dp) and a 3 dp
    // stroke: the radius (61 px) is more than half the height (37 px), so it must be clamped.
    private val scale = 2f
    private val inset = 1.5f * scale
    private val left = inset
    private val top = inset
    private val right = SmallStreakWidget.SIZE_2X1.width.value * scale - inset
    private val bottom = SmallStreakWidget.SIZE_2X1.height.value * scale - inset
    private val radius = 32f * scale - inset

    private val outline = RoundedRectOutline(left, top, right, bottom, radius)

    @Test
    fun radius_is_clamped_to_half_the_shorter_side() {
        assertThat(outline.radius).isEqualTo((bottom - top) / 2f)
    }

    @Test
    fun length_is_the_rounded_rect_perimeter() {
        val w = right - left
        val h = bottom - top
        val r = minOf(radius, w / 2f, h / 2f)
        val expected = 2 * (w - 2 * r) + 2 * (h - 2 * r) + 2 * PI.toFloat() * r

        assertThat(outline.length).isCloseTo(expected, 0.01f)
    }

    @Test
    fun the_full_outline_stays_inside_the_box() {
        outline.points(outline.slice(1f)).forEach { (x, y) ->
            assertThat(x).isGreaterThanOrEqualTo(left - 0.01f)
            assertThat(x).isLessThanOrEqualTo(right + 0.01f)
            assertThat(y).isGreaterThanOrEqualTo(top - 0.01f)
            assertThat(y).isLessThanOrEqualTo(bottom + 0.01f)
        }
    }

    @Test
    fun half_progress_covers_half_the_length() {
        assertThat(outline.slice(0.5f).sumOf { it.length.toDouble() }.toFloat()).isCloseTo(outline.length / 2f, 0.01f)
    }

    @Test
    fun progress_starts_at_the_end_of_the_top_start_corner() {
        val first = outline.points(outline.slice(0.01f)).first()

        assertThat(first.first).isCloseTo(left + outline.radius, 0.01f)
        assertThat(first.second).isCloseTo(top, 0.01f)
    }

    @Test
    fun an_unclamped_radius_is_kept() {
        val wide = RoundedRectOutline(0f, 0f, 400f, 300f, 36f)

        assertThat(wide.radius).isEqualTo(36f)
        assertThat(wide.length).isCloseTo(2 * 328f + 2 * 228f + 2 * PI.toFloat() * 36f, 0.01f)
    }
}
