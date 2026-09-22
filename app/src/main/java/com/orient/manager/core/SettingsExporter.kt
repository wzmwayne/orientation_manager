package com.orient.manager.core

import android.content.Context
import android.os.Build
import com.orient.manager.core.strategy.StrategyId
import com.orient.manager.core.strategy.StrategyManager
import com.orient.manager.pref.Prefs
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SettingsExporter {

    private const val FORMAT = 1

    fun build(context: Context): String {
        val prefs = Prefs(context)
        val appStore = AppRuleStore(context)
        val advanced = AdvancedRuleStore(context)

        val root = JSONObject()
        root.put("app", context.packageName)
        root.put("versionName", versionName(context))
        root.put("versionCode", versionCode(context))
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        root.put("format", FORMAT)

        val global = JSONObject()
        global.put("mode", prefs.mode.name)
        global.put("serviceEnabled", prefs.serviceEnabled)
        global.put("overrideEnabled", prefs.overrideEnabled)
        global.put("overrideIntervalMs", prefs.overrideIntervalMs)
        global.put("inspectorEnabled", prefs.inspectorEnabled)
        val strategies = JSONObject()
        for (id in StrategyId.entries) {
            strategies.put(id.key, StrategyManager.isEnabled(context, id))
        }
        global.put("strategies", strategies)
        root.put("global", global)

        val perApp = JSONObject()
        perApp.put("enabled", appStore.perAppEnabled)
        perApp.put("rules", appStore.snapshot())
        root.put("perApp", perApp)

        val adv = JSONObject()
        adv.put("enabled", advanced.enabled)
        adv.put("rules", advanced.exportJson())
        root.put("advancedRules", adv)

        return root.toString(2)
    }

    private fun versionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "-"
    } catch (t: Throwable) {
        "-"
    }

    private fun versionCode(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    } catch (t: Throwable) {
        -1L
    }
}
