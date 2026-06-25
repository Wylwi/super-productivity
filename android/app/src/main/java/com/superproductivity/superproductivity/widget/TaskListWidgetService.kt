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
    // Optimistic done-state map: id → isDone. Snapshotted once per data-set
    // change so getViewAt does not hit SharedPreferences on every row.
    private var pendingIntents: Map<String, Boolean> = emptyMap()
    // Set derived from pendingIntents for O(1) containment checks in getViewAt.
    private var pendingIds: Set<String> = emptySet()
    // Stable numeric IDs for each task string ID, used by getItemId.
    // Retains previously seen IDs so animations survive data-set refreshes.
    private val stableIdMap = mutableMapOf<String, Long>()
    private var nextStableId = 1L

    override fun onCreate() {}

    override fun onDataSetChanged() {
        try {
            val json = (context.applicationContext as App).keyValStore.get("widget_data", "{}")
            val root = JSONObject(json)
            val currentId = WidgetSettings.getContextId(context)
            val isAllTime = WidgetSettings.isShowAllTime(context)
            val tasksArray = root.optJSONArray("tasks")
            val all = mutableListOf<WidgetTask>()

            if (tasksArray != null) {
                for (i in 0 until tasksArray.length()) {
                    val task = tasksArray.getJSONObject(i)
                    val projId = task.opt("projectId") as? String
                    val tagIdsArray = task.optJSONArray("tagIds")
                    val isToday = task.optBoolean("isToday", false)

                    val hasTag = tagIdsArray != null && (0 until tagIdsArray.length())
                        .any { tagIdsArray.optString(it) == currentId }

                    // A task is included when it matches the selected context AND
                    // the time scope (all-time or today-only).
                    val matchesContext = currentId == "TODAY" || projId == currentId || hasTag
                    val shouldAdd = matchesContext && (isAllTime || isToday)

                    if (shouldAdd) {
                        all.add(
                            WidgetTask(
                                id = task.getString("id"),
                                title = task.getString("title"),
                                isDone = task.optBoolean("isDone", false),
                            )
                        )
                    }
                }
            }

            // Limit the final tasks shown to MAX_WIDGET_TASKS.
            val limited = if (all.size > MAX_WIDGET_TASKS) all.take(MAX_WIDGET_TASKS) else all

            // Reconcile pending state against the current snapshot before applying
            // the hide-done filter, so optimistic ticks clear for hidden rows too.
            WidgetIntents.reconcile(context, limited.associate { it.id to it.isDone })
            pendingIntents = WidgetIntents.peek(context)
            pendingIds = pendingIntents.keys

            // Assign stable Long IDs for any newly seen task IDs.
            limited.forEach { task ->
                stableIdMap.getOrPut(task.id) { nextStableId++ }
            }

            val hideDone = WidgetSettings.isHideDone(context)
            tasks = if (hideDone) limited.filterNot { it.isDone } else limited
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse widget data", e)
            tasks = emptyList()
            pendingIntents = WidgetIntents.peek(context)
            pendingIds = pendingIntents.keys
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
        val isDone = pendingIntents[task.id] ?: task.isDone

        if (isDone) {
            val spanned = SpannableString(task.title).apply {
                setSpan(StrikethroughSpan(), 0, length, 0)
            }
            rv.setTextViewText(R.id.widget_task_title, spanned)
            rv.setTextColor(R.id.widget_task_title, context.getColor(R.color.widget_task_title_done))
            rv.setImageViewResource(R.id.widget_done_checkbox, R.drawable.widget_checkbox_on)
        } else {
            rv.setTextViewText(R.id.widget_task_title, task.title)
            rv.setTextColor(R.id.widget_task_title, context.getColor(R.color.widget_task_title))
            rv.setImageViewResource(R.id.widget_done_checkbox, R.drawable.widget_checkbox_off)
        }

        rv.setViewVisibility(
            R.id.widget_pending_sync,
            if (pendingIds.contains(task.id)) View.VISIBLE else View.GONE
        )

        val fillInIntent = Intent().apply {
            putExtra(TaskListWidgetProvider.EXTRA_TASK_ID, task.id)
            putExtra(TaskListWidgetProvider.EXTRA_TARGET_DONE, !isDone)
        }
        rv.setOnClickFillInIntent(R.id.widget_done_checkbox, fillInIntent)

        val openIntent = Intent().apply {
            putExtra(TaskListWidgetProvider.EXTRA_OPEN_APP, true)
            putExtra(TaskListWidgetProvider.EXTRA_TASK_ID, task.id)
        }
        rv.setOnClickFillInIntent(R.id.widget_task_row_root, openIntent)
        rv.setOnClickFillInIntent(R.id.widget_task_title, openIntent)

        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        tasks.getOrNull(position)?.let { stableIdMap[it.id] } ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    companion object {
        private const val TAG = "TaskListWidget"
        private const val MAX_WIDGET_TASKS = 20
    }
}
