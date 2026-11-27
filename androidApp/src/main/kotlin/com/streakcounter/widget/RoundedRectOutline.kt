package com.streakcounter.widget

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The card outline as plain geometry (spec 6.6): a rounded rect that starts where the top-start
 * radius ends and runs clockwise (LTR; RTL mirrors the canvas). [radius] is clamped to half the
 * shorter side, so a short widget with a large corner radius still gets a real rounded rect (a
 * pill) and an exact [length]. Pure math, so it is unit-tested without android.graphics.
 */
internal class RoundedRectOutline(
    private val left: Float,
    private val top: Float,
    private val right: Float,
    private val bottom: Float,
    radius: Float,
) {
    val radius: Float = minOf(radius, (right - left) / 2f, (bottom - top) / 2f).coerceAtLeast(0f)

    sealed interface Piece {
        val length: Float

        data class Line(val x0: Float, val y0: Float, val x1: Float, val y1: Float) : Piece {
            override val length: Float get() = kotlin.math.hypot(x1 - x0, y1 - y0)
        }

        /** Circle at ([cx], [cy]) of radius [r]; angles in degrees, 0 = +x, clockwise on screen. */
        data class Arc(val cx: Float, val cy: Float, val r: Float, val startDeg: Float, val sweepDeg: Float) : Piece {
            override val length: Float get() = (r * sweepDeg * PI / 180.0).toFloat()
        }
    }

    private val pieces: List<Piece> = run {
        val r = this.radius
        listOf(
            Piece.Line(left + r, top, right - r, top),
            Piece.Arc(right - r, top + r, r, -90f, 90f),
            Piece.Line(right, top + r, right, bottom - r),
            Piece.Arc(right - r, bottom - r, r, 0f, 90f),
            Piece.Line(right - r, bottom, left + r, bottom),
            Piece.Arc(left + r, bottom - r, r, 90f, 90f),
            Piece.Line(left, bottom - r, left, top + r),
            Piece.Arc(left + r, top + r, r, 180f, 90f),
        ).filter { it.length > 0f }
    }

    val length: Float = pieces.sumOf { it.length.toDouble() }.toFloat()

    /** The pieces covering the first [fraction] (0..1) of [length], the last one cut to fit. */
    fun slice(fraction: Float): List<Piece> {
        var remaining = length * (if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f))
        val result = mutableListOf<Piece>()
        for (piece in pieces) {
            if (remaining <= 0f) break
            val len = piece.length
            if (len <= remaining) {
                result += piece
                remaining -= len
            } else {
                val t = remaining / len
                result += when (piece) {
                    is Piece.Line -> piece.copy(x1 = piece.x0 + (piece.x1 - piece.x0) * t, y1 = piece.y0 + (piece.y1 - piece.y0) * t)
                    is Piece.Arc -> piece.copy(sweepDeg = piece.sweepDeg * t)
                }
                remaining = 0f
            }
        }
        return result
    }

    /** Sample points along [pieces] (ends of lines, every few degrees of arcs). */
    fun points(pieces: List<Piece>): List<Pair<Float, Float>> = pieces.flatMap { piece ->
        when (piece) {
            is Piece.Line -> listOf(piece.x0 to piece.y0, piece.x1 to piece.y1)
            is Piece.Arc -> (0..10).map { i ->
                val a = Math.toRadians((piece.startDeg + piece.sweepDeg * i / 10f).toDouble())
                (piece.cx + piece.r * cos(a)).toFloat() to (piece.cy + piece.r * sin(a)).toFloat()
            }
        }
    }
}
