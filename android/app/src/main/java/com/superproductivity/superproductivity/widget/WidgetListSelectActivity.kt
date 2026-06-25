package com.superproductivity.superproductivity.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.superproductivity.superproductivity.App
import com.superproductivity.superproductivity.R
import org.json.JSONObject

class WidgetListSelectActivity : AppCompatActivity() {

    private data class WidgetContextItem(
        val id: String,
        val title: String,
        val subtitle: String,
        val color: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_list_select)

        // Dismiss if clicking outside the card
        val root = findViewById<View>(R.id.widget_select_root)
        root.setOnClickListener { finish() }

        val cancelBtn = findViewById<Button>(R.id.widget_select_cancel)
        cancelBtn.setOnClickListener { finish() }

        val widgetDataJsonStr = (applicationContext as App).keyValStore.get("widget_data", "{}")
        val items = parseContextItems(widgetDataJsonStr)

        if (items.isEmpty()) {
            val listView = findViewById<ListView>(R.id.widget_list_select_items)
            listView.visibility = View.GONE
            val titleView = findViewById<TextView>(R.id.select_list_title)
            titleView.text = getString(R.string.widget_select_no_lists)
            return
        }

        val currentId = WidgetSettings.getContextId(this)
        val finalSelectedId = if (items.any { it.id == currentId }) currentId else WidgetSettings.CONTEXT_ID_TODAY
        val listView = findViewById<ListView>(R.id.widget_list_select_items)
        listView.adapter = WidgetContextAdapter(this, items, finalSelectedId)

        val showAllTimeSwitch = findViewById<SwitchCompat>(R.id.widget_select_show_all_time)
        val showAllTimeHint = findViewById<TextView>(R.id.widget_select_show_all_time_hint)

        fun updateHint(isChecked: Boolean) {
            if (isChecked) {
                showAllTimeHint.text = getString(R.string.widget_select_hint_all_time)
                showAllTimeHint.setTextColor(getColor(R.color.widget_task_title_done))
            } else {
                showAllTimeHint.text = getString(R.string.widget_select_hint_today)
                showAllTimeHint.setTextColor(getColor(R.color.widget_color_today))
            }
        }

        showAllTimeSwitch.isChecked = WidgetSettings.isShowAllTime(this)
        updateHint(showAllTimeSwitch.isChecked)

        showAllTimeSwitch.setOnCheckedChangeListener { _, isChecked ->
            WidgetSettings.setShowAllTime(this, isChecked)
            updateHint(isChecked)
            TaskListWidgetProvider.notifyDataChanged(this)
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = items[position]
            WidgetSettings.setContextId(this, selected.id)
            WidgetSettings.setContextTitle(this, selected.title)
            TaskListWidgetProvider.notifyDataChanged(this)
            finish()
        }
    }

    private fun parseContextItems(jsonStr: String): List<WidgetContextItem> {
        if (jsonStr.isEmpty() || jsonStr == "{}") return emptyList()
        try {
            val root = JSONObject(jsonStr)
            val list = mutableListOf<WidgetContextItem>()

            list.add(WidgetContextItem(
                id = WidgetSettings.CONTEXT_ID_TODAY,
                title = getString(R.string.widget_title),
                subtitle = getString(R.string.widget_context_type_today),
                color = getColor(R.color.widget_color_today),
            ))

            val projectsObj = root.optJSONObject("projects")
            if (projectsObj != null) {
                val projectItems = mutableListOf<WidgetContextItem>()
                val keys = projectsObj.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val proj = projectsObj.optJSONObject(id) ?: continue
                    val title = proj.optString("title", "")
                    if (title.isEmpty()) continue
                    val color = parseColorSafely(proj.opt("color") as? String)
                        ?: getColor(R.color.widget_default_project_color)
                    projectItems.add(WidgetContextItem(id, title, getString(R.string.widget_context_type_project), color))
                }
                list.addAll(projectItems.sortedBy { it.title.lowercase() })
            }

            val tagsObj = root.optJSONObject("tags")
            if (tagsObj != null) {
                val tagItems = mutableListOf<WidgetContextItem>()
                val keys = tagsObj.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val tag = tagsObj.optJSONObject(id) ?: continue
                    val title = tag.optString("title", "")
                    if (title.isEmpty()) continue
                    val color = parseColorSafely(tag.opt("color") as? String)
                        ?: getColor(R.color.widget_default_tag_color)
                    tagItems.add(WidgetContextItem(id, title, getString(R.string.widget_context_type_tag), color))
                }
                list.addAll(tagItems.sortedBy { it.title.lowercase() })
            }
            return list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse widget contexts", e)
            return emptyList()
        }
    }

    /**
     * Parses a CSS colour string (#rrggbb, rgb(r,g,b), or rgba(r,g,b,a)) into an Android colour int.
     * Returns null for unrecognised formats, empty input, or the literal string "null"
     * (which the Angular serialiser sometimes emits when a project has no colour set).
     */
    private fun parseColorSafely(colorStr: String?): Int? {
        if (colorStr.isNullOrEmpty() || colorStr == "null") return null
        val trimmed = colorStr.trim()
        if (trimmed.startsWith("#")) {
            return runCatching { Color.parseColor(trimmed) }.getOrNull()
        }
        if (trimmed.startsWith("rgba(")) {
            return try {
                val parts = trimmed.substring(5, trimmed.length - 1).split(",")
                if (parts.size >= 3) {
                    Color.rgb(parts[0].trim().toInt(), parts[1].trim().toInt(), parts[2].trim().toInt())
                } else null
            } catch (e: Exception) {
                null
            }
        }
        if (trimmed.startsWith("rgb(")) {
            return try {
                val parts = trimmed.substring(4, trimmed.length - 1).split(",")
                if (parts.size >= 3) {
                    Color.rgb(parts[0].trim().toInt(), parts[1].trim().toInt(), parts[2].trim().toInt())
                } else null
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    private class ViewHolder(
        val title: TextView,
        val subtitle: TextView,
        val colorDot: View,
        val checkmark: ImageView
    )

    private class WidgetContextAdapter(
        private val context: Context,
        private val items: List<WidgetContextItem>,
        private val selectedId: String?
    ) : BaseAdapter() {

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val holder: ViewHolder
            val view: View
            if (convertView == null) {
                view = LayoutInflater.from(context).inflate(R.layout.widget_select_list_row, parent, false)
                holder = ViewHolder(
                    title = view.findViewById(R.id.widget_select_title),
                    subtitle = view.findViewById(R.id.widget_select_subtitle),
                    colorDot = view.findViewById(R.id.widget_select_color_dot),
                    checkmark = view.findViewById(R.id.widget_select_checkmark)
                )
                view.tag = holder
            } else {
                view = convertView
                holder = convertView.tag as ViewHolder
            }

            val item = items[position]
            holder.title.text = item.title
            holder.subtitle.text = item.subtitle

            // Reuse existing GradientDrawable on recycled rows; create only for new views.
            val dotDrawable = (holder.colorDot.background as? GradientDrawable)
                ?: GradientDrawable().apply { shape = GradientDrawable.OVAL }
            dotDrawable.setColor(item.color)
            holder.colorDot.background = dotDrawable

            holder.checkmark.visibility = if (item.id == selectedId) View.VISIBLE else View.GONE

            return view
        }
    }

    companion object {
        private const val TAG = "WidgetListSelect"
    }
}
