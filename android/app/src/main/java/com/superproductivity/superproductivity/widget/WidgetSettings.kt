package com.superproductivity.superproductivity.widget

import android.content.Context
import android.content.SharedPreferences

object WidgetSettings {
    const val CONTEXT_ID_TODAY = "TODAY"

    private const val PREFS_NAME = "SuperProductivityWidgetSettings"
    private const val KEY_HIDE_DONE = "HIDE_DONE"
    private const val KEY_CONTEXT_ID = "CONTEXT_ID"
    private const val KEY_CONTEXT_TITLE = "CONTEXT_TITLE"
    private const val KEY_SHOW_ALL_TIME = "SHOW_ALL_TIME"

    private fun getPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun isHideDone(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_HIDE_DONE, false)

    @Synchronized
    fun toggleHideDone(context: Context): Boolean {
        val prefs = getPrefs(context)
        val next = !prefs.getBoolean(KEY_HIDE_DONE, false)
        prefs.edit().putBoolean(KEY_HIDE_DONE, next).commit()
        return next
    }

    @Synchronized
    fun isShowAllTime(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_SHOW_ALL_TIME, false)

    @Synchronized
    fun toggleShowAllTime(context: Context): Boolean {
        val prefs = getPrefs(context)
        val next = !prefs.getBoolean(KEY_SHOW_ALL_TIME, false)
        prefs.edit().putBoolean(KEY_SHOW_ALL_TIME, next).commit()
        return next
    }

    @Synchronized
    fun setShowAllTime(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_ALL_TIME, value).commit()
    }

    @Synchronized
    fun getContextId(context: Context): String =
        getPrefs(context).getString(KEY_CONTEXT_ID, CONTEXT_ID_TODAY) ?: CONTEXT_ID_TODAY

    @Synchronized
    fun setContextId(context: Context, id: String) {
        getPrefs(context).edit().putString(KEY_CONTEXT_ID, id).commit()
    }

    @Synchronized
    fun getContextTitle(context: Context): String =
        getPrefs(context).getString(KEY_CONTEXT_TITLE, "") ?: ""

    @Synchronized
    fun setContextTitle(context: Context, title: String) {
        getPrefs(context).edit().putString(KEY_CONTEXT_TITLE, title).commit()
    }
}
