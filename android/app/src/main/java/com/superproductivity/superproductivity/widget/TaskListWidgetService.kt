package com.superproductivity.superproductivity.widget

import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.superproductivity.superproductivity.App
import com.superproductivity.superproductivity.R
import org.json.JSONObject

class TaskListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TaskListRemoteViewsFactory(applicationContext)
    }
}

private data class WidgetTask(
    val id: String,
    val title: String,
    val isDone: Boolean,
)

private class TaskListRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: List<WidgetTask> = emptyList()
    private var pendingIds: Set<String> = emptySet()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        pendingIds = WidgetDoneQueue.peek(context)
        try {
            val json = (context.applicationContext as App).keyValStore.get("widget_data", "{}")
            val root = JSONObject(json)
            val tasksArray = root.optJSONArray("tasks") ?: return

            val loaded = mutableListOf<WidgetTask>()
            val limit = minOf(tasksArray.length(), MAX_WIDGET_TASKS)
            for (i in 0 until limit) {
                val task = tasksArray.getJSONObject(i)
                loaded.add(
                    WidgetTask(
                        id = task.getString("id"),
                        title = task.getString("title"),
                        isDone = task.optBoolean("isDone", false),
                    )
                )
            }
            tasks = loaded
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse widget data", e)
            tasks = emptyList()
        }
    }

    override fun onDestroy() {
        tasks = emptyList()
    }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_task_row)

        if (position >= tasks.size) {
            return rv
        }

        val task = tasks[position]
        if (task.isDone) {
            val spanned = SpannableString(task.title).apply {
                setSpan(StrikethroughSpan(), 0, length, 0)
            }
            rv.setTextViewText(R.id.widget_task_title, spanned)
            rv.setTextColor(R.id.widget_task_title, TITLE_COLOR_DONE)
            rv.setImageViewResource(R.id.widget_done_checkbox, R.drawable.widget_checkbox_on)
        } else {
            rv.setTextViewText(R.id.widget_task_title, task.title)
            rv.setTextColor(R.id.widget_task_title, TITLE_COLOR)
            rv.setImageViewResource(R.id.widget_done_checkbox, R.drawable.widget_checkbox_off)
        }

        // Pending sync indicator: visible while the done action sits in the
        // WidgetDoneQueue waiting for Angular to drain and write back.
        rv.setViewVisibility(
            R.id.widget_pending_sync,
            if (pendingIds.contains(task.id)) View.VISIBLE else View.GONE
        )

        val fillInIntent = Intent().apply {
            putExtra(TaskListWidgetProvider.EXTRA_TASK_ID, task.id)
            putExtra(TaskListWidgetProvider.EXTRA_TARGET_DONE, !task.isDone)
        }
        rv.setOnClickFillInIntent(R.id.widget_done_checkbox, fillInIntent)

        val openIntent = Intent().apply {
            putExtra(TaskListWidgetProvider.EXTRA_OPEN_APP, true)
        }
        rv.setOnClickFillInIntent(R.id.widget_task_title, openIntent)

        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long =
        tasks.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    companion object {
        private const val TAG = "TaskListWidget"
        private const val MAX_WIDGET_TASKS = 20
        private const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        private const val TITLE_COLOR_DONE = 0x80FFFFFF.toInt()
    }
}
