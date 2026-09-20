package com.orient.manager.core

import android.content.Context
import org.json.JSONObject

class AppRuleStore(context: Context) {

    private val sp = context.getSharedPreferences("orient_app_rules", Context.MODE_PRIVATE)

    var perAppEnabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(value) {
            sp.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    fun modeOf(pkg: String): OrientationMode? {
        val name = rules().optString(pkg, null) ?: return null
        return OrientationMode.entries.firstOrNull { it.name == name }
    }

    fun set(pkg: String, mode: OrientationMode?) {
        val obj = rules()
        if (mode == null) obj.remove(pkg) else obj.put(pkg, mode.name)
        sp.edit().putString(KEY_RULES, obj.toString()).apply()
    }

    private fun rules(): JSONObject = try {
        JSONObject(sp.getString(KEY_RULES, null) ?: "{}")
    } catch (t: Throwable) {
        JSONObject()
    }

    private companion object {
        const val KEY_ENABLED = "per_app_enabled"
        const val KEY_RULES = "app_rules"
    }
}
