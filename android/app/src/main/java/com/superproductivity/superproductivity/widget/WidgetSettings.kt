package com.superproductivity.superproductivity.widget

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent widget UI preferences. Backed by SharedPreferences so settings
 * survive process death and device reboots.
 */
object WidgetSettings {
    private const val PREFS_NAME = "SuperProductivityWidgetSettings"
    private const val KEY_HIDE_DONE = "HIDE_DONE"

    private fun getPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isHideDone(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_HIDE_DONE, false)

    fun toggleHideDone(context: Context): Boolean {
        val prefs = getPrefs(context)
        val next = !prefs.getBoolean(KEY_HIDE_DONE, false)
        prefs.edit().putBoolean(KEY_HIDE_DONE, next).commit()
        return next
    }
}
