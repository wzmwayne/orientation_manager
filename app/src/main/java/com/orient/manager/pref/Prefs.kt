package com.orient.manager.pref

import android.content.Context
import com.orient.manager.core.OrientationMode

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("orient_prefs", Context.MODE_PRIVATE)

    var mode: OrientationMode
        get() = try {
            OrientationMode.valueOf(sp.getString(KEY_MODE, null) ?: OrientationMode.AUTO.name)
        } catch (t: Throwable) {
            OrientationMode.AUTO
        }
        set(value) {
            sp.edit().putString(KEY_MODE, value.name).apply()
        }

    var serviceEnabled: Boolean
        get() = sp.getBoolean(KEY_SERVICE, true)
        set(value) {
            sp.edit().putBoolean(KEY_SERVICE, value).apply()
        }

    var overrideEnabled: Boolean
        get() = sp.getBoolean(KEY_OVERRIDE, false)
        set(value) {
            sp.edit().putBoolean(KEY_OVERRIDE, value).apply()
        }

    var overrideIntervalMs: Long
        get() = sp.getLong(KEY_OVERRIDE_INTERVAL, DEFAULT_OVERRIDE_INTERVAL)
        set(value) {
            sp.edit().putLong(KEY_OVERRIDE_INTERVAL, value).apply()
        }

    var shellGranted: Boolean
        get() = sp.getBoolean(KEY_SHELL_GRANTED, false)
        set(value) {
            sp.edit().putBoolean(KEY_SHELL_GRANTED, value).apply()
        }

    fun strategyEnabled(id: String, default: Boolean): Boolean =
        sp.getBoolean(KEY_STRATEGY_PREFIX + id, default)

    fun setStrategyEnabled(id: String, enabled: Boolean) {
        sp.edit().putBoolean(KEY_STRATEGY_PREFIX + id, enabled).apply()
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_SERVICE = "service_enabled"
        const val KEY_OVERRIDE = "override_enabled"
        const val KEY_OVERRIDE_INTERVAL = "override_interval_ms"
        const val KEY_SHELL_GRANTED = "shell_granted"
        const val KEY_STRATEGY_PREFIX = "strategy_"
        const val DEFAULT_OVERRIDE_INTERVAL = 300L
    }
}
