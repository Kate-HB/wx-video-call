package com.forolderr.wxvideo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews

class WxWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        render(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = prefs(context).edit()
        appWidgetIds.forEach { editor.remove(KEY_PREFIX + it) }
        editor.apply()
    }

    companion object {
        private const val PREFS = "wx_widgets"
        private const val KEY_PREFIX = "w_"
        private const val KEY_PENDING = "pending"

        fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun setPendingContact(context: Context, contact: String) {
            prefs(context).edit().putString(KEY_PENDING, contact).apply()
        }

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val stored = prefs(context).getString(KEY_PREFIX + widgetId, null)
            val contact = stored ?: prefs(context).getString(KEY_PENDING, null)
            if (contact == null) return
            if (stored == null) {
                prefs(context).edit()
                    .putString(KEY_PREFIX + widgetId, contact)
                    .remove(KEY_PENDING)
                    .apply()
            }

            val views = RemoteViews(context.packageName, R.layout.widget_contact)
            views.setTextViewText(R.id.widget_name, contact)

            val intent = Intent(context, CallFlowActivity::class.java).apply {
                putExtra(ShortcutHelper.EXTRA_CONTACT, contact)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pending = PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
