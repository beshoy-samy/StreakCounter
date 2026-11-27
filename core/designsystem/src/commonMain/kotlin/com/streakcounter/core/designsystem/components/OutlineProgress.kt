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
 * The slice of the outline described by [segment] (LTR orientation; RTL is a canvas mirror at draw
 * time). A full segment is the closed outline. This is the path that gets drawn.
 */
internal fun outlineSegmentPath(size: Size, cornerRadius: Float, segment: OutlineSegment): Path {
    val full = outlinePath(size, cornerRadius)
    if (segment.isFull) return full
    val measure = PathMeasure()
    measure.setPath(full, false)
    return Path().also {
        measure.getSegment(
            measure.length * segment.startFraction,
            measure.length * segment.endFraction,
            it,
            true,
        )
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
    Canvas(modifier) {
        drawOutline(segment, cornerRadius.toPx(), strokeWidth.toPx(), trackColor, progressColor)
    }
}

private fun DrawScope.drawOutline(
    segment: OutlineSegment,
    cornerRadius: Float,
    strokeWidth: Float,
    trackColor: Color,
    progressColor: Color,
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
                drawPath(outlineSegmentPath(box, radius, segment), progressColor, style = stroke)
            }
        }
    }
}

private inline fun DrawScope.translateBy(by: Float, block: DrawScope.() -> Unit) =
    translate(by, by, block)
