package com.forolderr.wxvideo

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

object ShortcutHelper {

    private val BG_COLORS = intArrayOf(
        0xFF07C160.toInt(), 0xFF1677FF.toInt(), 0xFFFF6B35.toInt(),
        0xFF8E44AD.toInt(), 0xFFE91E63.toInt(), 0xFF00B4D8.toInt(),
        0xFF43A047.toInt(), 0xFFF4511E.toInt()
    )

    fun addShortcut(context: Context, name: String) {
        val id = "wx_" + (name.hashCode() and 0x7FFFFFFF)
        val intent = Intent(context, CallFlowActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_CONTACT, name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val shortcut = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(name)
            .setLongLabel("微信视频 · $name")
            .setIcon(IconCompat.createWithBitmap(makeIcon(name)))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    private fun makeIcon(name: String, size: Int = 288): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = BG_COLORS[(name.hashCode() and 0x7FFFFFFF) % BG_COLORS.size]
        c.drawRoundRect(
            0f, 0f, size.toFloat(), size.toFloat(),
            size * 0.24f, size * 0.24f, paint
        )

        val label = name.take(3)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        var textSize = size * 0.44f
        paint.textSize = textSize
        val maxWidth = size * 0.78f
        val measured = paint.measureText(label)
        if (measured > maxWidth) {
            textSize *= maxWidth / measured
            paint.textSize = textSize
        }
        val fm = paint.fontMetrics
        val baseline = size / 2f - (fm.ascent + fm.descent) / 2f
        c.drawText(label, size / 2f, baseline, paint)
        return bmp
    }

    const val EXTRA_CONTACT = "contact"
}
