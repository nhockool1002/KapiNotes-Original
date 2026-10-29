package com.kapi.notes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kapi.notes.MainActivity
import com.kapi.notes.R

/**
 * Tablet home-screen quick-capture widget ("Widget trang chủ" from the feature list): a single
 * tap launches straight into a brand-new blank note, skipping the Dashboard and its "Tạo ghi
 * chú" dialog entirely - see [MainActivity.ACTION_QUICK_NOTE]. Meant for a tablet's larger home
 * screen grid (140dp min size, see widget_quick_note_info.xml); Android itself has no concept of
 * "only allow placing this widget on a tablet", so this is a sizing/design choice, not an
 * enforced restriction.
 */
class QuickNoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_note)
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_QUICK_NOTE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
