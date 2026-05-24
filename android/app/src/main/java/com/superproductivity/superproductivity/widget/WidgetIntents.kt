package com.superproductivity.superproductivity.widget

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Tracks the user's intended target state per task (id → desired isDone) for the
 * widget sync indicator. Independent of [WidgetDoneQueue], which is the atomic
 * drain log driving setDone/setUnDone dispatches.
 *
 * Why a separate store: the queue empties as soon as Angular drains it (often
 * within milliseconds when the app is alive). Multiple rapid taps would then
 * lose the pending visual indicator on all but the latest tap. This store
 * persists each intent until `widget_data` is verified to reflect it (via
 * [reconcile]), independent of when the queue gets drained.
 */
object WidgetIntents {
    private const val PREFS_NAME = "SuperProductivityWidgetIntents"
    private const val KEY_INTENTS = "WIDGET_INTENTS"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun add(context: Context, taskId: String, targetDone: Boolean) {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_INTENTS, null)
        val obj = if (json != null) JSONObject(json) else JSONObject()
        obj.put(taskId, targetDone)
        prefs.edit().putString(KEY_INTENTS, obj.toString()).commit()
    }

    /** Map of taskId → target isDone for all currently pending intents. */
    @Synchronized
    fun peek(context: Context): Map<String, Boolean> {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_INTENTS, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = HashMap<String, Boolean>(obj.length())
            val it = obj.keys()
            while (it.hasNext()) {
                val k = it.next()
                map[k] = obj.getBoolean(k)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Removes any intent that is either (a) already reflected in the snapshot —
     * the target state matches the current state — or (b) no longer relevant
     * because its task ID is absent from the snapshot (e.g. moved to archive).
     * Called from the factory once widget_data has been read.
     */
    @Synchronized
    fun reconcile(context: Context, currentDoneState: Map<String, Boolean>) {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_INTENTS, null) ?: return
        try {
            val obj = JSONObject(json)
            var changed = false
            val toRemove = mutableListOf<String>()
            val it = obj.keys()
            while (it.hasNext()) {
                val id = it.next()
                val target = obj.getBoolean(id)
                val current = currentDoneState[id]
                if (current == null || current == target) {
                    toRemove.add(id)
                    changed = true
                }
            }
            if (changed) {
                for (id in toRemove) obj.remove(id)
                prefs.edit().putString(KEY_INTENTS, obj.toString()).commit()
            }
        } catch (_: Exception) {
            // ignore — bad json will be overwritten on next add
        }
    }
}
