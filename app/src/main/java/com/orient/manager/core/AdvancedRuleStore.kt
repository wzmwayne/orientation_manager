package com.orient.manager.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RuleMatch(
    val index: Int,
    val rule: AdvancedRule,
    val matchedValue: String,
)

class AdvancedRuleStore(context: Context) {

    private val appContext = context.applicationContext
    private val sp = appContext.getSharedPreferences("orient_advanced_rules", Context.MODE_PRIVATE)
    private val legacy = PageRuleStore(appContext)

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(value) {
            sp.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    fun all(): List<AdvancedRule> {
        migrateIfNeeded()
        val array = try {
            JSONArray(sp.getString(KEY_RULES, null) ?: "[]")
        } catch (t: Throwable) {
            Logger.error("AdvRule", "解析高级规则失败", t)
            JSONArray()
        }
        val result = ArrayList<AdvancedRule>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val pattern = obj.optString("pattern")
            val field = runCatching { RuleField.valueOf(obj.optString("field")) }
                .getOrDefault(RuleField.ANY)
            val mode = OrientationMode.entries.firstOrNull { it.name == obj.optString("mode") }
                ?: continue
            if (pattern.isBlank()) continue
            result.add(AdvancedRule(pattern, field, mode))
        }
        return result
    }

    fun add(rule: AdvancedRule) {
        val list = all().toMutableList()
        list.add(rule)
        save(list)
    }

    fun update(index: Int, rule: AdvancedRule) {
        val list = all().toMutableList()
        if (index !in list.indices) return
        list[index] = rule
        save(list)
    }

    fun remove(index: Int) {
        val list = all().toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        save(list)
    }

    fun match(snapshot: WindowSnapshot, ownerPackage: String): RuleMatch? {
        if (!enabled) {
            Logger.logThrottled("AdvRule", "disabled", "高级规则已关闭", 5000L)
            return null
        }
        val rules = all()
        if (rules.isEmpty()) {
            Logger.logThrottled("AdvRule", "empty", "高级规则为空", 5000L)
            return null
        }
        for ((index, rule) in rules.withIndex()) {
            val values = rule.field.valuesFor(snapshot, ownerPackage)
            if (values.isEmpty()) continue
            val regex = compile(rule.pattern) ?: continue
            val hit = values.firstOrNull { regex.containsMatchIn(it) }
            if (hit != null) {
                Logger.log(
                    "AdvRule",
                    "命中 #" + (index + 1) + " 字段=" + rule.field.name +
                        " 正则=" + rule.pattern + " 值=" + hit + " → " + rule.mode.name,
                )
                return RuleMatch(index, rule, hit)
            }
        }
        Logger.logThrottled(
            "AdvRule",
            "noHit",
            "高级规则未命中：规则 " + rules.size + " 条，候选 " +
                snapshot.candidateValues(ownerPackage).size + " 个",
            2000L,
        )
        return null
    }

    private fun compile(pattern: String): Regex? = try {
        Regex(pattern, RegexOption.IGNORE_CASE)
    } catch (t: Throwable) {
        Logger.logThrottled("AdvRule", "badRegex:" + pattern, "正则无效，已跳过：" + pattern, 5000L)
        null
    }

    private fun save(list: List<AdvancedRule>) {
        val array = JSONArray()
        for (rule in list) {
            val obj = JSONObject()
            obj.put("pattern", rule.pattern)
            obj.put("field", rule.field.name)
            obj.put("mode", rule.mode.name)
            array.put(obj)
        }
        sp.edit().putString(KEY_RULES, array.toString()).apply()
    }

    private fun migrateIfNeeded() {
        if (sp.getBoolean(KEY_MIGRATED, false)) return
        sp.edit().putBoolean(KEY_MIGRATED, true).apply()
        val legacyRules = try {
            legacy.all()
        } catch (t: Throwable) {
            emptyMap<String, OrientationMode>()
        }
        if (legacyRules.isEmpty()) return
        val list = all().toMutableList()
        legacyRules.forEach { (pattern, mode) ->
            list.add(AdvancedRule(Regex.escape(pattern), RuleField.ANY, mode))
        }
        save(list)
        Logger.log("AdvRule", "已从旧的页面规则迁移 " + legacyRules.size + " 条为高级规则（全部字段 + 正则转义）")
    }

    private companion object {
        const val KEY_ENABLED = "advanced_rules_enabled"
        const val KEY_RULES = "advanced_rules"
        const val KEY_MIGRATED = "advanced_rules_migrated"
    }
}
