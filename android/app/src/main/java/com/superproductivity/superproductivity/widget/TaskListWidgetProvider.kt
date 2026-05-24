package com.superproductivity.superproductivity.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.superproductivity.superproductivity.CapacitorMainActivity
import com.superproductivity.superproductivity.R

class TaskListWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_MARK_DONE -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                if (taskId == null) {
                    // Title taps fall through here because a ListView only has one
                    // PendingIntentTemplate. The title's fill-in intent sets only
                    // EXTRA_OPEN_APP, so detect that case and launch the app.
                    if (intent.getBooleanExtra(EXTRA_OPEN_APP, false)) {
                        context.startActivity(
                            Intent(context, CapacitorMainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                        )
                    }
                    return
                }
                val targetDone = intent.getBooleanExtra(EXTRA_TARGET_DONE, true)
                Log.d(TAG, "Toggle from widget: taskId=$taskId targetDone=$targetDone")

                WidgetDoneQueue.add(context, taskId, targetDone)
                // Independent UX-only intent tracking — keeps the sync indicator
                // visible across rapid taps and survives drain → setDone latency.
                WidgetIntents.add(context, taskId, targetDone)

                // Refresh widget to show updated state (pending indicator picks up the queue entry)
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, TaskListWidgetProvider::class.java)
                )
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_task_list)

                // Signal the live app (if any) to drain the queue. No task ID payload — the
                // queue is the single source of truth.
                LocalBroadcastManager.getInstance(context)
                    .sendBroadcast(Intent(ACTION_WIDGET_DONE_LOCAL))
            }
            ACTION_OPEN_APP -> {
                val openIntent = Intent(context, CapacitorMainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(openIntent)
            }
        }
    }

    companion object {
        private const val TAG = "TaskListWidget"
        const val ACTION_MARK_DONE = "com.superproductivity.superproductivity.WIDGET_MARK_DONE"
        const val ACTION_OPEN_APP = "com.superproductivity.superproductivity.WIDGET_OPEN_APP"
        const val ACTION_WIDGET_DONE_LOCAL = "com.superproductivity.superproductivity.WIDGET_DONE_LOCAL"
        const val EXTRA_TASK_ID = "WIDGET_TASK_ID"
        const val EXTRA_OPEN_APP = "WIDGET_OPEN_APP"
        const val EXTRA_TARGET_DONE = "WIDGET_TARGET_DONE"

        fun notifyDataChanged(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, TaskListWidgetProvider::class.java)
            )
            appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_task_list)
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_task_list)

            // Set up the RemoteViews adapter for the ListView
            val serviceIntent = Intent(context, TaskListWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_task_list, serviceIntent)
            views.setEmptyView(R.id.widget_task_list, R.id.widget_empty)

            // PendingIntent template for done checkbox clicks
            val doneIntent = Intent(context, TaskListWidgetProvider::class.java).apply {
                action = ACTION_MARK_DONE
            }
            val donePendingIntent = PendingIntent.getBroadcast(
                context, 0, doneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_task_list, donePendingIntent)

            // Header tap → open app
            val openAppIntent = Intent(context, CapacitorMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, openAppPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
