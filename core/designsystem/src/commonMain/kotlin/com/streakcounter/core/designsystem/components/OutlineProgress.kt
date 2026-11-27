package com.streakcounter.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A slice of the card outline. The outline starts where the top-start corner radius ends (on the top
 * edge) and runs clockwise; [mirrored] flips it horizontally for RTL so it runs counter-clockwise
 * from the top-end corner. Fractions are of the full perimeter.
 */
data class OutlineSegment(
    val startFraction: Float,
    val endFraction: Float,
    val mirrored: Boolean,
) {
    val isFull: Boolean get() = endFraction >= 1f
}

/** Clamps [progress] to 0..1 (NaN counts as 0) and picks the direction for [layoutDirection]. */
fun outlineSegment(progress: Float, layoutDirection: LayoutDirection): OutlineSegment {
    val end = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
    return OutlineSegment(
        startFraction = 0f,
        endFraction = end,
        mirrored = layoutDirection == LayoutDirection.Rtl,
    )
}

/**
 * Point on the rounded-rectangle perimeter at [fraction] (0 = top edge where the top-start radius
 * ends, clockwise in LTR, mirrored in RTL). Pure geometry, shares its walk with [outlinePath].
 */
fun outlinePoint(
    fraction: Float,
    size: Size,
    cornerRadius: Float,
    layoutDirection: LayoutDirection,
): Offset {
    val w = size.width
    val h = size.height
    val r = cornerRadius
    val top = w - 2 * r
    val side = h - 2 * r
    val arc = (PI.toFloat() / 2f) * r
    val total = 2 * top + 2 * side + 4 * arc
    var d = fraction.coerceIn(0f, 1f) * total

    fun arcPoint(cx: Float, cy: Float, startAngle: Float, length: Float): Offset {
        val a = startAngle + if (r == 0f) 0f else length / r
        return Offset(cx + r * cos(a), cy + r * sin(a))
    }

    val half = (PI / 2).toFloat()
    val p: Offset = when {
        d <= top -> Offset(r + d, 0f)
        else -> {
            d -= top
            if (d <= arc) arcPoint(w - r, r, -half, d)
            else {
                d -= arc
                if (d <= side) Offset(w, r + d)
                else {
                    d -= side
                    if (d <= arc) arcPoint(w - r, h - r, 0f, d)
                    else {
                        d -= arc
                        if (d <= top) Offset(w - r - d, h)
                        else {
                            d -= top
                            if (d <= arc) arcPoint(r, h - r, half, d)
                            else {
                                d -= arc
                                if (d <= side) Offset(0f, h - r - d)
                                else arcPoint(r, r, 2 * half, d - side)
                            }
                        }
                    }
                }
            }
        }
    }
    return if (layoutDirection == LayoutDirection.Rtl) Offset(w - p.x, p.y) else p
}

/** Closed rounded-rect path (LTR orientation) starting at the end of the top-start radius, clockwise. */
internal fun outlinePath(size: Size, cornerRadius: Float): Path {
    val w = size.width
    val h = size.height
    val r = cornerRadius
    val d = 2 * r
    return Path().apply {
        moveTo(r, 0f)
        lineTo(w - r, 0f)
        arcTo(Rect(w - d, 0f, w, d), -90f, 90f, false)
        lineTo(w, h - r)
        arcTo(Rect(w - d, h - d, w, h), 0f, 90f, false)
        lineTo(r, h)
        arcTo(Rect(0f, h - d, d, h), 90f, 90f, false)
        lineTo(0f, r)
        arcTo(Rect(0f, 0f, d, d), 180f, 90f, false)
        close()
    }
}

/**
 * Card-edge goal progress: [track] ring plus a [progress] arc with round caps. `null` draws nothing;
 * a full ring (progress >= 1) is drawn closed in the progress color.
 */
@Composable
fun OutlineProgress(
    progress: Float?,
    cornerRadius: Dp,
    trackColor: Color,
    progressColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 3.dp,
) {
    if (progress == null) return
    val layoutDirection = LocalLayoutDirection.current
    val segment = remember(progress, layoutDirection) { outlineSegment(progress, layoutDirection) }
    val measure = remember { PathMeasure() }
    Canvas(modifier) {
        drawOutline(segment, cornerRadius.toPx(), strokeWidth.toPx(), trackColor, progressColor, measure)
    }
}

private fun DrawScope.drawOutline(
    segment: OutlineSegment,
    cornerRadius: Float,
    strokeWidth: Float,
    trackColor: Color,
    progressColor: Color,
    measure: PathMeasure,
) {
    // Stroke is centered on the path: inset by half so it stays inside the card bounds.
    val inset = strokeWidth / 2f
    val box = Size(size.width - strokeWidth, size.height - strokeWidth)
    val radius = (cornerRadius - inset).coerceAtLeast(0f)
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    val sx = if (segment.mirrored) -1f else 1f

    scale(scaleX = sx, scaleY = 1f, pivot = Offset(size.width / 2f, size.height / 2f)) {
        translateBy(inset) {
            if (segment.isFull) {
                drawRoundRect(progressColor, size = box, cornerRadius = CornerRadius(radius), style = stroke)
                return@translateBy
            }
            drawRoundRect(trackColor, size = box, cornerRadius = CornerRadius(radius), style = stroke)
            if (segment.endFraction > 0f) {
                val path = outlinePath(box, radius)
                measure.setPath(path, false)
                val out = Path()
                measure.getSegment(
                    measure.length * segment.startFraction,
                    measure.length * segment.endFraction,
                    out,
                    true,
                )
                drawPath(out, progressColor, style = stroke)
            }
        }
    }
}

private inline fun DrawScope.translateBy(by: Float, block: DrawScope.() -> Unit) =
    translate(by, by, block)
