package com.forolderr.wxvideo

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Uses accessibility overlay permission; no vendor-specific floating-window permission. */
class CalibrationOverlay(private val service: AccessibilityService) {
    private val manager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    val showing get() = view != null

    fun close() {
        view?.let { runCatching { manager.removeViewImmediate(it) } }
        view = null
    }

    fun show(message: String, cancel: () -> Unit, actionLabel: String? = null,
             action: (() -> Unit)? = null, pick: ((Float, Float) -> Unit)? = null,
             back: (() -> Unit)? = null) {
        close()
        val root = object : FrameLayout(service) {
            private var touch: Pair<Float, Float>? = null
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (pick == null) return super.onTouchEvent(event)
                if (event.action == MotionEvent.ACTION_UP) {
                    touch = event.rawX to event.rawY
                    performClick()
                }
                if (event.action == MotionEvent.ACTION_CANCEL) touch = null
                return true
            }
            override fun performClick(): Boolean {
                super.performClick()
                val position = touch ?: return false
                touch = null
                close()
                pick?.invoke(position.first, position.second)
                return true
            }
        }
        // No scrim at all: dimming the page makes the card look modal and the page untappable.

        val panel = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.argb(235, 255, 255, 255), dp(24))
            setPadding(dp(20), dp(18), dp(20), dp(14))
            elevation = dp(6).toFloat()
            isClickable = true // taps on the card must not fall through to the picker
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(20), 0, dp(20), 0) }
        }
        panel.addView(TextView(service).apply {
            text = message
            setTextColor(Color.rgb(20, 24, 21))
            textSize = 16f
            setLineSpacing(dp(4).toFloat(), 1f)
        })
        if (pick != null) panel.addView(TextView(service).apply {
            text = "轻点下方画面中的目标位置"
            setTextColor(Color.rgb(6, 138, 74))
            textSize = 13.5f
            setPadding(0, dp(6), 0, 0)
        })
        if (actionLabel != null && action != null) panel.addView(primaryButton(actionLabel) {
            close(); action()
        })
        val controls = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        if (back != null) controls.addView(secondaryButton("上一步", 1f) { close(); back() })
        controls.addView(secondaryButton("取消", 0f) {
            close(); cancel()
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { if (back != null) marginStart = dp(10) })
        if (pick != null) controls.addView(secondaryButton("移动提示框", 0f) {
            val atTop = (panel.tag as? Boolean) != true
            panel.tag = atTop
            (panel.layoutParams as FrameLayout.LayoutParams).gravity =
                if (atTop) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
            panel.layoutParams = panel.layoutParams
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(10) })
        if (controls.childCount > 0) {
            controls.setPadding(0, dp(10), 0, 0)
            panel.addView(controls)
        }

        root.addView(panel)
        (panel.layoutParams as FrameLayout.LayoutParams).gravity =
            if (pick != null) Gravity.CENTER else Gravity.CENTER
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            if (pick != null) WindowManager.LayoutParams.MATCH_PARENT
            else WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        manager.addView(root, params)
        view = root
    }

    private fun rounded(color: Int, radius: Int, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (stroke > 0) setStroke(stroke, strokeColor)
        }

    private fun primaryButton(label: String, onClick: () -> Unit): Button =
        Button(service).apply {
            text = label
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(6, 138, 74), dp(14))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
            ).apply { topMargin = dp(14) }
        }

    private fun secondaryButton(label: String, weight: Float,
                                onClick: () -> Unit): Button =
        Button(service).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.rgb(45, 52, 47))
            background = rounded(Color.rgb(238, 241, 238), dp(14))
            setOnClickListener { onClick() }
            layoutParams = if (weight > 0f) {
                LinearLayout.LayoutParams(0, dp(46), weight)
            } else {
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)
                ).apply { minimumWidth = dp(72) }
            }
        }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()
}
