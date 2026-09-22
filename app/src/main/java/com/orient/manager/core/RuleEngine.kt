package com.orient.manager.core

import android.content.Context
import com.orient.manager.core.strategy.StrategyId
import com.orient.manager.core.strategy.StrategyManager
import com.orient.manager.pref.Prefs

data class Resolution(val mode: OrientationMode, val source: String)

object RuleEngine {

    fun resolve(context: Context, pkg: String?, activityClass: String?): Resolution {
        val page = PageRuleStore(context).match(activityClass)
        if (page != null) {
            return Resolution(page.mode, "页面 " + page.pattern)
        }
        val global = Prefs(context).mode
        if (pkg.isNullOrBlank()) return Resolution(global, "全局")
        val store = AppRuleStore(context)
        if (!store.perAppEnabled) return Resolution(global, "全局")
        val appMode = store.modeOf(pkg)
        return if (appMode != null) {
            Resolution(appMode, "应用 " + pkg)
        } else {
            Resolution(global, "全局")
        }
    }

    fun applyForForeground(context: Context, pkg: String?, activityClass: String?): Resolution? {
        if (!StrategyManager.active(context, StrategyId.ACCESSIBILITY)) {
            Logger.logThrottled("Rule", "skip", "无障碍方案已关闭，跳过规则应用", 5000L)
            return null
        }
        val resolution = resolve(context, pkg, activityClass)
        OrientationController.apply(context, resolution.mode, resolution.source)
        return resolution
    }
}
