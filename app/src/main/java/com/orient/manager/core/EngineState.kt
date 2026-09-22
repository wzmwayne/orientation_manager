package com.orient.manager.core

import android.content.Context
import com.orient.manager.pref.Prefs

object EngineState {

    @Volatile
    var mode: OrientationMode = OrientationMode.AUTO
        private set

    @Volatile
    var source: String = "-"
        private set

    fun initFromPrefs(context: Context) {
        if (source == "-") {
            mode = Prefs(context).mode
            source = "全局"
        }
    }

    fun update(newMode: OrientationMode, newSource: String) {
        mode = newMode
        source = newSource
    }

    fun describe(context: Context): String = context.getString(mode.labelRes) + " · " + source
}
