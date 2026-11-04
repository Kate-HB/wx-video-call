package com.forolderr.wxvideo

data class ScreenFrame(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    fun contains(x: Float, y: Float) = x.isFinite() && y.isFinite() &&
        width > 0 && height > 0 && x >= left && x < right && y >= top && y < bottom
}

/** Never stretch a calibrated coordinate into a different layout. */
data class CalibratedPoint(val x: Float, val y: Float, val frame: ScreenFrame) {
    fun resolve(current: ScreenFrame): Pair<Float, Float>? =
        if (current == frame && current.contains(x, y)) x to y else null

    companion object {
        fun capture(x: Float, y: Float, frame: ScreenFrame): CalibratedPoint? =
            if (frame.contains(x, y)) CalibratedPoint(x, y, frame) else null
    }
}

enum class LookupDecision { WAIT, CALIBRATED, STOP }
fun lookupDecision(now: Long, deadline: Long, calibrated: Boolean) = when {
    now < deadline -> LookupDecision.WAIT
    calibrated -> LookupDecision.CALIBRATED
    else -> LookupDecision.STOP
}

fun calibrationMatches(saved: String, current: String) = saved.isNotBlank() && saved == current

