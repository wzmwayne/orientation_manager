package com.orient.manager.core

import android.content.Context
import com.orient.manager.R
import com.orient.manager.core.strategy.StrategyId
import com.orient.manager.core.strategy.StrategyManager
import com.orient.manager.pref.Prefs
import org.json.JSONArray
import org.json.JSONObject

object SettingsImporter {

    data class Preview(
        val describe: String,
        val mode: OrientationMode?,
        val perAppCount: Int,
        val advancedCount: Int,
    )

    private data class Parsed(
        val versionName: String,
        val exportedAt: String,
        val mode: OrientationMode?,
        val strategies: Map<StrategyId, Boolean>,
        val serviceEnabled: Boolean?,
        val overrideEnabled: Boolean?,
        val overrideIntervalMs: Long?,
        val inspectorEnabled: Boolean?,
        val perAppEnabled: Boolean?,
        val perAppRules: Map<String, OrientationMode>,
        val advancedEnabled: Boolean?,
        val advancedRules: List<AdvancedRule>,
    )

    fun preview(context: Context, raw: String): Preview = summarize(context, parse(raw))

    fun apply(context: Context, raw: String): Preview {
        val parsed = parse(raw)
        val prefs = Prefs(context)

        parsed.mode?.let { prefs.mode = it }
        parsed.serviceEnabled?.let { prefs.serviceEnabled = it }
        parsed.overrideEnabled?.let { prefs.overrideEnabled = it }
        parsed.overrideIntervalMs?.let { prefs.overrideIntervalMs = it }
        parsed.inspectorEnabled?.let { prefs.inspectorEnabled = it }
        parsed.strategies.forEach { (id, enabled) -> StrategyManager.setEnabled(context, id, enabled) }

        if (parsed.perAppEnabled != null || parsed.perAppRules.isNotEmpty()) {
            val store = AppRuleStore(context)
            parsed.perAppEnabled?.let { store.perAppEnabled = it }
            val rules = JSONObject()
            parsed.perAppRules.forEach { (pkg, mode) -> rules.put(pkg, mode.name) }
            store.replaceAll(rules)
        }

        if (parsed.advancedEnabled != null || parsed.advancedRules.isNotEmpty()) {
            val store = AdvancedRuleStore(context)
            parsed.advancedEnabled?.let { store.enabled = it }
            store.replaceAll(parsed.advancedRules)
        }

        return summarize(context, parsed)
    }

    private fun summarize(context: Context, parsed: Parsed): Preview {
        val lines = ArrayList<String>()
        val source = if (parsed.versionName.isBlank()) "-" else parsed.versionName
        lines.add("来源版本: " + source + "（导出于 " + parsed.exportedAt.ifBlank { "未知" } + "）")
        lines.add(
            "全局模式: " + (parsed.mode?.let { context.getString(it.labelRes) } ?: "保持当前"),
        )
        if (parsed.strategies.isNotEmpty()) {
            lines.add("通道开关: " + parsed.strategies.size + " 项")
        }
        lines.add("按应用配置: " + parsed.perAppRules.size + " 条")
        lines.add("高级规则: " + parsed.advancedRules.size + " 条")
        return Preview(
            lines.joinToString("\n"),
            parsed.mode,
            parsed.perAppRules.size,
            parsed.advancedRules.size,
        )
    }

    private fun parse(raw: String): Parsed {
        val root = try {
            JSONObject(raw)
        } catch (t: Throwable) {
            throw IllegalArgumentException("不是合法的 JSON 设置文件", t)
        }
        val hasSettings = root.has("global") || root.has("perApp") || root.has("advancedRules")
        if (!hasSettings) {
            throw IllegalArgumentException("不是本应用导出的设置文件")
        }

        val global = root.optJSONObject("global")
        val strategies = LinkedHashMap<StrategyId, Boolean>()
        var mode: OrientationMode? = null
        var serviceEnabled: Boolean? = null
        var overrideEnabled: Boolean? = null
        var overrideIntervalMs: Long? = null
        var inspectorEnabled: Boolean? = null
        if (global != null) {
            global.optString("mode").takeIf { it.isNotBlank() }?.let { name ->
                mode = OrientationMode.entries.firstOrNull { it.name == name }
            }
            serviceEnabled = global.optBooleanOrNull("serviceEnabled")
            overrideEnabled = global.optBooleanOrNull("overrideEnabled")
            if (global.has("overrideIntervalMs")) {
                overrideIntervalMs = global.optLong("overrideIntervalMs", 300L)
            }
            inspectorEnabled = global.optBooleanOrNull("inspectorEnabled")
            val items = global.optJSONObject("strategies")
            if (items != null) {
                for (id in StrategyId.entries) {
                    if (items.has(id.key)) {
                        strategies[id] = items.optBoolean(id.key, id.defaultEnabled)
                    }
                }
            }
        }

        val perApp = root.optJSONObject("perApp")
        val perAppRules = LinkedHashMap<String, OrientationMode>()
        var perAppEnabled: Boolean? = null
        if (perApp != null) {
            perAppEnabled = perApp.optBooleanOrNull("enabled")
            val rules = perApp.optJSONObject("rules")
            if (rules != null) {
                for (pkg in rules.keys()) {
                    val value = rules.optString(pkg)
                    OrientationMode.entries.firstOrNull { it.name == value }?.let {
                        perAppRules[pkg] = it
                    }
                }
            }
        }

        val advanced = root.optJSONObject("advancedRules")
        var advancedEnabled: Boolean? = null
        var advancedRules: List<AdvancedRule> = emptyList()
        if (advanced != null) {
            advancedEnabled = advanced.optBooleanOrNull("enabled")
            advancedRules = AdvancedRuleStore.parseRules(advanced.optJSONArray("rules") ?: JSONArray())
        }

        return Parsed(
            versionName = root.optString("versionName"),
            exportedAt = root.optString("exportedAt"),
            mode = mode,
            strategies = strategies,
            serviceEnabled = serviceEnabled,
            overrideEnabled = overrideEnabled,
            overrideIntervalMs = overrideIntervalMs,
            inspectorEnabled = inspectorEnabled,
            perAppEnabled = perAppEnabled,
            perAppRules = perAppRules,
            advancedEnabled = advancedEnabled,
            advancedRules = advancedRules,
        )
    }

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
        if (has(key)) optBoolean(key, false) else null
}
