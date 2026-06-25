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
        Log.d(TAG, "Widget onReceive action = ${intent.action}")

        when (intent.action) {
            ACTION_MARK_DONE -> {
                if (intent.getBooleanExtra(EXTRA_OPEN_APP, false)) {
                    val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                    context.startActivity(
                        Intent(context, CapacitorMainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            if (taskId != null) {
                                putExtra("WIDGET_TASK_ID", taskId)
                            }
                        }
                    )
                    return
                }

                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val targetDone = intent.getBooleanExtra(EXTRA_TARGET_DONE, true)
                Log.d(TAG, "Toggle from widget: taskId=$taskId targetDone=$targetDone")

                WidgetDoneQueue.add(context, taskId, targetDone)
                // Independent UX-only intent tracking — keeps the sync indicator
                // visible across rapid taps and survives drain → setDone latency.
                WidgetIntents.add(context, taskId, targetDone)

                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, TaskListWidgetProvider::class.java)
                )
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_task_list)

                // Signal the live app (if any) to drain the queue. No task ID payload—
                // the queue is the single source of truth.
                context.sendBroadcast(Intent(ACTION_WIDGET_DONE_LOCAL).apply {
                    `package` = context.packageName
                })
            }

            ACTION_TOGGLE_HIDE_DONE -> {
                val nowHiding = WidgetSettings.toggleHideDone(context)
                Log.d(TAG, "Toggled hideDone -> $nowHiding")
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(
                    ComponentName(context, TaskListWidgetProvider::class.java)
                )
                // Refresh the list first (the factory re-reads the pref to filter),
                // then rebuild the chrome so the toggle icon update is the last
                // RemoteViews delivery the launcher receives.
                mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_task_list)
                for (id in ids) updateWidget(context, mgr, id)
            }

        }
    }

    companion object {
        private const val TAG = "TaskListWidget"
        const val ACTION_MARK_DONE = "com.superproductivity.superproductivity.WIDGET_MARK_DONE"
        const val ACTION_WIDGET_DONE_LOCAL = "com.superproductivity.superproductivity.WIDGET_DONE_LOCAL"
        const val ACTION_TOGGLE_HIDE_DONE = "com.superproductivity.superproductivity.WIDGET_TOGGLE_HIDE_DONE"
        const val EXTRA_TASK_ID = "WIDGET_TASK_ID"
        const val EXTRA_OPEN_APP = "WIDGET_OPEN_APP"
        const val EXTRA_TARGET_DONE = "WIDGET_TARGET_DONE"

        // PendingIntent request codes: Android uses these to distinguish cached PendingIntents
        // that share the same action/component. Without unique codes, the system returns the same
        // cached instance for every button, causing the wrong action to fire.
        private const val RC_MARK_DONE = 101
        private const val RC_TOGGLE_HIDE_DONE = 102
        private const val RC_SELECT_CONTEXT = 103
        private const val RC_OPEN_APP = 104

        fun notifyDataChanged(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, TaskListWidgetProvider::class.java)
            )
            appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_task_list)
            for (id in widgetIds) {
                updateWidget(context, appWidgetManager, id)
            }
        }


        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_task_list)

            val serviceIntent = Intent(context, TaskListWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_task_list, serviceIntent)
            views.setEmptyView(R.id.widget_task_list, R.id.widget_empty)

            val doneIntent = Intent(context, TaskListWidgetProvider::class.java).apply {
                action = ACTION_MARK_DONE
            }
            val donePendingIntent = PendingIntent.getBroadcast(
                context, RC_MARK_DONE, doneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_task_list, donePendingIntent)

            val openAppIntent = Intent(context, CapacitorMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, RC_OPEN_APP, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, openAppPendingIntent)

            val isHiding = WidgetSettings.isHideDone(context)
            views.setImageViewResource(
                R.id.widget_toggle_hide_done,
                if (isHiding) R.drawable.widget_hide_done else R.drawable.widget_show_done
            )
            val toggleIntent = Intent(context, TaskListWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_HIDE_DONE
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context, RC_TOGGLE_HIDE_DONE, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_toggle_hide_done, togglePendingIntent)

            // Setup select list action
            val selectIntent = Intent(context, WidgetListSelectActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val selectPendingIntent = PendingIntent.getActivity(
                context, RC_SELECT_CONTEXT, selectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_select_context, selectPendingIntent)

            // Header title is persisted by WidgetListSelectActivity when the user picks a context,
            // so we never need to re-parse widget_data JSON here.
            val currentId = WidgetSettings.getContextId(context)
            val storedTitle = WidgetSettings.getContextTitle(context)
            var headerTitle = storedTitle.ifEmpty { context.getString(R.string.widget_title) }
            if (currentId != "TODAY" && !WidgetSettings.isShowAllTime(context)) {
                headerTitle += context.getString(R.string.widget_header_suffix_today)
            }
            views.setTextViewText(R.id.widget_header, headerTitle)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

    }
}
