package com.orient.manager.core

import android.content.Context
import com.orient.manager.core.strategy.StrategyId
import com.orient.manager.core.strategy.StrategyManager
import com.orient.manager.pref.Prefs

object RuleEngine {

    fun resolve(context: Context, pkg: String?): OrientationMode {
        val global = Prefs(context).mode
        if (pkg == null) return global
        val store = AppRuleStore(context)
        if (!store.perAppEnabled) return global
        return store.modeOf(pkg) ?: global
    }

    fun applyForForeground(context: Context, pkg: String?) {
        if (!StrategyManager.active(context, StrategyId.ACCESSIBILITY)) return
        OrientationController.apply(context, resolve(context, pkg))
    }
}
