package com.orient.manager.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.orient.manager.R
import com.orient.manager.core.EngineHost
import com.orient.manager.core.Logger
import com.orient.manager.core.RuleEngine
import com.orient.manager.core.ToastNotifier
import com.orient.manager.pref.Prefs

class OrientationAccessibilityService : AccessibilityService() {

    private var lastPkg: String? = null
    private var lastClass: String? = null
    private var lastApplied: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        Logger.log("A11y", "无障碍服务已连接")
        EngineHost.start(this)
        val mode = Prefs(this).mode
        ToastNotifier.show(
            this,
            getString(R.string.toast_service_started, getString(mode.labelRes)),
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || IGNORED.contains(pkg)) return
        val activityClass = event.className?.toString()
        if (pkg == lastPkg && activityClass == lastClass) return
        lastPkg = pkg
        lastClass = activityClass
        lastPackage = pkg
        lastActivityClass = activityClass
        Logger.log("A11y", "前台页面: " + pkg + " / " + activityClass)

        val resolution = RuleEngine.applyForForeground(this, pkg, activityClass) ?: return
        val key = resolution.source + "->" + resolution.mode.name
        if (key != lastApplied) {
            lastApplied = key
            ToastNotifier.show(
                this,
                getString(
                    R.string.toast_rule_changed,
                    resolution.source,
                    getString(resolution.mode.labelRes),
                ),
            )
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        Logger.warn("A11y", "无障碍服务已解绑")
        EngineHost.stop("无障碍解绑")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connected = false
        Logger.warn("A11y", "无障碍服务已销毁")
        EngineHost.stop("无障碍销毁")
        super.onDestroy()
    }

    companion object {
        private val IGNORED = setOf("com.android.systemui")

        @Volatile
        var connected: Boolean = false
            private set

        @Volatile
        var lastPackage: String? = null
            private set

        @Volatile
        var lastActivityClass: String? = null
            private set
    }
}
