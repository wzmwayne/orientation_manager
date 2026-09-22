package com.orient.manager.core

import android.content.Context
import org.json.JSONObject

data class PageMatch(val pattern: String, val mode: OrientationMode)

class PageRuleStore(context: Context) {

    private val sp = context.getSharedPreferences("orient_page_rules", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(value) {
            sp.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    fun all(): Map<String, OrientationMode> {
        val obj = rules()
        val result = LinkedHashMap<String, OrientationMode>()
        for (key in obj.keys()) {
            val mode = OrientationMode.entries.firstOrNull { it.name == obj.optString(key) }
            if (mode != null) result[key] = mode
        }
        return result
    }

    fun set(pattern: String, mode: OrientationMode?) {
        val obj = rules()
        if (mode == null) obj.remove(pattern) else obj.put(pattern, mode.name)
        sp.edit().putString(KEY_RULES, obj.toString()).apply()
    }

    fun match(activityClass: String?): PageMatch? {
        if (!enabled) return null
        if (activityClass.isNullOrBlank()) return null
        val lower = activityClass.lowercase()
        var best: PageMatch? = null
        for ((pattern, mode) in all()) {
            if (pattern.isBlank()) continue
            if (!lower.contains(pattern.lowercase())) continue
            val current = best
            if (current == null || pattern.length > current.pattern.length) {
                best = PageMatch(pattern, mode)
            }
        }
        return best
    }

    private fun rules(): JSONObject = try {
        JSONObject(sp.getString(KEY_RULES, null) ?: "{}")
    } catch (t: Throwable) {
        Logger.error("PageRule", "解析页面规则失败", t)
        JSONObject()
    }

    private companion object {
        const val KEY_ENABLED = "page_rules_enabled"
        const val KEY_RULES = "page_rules"
    }
}
