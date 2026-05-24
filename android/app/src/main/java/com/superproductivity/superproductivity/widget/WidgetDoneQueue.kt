package com.superproductivity.superproductivity.widget

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * SharedPreferences-backed queue for persisting "Done" task IDs from widget checkbox actions.
 * Accumulates task IDs (multiple done presses before app opens) as a JSON array.
 */
object WidgetDoneQueue {
    private const val PREFS_NAME = "SuperProductivityWidgetDone"
    private const val KEY_DONE_TASKS = "WIDGET_DONE_TASK_IDS"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun addTaskId(context: Context, taskId: String) {
        val prefs = getPrefs(context)
        val existing = prefs.getString(KEY_DONE_TASKS, null)
        val array = if (existing != null) JSONArray(existing) else JSONArray()
        array.put(taskId)
        prefs.edit().putString(KEY_DONE_TASKS, array.toString()).commit()
    }

    @Synchronized
    fun getAndClear(context: Context): String? {
        val prefs = getPrefs(context)
        val data = prefs.getString(KEY_DONE_TASKS, null)
        if (data != null) {
            prefs.edit().remove(KEY_DONE_TASKS).commit()
        }
        return data
    }

    /**
     * Non-clearing read of currently queued task IDs. Used by the widget to render
     * a "pending sync" indicator on rows whose done action has not yet been
     * processed by Angular. The actual clear happens via [getAndClear] during the
     * drain flow.
     */
    @Synchronized
    fun peek(context: Context): Set<String> {
        val prefs = getPrefs(context)
        val data = prefs.getString(KEY_DONE_TASKS, null) ?: return emptySet()
        return try {
            val array = JSONArray(data)
            val ids = HashSet<String>(array.length())
            for (i in 0 until array.length()) {
                ids.add(array.getString(i))
            }
            ids
        } catch (_: Exception) {
            emptySet()
        }
    }
}
