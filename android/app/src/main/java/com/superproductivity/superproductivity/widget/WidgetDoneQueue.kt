package com.superproductivity.superproductivity.widget

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * SharedPreferences-backed queue for persisting widget done/undone toggles.
 * Each entry is {"id": String, "isDone": Boolean} — `isDone` reflects the
 * user's intent at tap-time (the desired new state), not the prior state.
 */
object WidgetDoneQueue {
    private const val PREFS_NAME = "SuperProductivityWidgetDone"
    private const val KEY_DONE_TASKS = "WIDGET_DONE_TASK_IDS"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun add(context: Context, taskId: String, isDone: Boolean) {
        val prefs = getPrefs(context)
        val existing = prefs.getString(KEY_DONE_TASKS, null)
        val array = if (existing != null) JSONArray(existing) else JSONArray()
        array.put(JSONObject().put("id", taskId).put("isDone", isDone))
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

}
