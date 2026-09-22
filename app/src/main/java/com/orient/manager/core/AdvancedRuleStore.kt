package com.orient.manager.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RuleMatch(
    val index: Int,
    val rule: AdvancedRule,
    val matchedValues: List<String>,
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
        ensureMigrated()
        val raw = sp.getString(KEY_RULES, null) ?: "[]"
        synchronized(lock) {
            val cached = cache
            if (cached != null && raw == cacheRaw) return cached
        }
        val parsed = parse(raw)
        synchronized(lock) {
            cache = parsed
            cacheRaw = raw
        }
        return parsed
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

    fun move(index: Int, delta: Int): Boolean {
        val list = all().toMutableList()
        val target = index + delta
        if (index !in list.indices || target !in list.indices) return false
        val item = list.removeAt(index)
        list.add(target, item)
        save(list)
        return true
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
            val matchedValues = ArrayList<String>()
            var allMatched = true
            for (condition in rule.conditions) {
                val values = condition.field.valuesFor(snapshot, ownerPackage)
                val regex = compile(condition.pattern)
                if (regex == null) {
                    allMatched = false
                    break
                }
                val hit = values.firstOrNull { regex.containsMatchIn(it) }
                val satisfied = if (condition.negate) hit == null else hit != null
                if (!satisfied) {
                    allMatched = false
                    break
                }
                matchedValues.add(
                    condition.field.name + (if (condition.negate) "!不匹配" else "=" + hit),
                )
            }
            if (allMatched && rule.conditions.isNotEmpty()) {
                Logger.log(
                    "AdvRule",
                    "命中 #" + (index + 1) + " 条件 " + rule.conditions.size + " 个全部满足：" +
                        matchedValues.joinToString(" | ") + " → " + rule.mode.name,
                )
                return RuleMatch(index, rule, matchedValues)
            }
        }
        Logger.logThrottled(
            "AdvRule",
            "noHit",
            "高级规则未命中：规则 " + rules.size + " 条，候选 " +
                snapshot.candidateValues(ownerPackage).size + " 个，节点 " + snapshot.nodes.size,
            2000L,
        )
        return null
    }

    private fun parse(raw: String): List<AdvancedRule> {
        val array = try {
            JSONArray(raw)
        } catch (t: Throwable) {
            Logger.error("AdvRule", "解析高级规则失败", t)
            JSONArray()
        }
        return parseRules(array)
    }

    fun replaceAll(list: List<AdvancedRule>) = save(list)

    private fun compile(pattern: String): Regex? = try {
        Regex(pattern, RegexOption.IGNORE_CASE)
    } catch (t: Throwable) {
        Logger.logThrottled("AdvRule", "badRegex:" + pattern, "正则无效，已跳过：" + pattern, 5000L)
        null
    }

    fun exportJson(): JSONArray = toJson(all())

    private fun save(list: List<AdvancedRule>) {
        val array = toJson(list)
        val raw = array.toString()
        sp.edit().putString(KEY_RULES, raw).apply()
        synchronized(lock) {
            cache = list.toList()
            cacheRaw = raw
        }
    }

    private fun toJson(list: List<AdvancedRule>): JSONArray {
        val array = JSONArray()
        for (rule in list) {
            val obj = JSONObject()
            val condArray = JSONArray()
            for (condition in rule.conditions) {
                val c = JSONObject()
                c.put("field", condition.field.name)
                c.put("pattern", condition.pattern)
                c.put("negate", condition.negate)
                condArray.put(c)
            }
            obj.put("conditions", condArray)
            obj.put("mode", rule.mode.name)
            obj.put("name", rule.name)
            array.put(obj)
        }
        return array
    }

    private fun ensureMigrated() {
        synchronized(lock) {
            if (migrated) return
            migrated = true
        }
        val legacyRules = try {
            legacy.all()
        } catch (t: Throwable) {
            emptyMap<String, OrientationMode>()
        }
        if (legacyRules.isEmpty()) return
        val list = all().toMutableList()
        legacyRules.forEach { (pattern, mode) ->
            list.add(
                AdvancedRule(
                    listOf(RuleCondition(RuleField.ANY, Regex.escape(pattern))),
                    mode,
                ),
            )
        }
        save(list)
        Logger.log("AdvRule", "已从旧的页面规则迁移 " + legacyRules.size + " 条（全部字段 + 正则转义）")
    }

    companion object {
        private const val KEY_ENABLED = "advanced_rules_enabled"
        private const val KEY_RULES = "advanced_rules"

        fun parseRules(array: JSONArray): List<AdvancedRule> {
            val result = ArrayList<AdvancedRule>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val mode = OrientationMode.entries.firstOrNull { it.name == obj.optString("mode") }
                    ?: continue
                val conditions = ArrayList<RuleCondition>()
                val condArray = obj.optJSONArray("conditions")
                if (condArray != null) {
                    for (j in 0 until condArray.length()) {
                        val c = condArray.optJSONObject(j) ?: continue
                        val pattern = c.optString("pattern")
                        if (pattern.isBlank()) continue
                        val field = runCatching { RuleField.valueOf(c.optString("field")) }
                            .getOrDefault(RuleField.ANY)
                        conditions.add(RuleCondition(field, pattern, c.optBoolean("negate", false)))
                    }
                } else if (obj.optString("pattern").isNotBlank()) {
                    val field = runCatching { RuleField.valueOf(obj.optString("field")) }
                        .getOrDefault(RuleField.ANY)
                    conditions.add(RuleCondition(field, obj.optString("pattern")))
                }
                if (conditions.isEmpty()) continue
                result.add(AdvancedRule(conditions, mode, obj.optString("name")))
            }
            return result
        }

        private val lock = Any()
        private var cache: List<AdvancedRule>? = null
        private var cacheRaw: String? = null
        private var migrated = false
    }
}
