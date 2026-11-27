package com.streakcounter.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.unit.ColorProvider
import android.util.LayoutDirection

/**
 * Glance can't draw paths, so the goal outline (spec 6.6: rounded rect from the top-start corner,
 * clockwise, mirrored for RTL) is drawn into two white ALPHA_8 masks with android.graphics: the
 * full track and the progress slice. Each is shown as an Image tinted with a day/night
 * [ColorProvider], so the theme still switches without a redraw. Masks are drawn at most at
 * [MAX_SCALE] px per dp to stay well inside the RemoteViews bitmap budget.
 */
internal object OutlineBitmap {
    private const val MAX_SCALE = 2f

    /** The slice from the top-start corner, clockwise, covering [fraction] of the perimeter (0..1). */
    fun mask(
        size: DpSize,
        cornerRadius: Dp,
        stroke: Dp,
        fraction: Float,
        density: Float,
        rtl: Boolean,
    ): Bitmap? {
        val scale = minOf(density, MAX_SCALE)
        val w = (size.width.value * scale).toInt()
        val h = (size.height.value * scale).toInt()
        if (w <= 0 || h <= 0) return null
        val end = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        if (end <= 0f) return bitmap

        val strokePx = stroke.value * scale
        val inset = strokePx / 2f
        val box = RectF(inset, inset, w - inset, h - inset)
        val radius = (cornerRadius.value * scale - inset).coerceAtLeast(0f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            color = android.graphics.Color.WHITE
        }
        val canvas = Canvas(bitmap)
        if (rtl) canvas.scale(-1f, 1f, w / 2f, h / 2f)
        if (end >= 1f) {
            canvas.drawRoundRect(box, radius, radius, paint)
        } else {
            val full = outlinePath(box, radius)
            val measure = PathMeasure(full, false)
            val slice = Path()
            measure.getSegment(0f, measure.length * end, slice, true)
            canvas.drawPath(slice, paint)
        }
        return bitmap
    }

    /** Closed rounded rect starting where the top-start radius ends, clockwise (LTR). */
    private fun outlinePath(box: RectF, r: Float): Path = Path().apply {
        val d = 2 * r
        moveTo(box.left + r, box.top)
        lineTo(box.right - r, box.top)
        arcTo(RectF(box.right - d, box.top, box.right, box.top + d), -90f, 90f, false)
        lineTo(box.right, box.bottom - r)
        arcTo(RectF(box.right - d, box.bottom - d, box.right, box.bottom), 0f, 90f, false)
        lineTo(box.left + r, box.bottom)
        arcTo(RectF(box.left, box.bottom - d, box.left + d, box.bottom), 90f, 90f, false)
        lineTo(box.left, box.top + r)
        arcTo(RectF(box.left, box.top, box.left + d, box.top + d), 180f, 90f, false)
        close()
    }
}

/**
 * Goal outline behind a widget or row of [size]: the track in outline-variant and the progress in
 * primary; a reached goal is one closed primary ring. Nothing without a goal.
 */
@Composable
internal fun OutlineProgressImage(progress: Float?, size: DpSize, cornerRadius: Dp) {
    if (progress == null) return
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val rtl = context.resources.configuration.layoutDirection == LayoutDirection.RTL
    val full = progress >= 1f
    if (!full) {
        OutlineBitmap.mask(size, cornerRadius, WidgetDimens.outlineStroke, 1f, density, rtl)?.let {
            OutlineImage(it, WidgetColors.outlineVariant)
        }
    }
    OutlineBitmap.mask(size, cornerRadius, WidgetDimens.outlineStroke, progress, density, rtl)?.let {
        OutlineImage(it, WidgetColors.primary)
    }
}

@Composable
private fun OutlineImage(bitmap: Bitmap, color: ColorProvider) {
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(color),
        modifier = GlanceModifier.fillMaxSize(),
    )
}
