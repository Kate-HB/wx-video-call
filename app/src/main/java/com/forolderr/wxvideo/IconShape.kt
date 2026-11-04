package com.forolderr.wxvideo

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

object IconShape {
    // Keep icon sizes stable on wide tablets; one normalized pixel is about one dp.
    fun normalizationScale(density: Float): Float =
        (density.takeIf { it.isFinite() && it > 0f } ?: 3.5f) / 1.1f

    private val ringOffsets = (5..10).map { radius ->
        IntArray(24) { index ->
            val angle = (index / 2) * Math.PI / 6
            ((if (index % 2 == 0) cos(angle) else sin(angle)) * radius).roundToInt()
        }
    }

    fun find(pixels: IntArray, width: Int, height: Int, left: Int, top: Int,
             right: Int, bottom: Int, plus: Boolean,
             excluded: (Int, Int) -> Boolean = { _, _ -> false }): Pair<Float, Float>? {
        if (pixels.size != width * height || right - left < 40 || bottom - top < 80) return null
        val gray = IntArray(pixels.size) { i ->
            val p = pixels[i]
            (((p shr 16) and 255) + ((p shr 8) and 255) + (p and 255)) / 3
        }
        fun luminance(x: Int, y: Int) = gray[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]
        fun stroke(x: Int, y: Int, background: Int): Int {
            var best = background
            for (dx in -1..1) for (dy in -1..1) {
                val value = luminance(x + dx, y + dy)
                if (abs(value - background) > abs(best - background)) best = value
            }
            return best
        }
        val foreground = IntArray(15)
        val background = IntArray(16)
        val candidates = mutableListOf<Pair<Int, Int>>()
        val xStart = if (plus) left + ((right - left) * .89f).toInt() else left + 10
        val xEnd = right - 10
        val yStart = if (plus) top + ((bottom - top) * .42f).toInt() else maxOf(top + 10, 28)
        val yEnd = if (plus) bottom - 10 else minOf(bottom - 10, top + 100)
        for (y in yStart..yEnd) for (x in xStart..xEnd) {
            if (candidates.any { abs(it.first - x) < 22 && abs(it.second - y) < 22 }) continue
            for (radius in 5..10) {
                var fgCount = 0
                var bgCount = 0
                if (plus) {
                    val diagonal = maxOf(3, radius * 2 / 3)
                    if (abs(luminance(x, y) - luminance(x - diagonal, y - diagonal)) < 30) continue
                    for (i in -2..2) {
                        val d = i * radius / 2
                        foreground[fgCount++] = luminance(x + d, y)
                        foreground[fgCount++] = luminance(x, y + d)
                    }
                    background[bgCount++] = luminance(x - diagonal, y - diagonal)
                    background[bgCount++] = luminance(x - diagonal, y + diagonal)
                    background[bgCount++] = luminance(x + diagonal, y - diagonal)
                    background[bgCount++] = luminance(x + diagonal, y + diagonal)
                } else {
                    // Ring strokes are antialiased and often lie between pixels after downsampling.
                    // Compare against the local background and allow a one-pixel stroke offset.
                    val localBackground = luminance(x, y)
                    if (abs(stroke(x + radius, y, localBackground) - localBackground) < 30) continue
                    val offsets = ringOffsets[radius - 5]
                    for (i in 0 until 12) {
                        foreground[fgCount++] = stroke(x + offsets[i * 2], y + offsets[i * 2 + 1], localBackground)
                    }
                    for (d in 0..2) foreground[fgCount++] = stroke(x + radius + d, y + radius + d, localBackground)
                    background[bgCount++] = localBackground
                    // A magnifier has an empty interior; letters and the toolbar's circled plus do not.
                    background[bgCount++] = luminance(x - radius / 2, y)
                    background[bgCount++] = luminance(x + radius / 2, y)
                    background[bgCount++] = luminance(x, y - radius / 2)
                    background[bgCount++] = luminance(x, y + radius / 2)
                    background[bgCount++] = luminance(x - radius / 3, y - radius / 3)
                    background[bgCount++] = luminance(x + radius / 3, y - radius / 3)
                    background[bgCount++] = luminance(x - radius / 3, y + radius / 3)
                    background[bgCount++] = luminance(x + radius / 3, y + radius / 3)
                    background[bgCount++] = luminance(x - radius - 3, y)
                    background[bgCount++] = luminance(x, y - radius - 3)
                    background[bgCount++] = luminance(x + radius + 3, y)
                    background[bgCount++] = luminance(x, y + radius + 3)
                    background[bgCount++] = luminance(x - radius, y - radius)
                    background[bgCount++] = luminance(x + radius, y - radius)
                    background[bgCount++] = luminance(x - radius, y + radius)
                }
                var fgSum = 0
                var bgSum = 0
                for (i in 0 until fgCount) fgSum += foreground[i]
                for (i in 0 until bgCount) bgSum += background[i]
                val fg = fgSum.toDouble() / fgCount
                val bg = bgSum.toDouble() / bgCount
                if (abs(fg - bg) < 60) continue
                if ((0 until fgCount).any { abs(foreground[it] - fg) > 55 } ||
                    (0 until bgCount).any { abs(background[it] - bg) > 45 }) continue
                if (excluded(x, y)) continue
                candidates.add(x to y)
                if (candidates.size > 1) return null
                break
            }
        }
        return candidates.singleOrNull()?.let { it.first.toFloat() to it.second.toFloat() }
    }
}
