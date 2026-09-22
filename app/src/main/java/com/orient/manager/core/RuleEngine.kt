package com.orient.manager.core

import android.content.Context
import com.orient.manager.core.strategy.StrategyId
import com.orient.manager.core.strategy.StrategyManager
import com.orient.manager.pref.Prefs

data class Resolution(val mode: OrientationMode, val source: String)

object RuleEngine {

    fun resolve(context: Context, snapshot: WindowSnapshot): Resolution {
        val ruleMatch = AdvancedRuleStore(context).match(snapshot, context.packageName)
        if (ruleMatch != null) {
            val source = "高级规则#" + (ruleMatch.index + 1) + " " + ruleMatch.rule.summary()
            Logger.log(
                "Rule",
                "解析：包=" + snapshot.pkg + " 活动=" + snapshot.activityClass +
                    " → 命中 " + source + "，模式=" + ruleMatch.rule.mode.name,
            )
            return Resolution(ruleMatch.rule.mode, source)
        }
        val global = Prefs(context).mode
        val pkg = snapshot.pkg
        if (pkg.isNullOrBlank()) {
            Logger.log("Rule", "解析：无包名 → 全局模式 " + global.name)
            return Resolution(global, "全局")
        }
        val store = AppRuleStore(context)
        if (!store.perAppEnabled) {
            Logger.log("Rule", "解析：按应用规则已关闭，pkg=" + pkg + " → 全局 " + global.name)
            return Resolution(global, "全局")
        }
        val appMode = store.modeOf(pkg)
        return if (appMode != null) {
            Logger.log("Rule", "解析：pkg=" + pkg + " → 命中应用规则，模式=" + appMode.name)
            Resolution(appMode, "应用 " + pkg)
        } else {
            Logger.log("Rule", "解析：pkg=" + pkg + " 无规则 → 全局 " + global.name)
            Resolution(global, "全局")
        }
    }

    fun applyForForeground(context: Context, snapshot: WindowSnapshot): Resolution? {
        if (!StrategyManager.active(context, StrategyId.ACCESSIBILITY)) {
            Logger.logThrottled("Rule", "skip", "无障碍方案已关闭，跳过规则应用", 5000L)
            return null
        }
        val resolution = resolve(context, snapshot)
        OrientationController.apply(context, resolution.mode, resolution.source)
        return resolution
    }
}
