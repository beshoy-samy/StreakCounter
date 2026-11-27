package com.streakcounter.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
 * clockwise, mirrored for RTL; geometry in [RoundedRectOutline]) is drawn into two white ALPHA_8
 * masks at the widget's real size (no stretching): the full track and the progress slice. Each is shown as an Image tinted with a day/night
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
        // Stroke is centered on the path: inset by half so it stays inside the bitmap.
        val inset = strokePx / 2f
        val outline = RoundedRectOutline(
            left = inset,
            top = inset,
            right = w - inset,
            bottom = h - inset,
            radius = (cornerRadius.value * scale - inset).coerceAtLeast(0f),
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            color = android.graphics.Color.WHITE
        }
        val canvas = Canvas(bitmap)
        if (rtl) canvas.scale(-1f, 1f, w / 2f, h / 2f)
        if (end >= 1f) {
            val r = outline.radius
            canvas.drawRoundRect(RectF(inset, inset, w - inset, h - inset), r, r, paint)
        } else {
            canvas.drawPath(outline.slice(end).toPath(), paint)
        }
        return bitmap
    }

    private fun List<RoundedRectOutline.Piece>.toPath(): Path = Path().also { path ->
        forEachIndexed { index, piece ->
            when (piece) {
                is RoundedRectOutline.Piece.Line -> {
                    if (index == 0) path.moveTo(piece.x0, piece.y0)
                    path.lineTo(piece.x1, piece.y1)
                }
                is RoundedRectOutline.Piece.Arc -> {
                    val oval = RectF(piece.cx - piece.r, piece.cy - piece.r, piece.cx + piece.r, piece.cy + piece.r)
                    path.arcTo(oval, piece.startDeg, piece.sweepDeg, index == 0)
                }
            }
        }
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
