package com.superproductivity.superproductivity.widget

import android.content.Context
import android.content.SharedPreferences

// Single-slot store: only the most recent tap survives if multiple arrive before the app opens.
object WidgetLastTaskTap {
    private const val PREFS_NAME = "SuperProductivityWidgetTaskTap"
    private const val KEY_TAP_TASK_ID = "TAP_TASK_ID"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun setTaskId(context: Context, taskId: String) {
        getPrefs(context).edit().putString(KEY_TAP_TASK_ID, taskId).apply()
    }

    @Synchronized
    fun getAndClear(context: Context): String? {
        val prefs = getPrefs(context)
        val data = prefs.getString(KEY_TAP_TASK_ID, null)
        prefs.edit().remove(KEY_TAP_TASK_ID).commit()
        return data
    }
}
