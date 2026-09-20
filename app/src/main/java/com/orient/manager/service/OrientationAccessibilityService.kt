package com.orient.manager.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.orient.manager.core.EngineHost
import com.orient.manager.core.Logger
import com.orient.manager.core.RuleEngine

class OrientationAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        Logger.log("A11y", "无障碍服务已连接")
        EngineHost.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || IGNORED.contains(pkg)) return
        if (pkg == lastPackage) return
        lastPackage = pkg
        Logger.logThrottled("A11y", "fg", "前台应用: " + pkg, 500L)
        RuleEngine.applyForForeground(this, pkg)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
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
    }
}
