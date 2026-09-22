package com.orient.manager.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.orient.manager.R
import com.orient.manager.core.ActivityInspector
import com.orient.manager.core.EngineHost
import com.orient.manager.core.Logger
import com.orient.manager.core.RuleEngine
import com.orient.manager.core.ToastNotifier
import com.orient.manager.pref.Prefs

class OrientationAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val activityByPackage = HashMap<String, String>()
    private var currentPkg: String? = null
    private var currentActivity: String? = null
    private var lastApplied: String? = null

    private val poller = object : Runnable {
        override fun run() {
            detectActiveWindow("主动轮询")
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        instance = this
        Logger.log(
            "A11y",
            "无障碍服务已连接：事件监听 + 主动检测（每 " + POLL_MS + "ms）；" +
                "页面身份只认 WINDOW_STATE_CHANGED 的活动类名，视图类名仅记录",
        )
        EngineHost.start(this)
        ToastNotifier.show(
            this,
            getString(R.string.toast_service_started, getString(Prefs(this).mode.labelRes)),
        )
        handler.removeCallbacks(poller)
        handler.post(poller)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        val pkg = event.packageName?.toString()
        val cls = event.className?.toString()

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val isActivity = !isViewClass(cls)
            Logger.logThrottled(
                "A11y",
                "eventState",
                "事件识别[WINDOW_STATE_CHANGED] pkg=" + pkg + " cls=" + cls +
                    "（" + (if (isActivity) "活动类名" else "视图类名") + "）",
                EVENT_LOG_INTERVAL_MS,
            )
            if (!pkg.isNullOrBlank() && isActivity) {
                activityByPackage[pkg] = cls!!
                Logger.log("A11y", "缓存活动类名：" + pkg + " → " + cls)
            }
            if (!pkg.isNullOrBlank()) {
                handleIdentity("事件:WINDOW_STATE_CHANGED", pkg, if (isActivity) cls else activityByPackage[pkg])
            }
            return
        }

        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            Logger.logThrottled(
                "A11y",
                "eventContent",
                "事件识别[WINDOW_CONTENT_CHANGED] pkg=" + pkg + " cls=" + cls +
                    "（视图类名，不用于页面判定）",
                EVENT_LOG_INTERVAL_MS,
            )
            if (!pkg.isNullOrBlank() && pkg != currentPkg) {
                handleIdentity("事件:WINDOW_CONTENT_CHANGED", pkg, activityByPackage[pkg])
            }
            return
        }

        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            Logger.logThrottled("A11y", "eventWindows", "事件识别[WINDOWS_CHANGED]（无包名，触发主动检测）", EVENT_LOG_INTERVAL_MS)
            detectActiveWindow("事件:WINDOWS_CHANGED")
        }
    }

    fun requestActiveDetection(reason: String) {
        Logger.log("A11y", "收到主动检测请求：" + reason)
        detectActiveWindow(reason)
    }

    private fun detectActiveWindow(source: String) {
        try {
            val root = rootInActiveWindow
            val windowList = windows
            val pkg = root?.packageName?.toString() ?: windowList.lastOrNull()?.root?.packageName?.toString()
            val cached = pkg?.let { activityByPackage[it] }
            Logger.logThrottled(
                "A11y",
                "poll",
                "主动检测(" + source + ")：窗口数=" + windowList.size +
                    " 根视图=" + root?.className + " 前台包=" + pkg +
                    " 缓存活动=" + (cached ?: "无"),
                POLL_LOG_INTERVAL_MS,
            )
            if (pkg.isNullOrBlank()) {
                Logger.logThrottled(
                    "A11y",
                    "pollEmpty",
                    "主动检测未取到前台包名（检查 canRetrieveWindowContent / flagRetrieveInteractiveWindows）",
                    5000L,
                )
                return
            }
            handleIdentity(source, pkg, cached)
        } catch (t: Throwable) {
            Logger.error("A11y", "主动检测异常(" + source + ")", t)
        }
    }

    private fun handleIdentity(source: String, pkg: String, activityClass: String?) {
        if (pkg == packageName) {
            Logger.logThrottled("A11y", "self", "前台为本应用，跳过(" + source + ")", 5000L)
            return
        }
        if (IGNORED.contains(pkg)) {
            Logger.logThrottled("A11y", "ignored", "忽略系统界面 " + pkg, 5000L)
            return
        }
        if (pkg == currentPkg && activityClass == currentActivity) {
            Logger.logThrottled(
                "A11y",
                "same",
                "界面未变化(" + source + ")：" + pkg + " / " + (activityClass ?: "未知活动"),
                5000L,
            )
            return
        }
        Logger.log(
            "A11y",
            "界面切换[" + source + "] " + (currentPkg ?: "-") + "/" + (currentActivity ?: "-") +
                "  →  " + pkg + " / " + (activityClass ?: "未知活动"),
        )
        currentPkg = pkg
        currentActivity = activityClass
        lastPackage = pkg
        lastActivityClass = activityClass

        ActivityInspector.onIdentityChanged(pkg, activityClass)

        val resolution = RuleEngine.applyForForeground(this, pkg, activityClass) ?: return
        val key = resolution.source + "->" + resolution.mode.name
        if (key != lastApplied) {
            lastApplied = key
            Logger.log("A11y", "规则生效：" + resolution.source + " → " + resolution.mode.name)
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

    private fun isViewClass(cls: String?): Boolean {
        if (cls.isNullOrBlank()) return true
        return cls.startsWith("android.widget.") ||
            cls.startsWith("android.view.") ||
            cls.startsWith("android.webkit.") ||
            cls.startsWith("androidx.compose.ui.") ||
            cls.startsWith("androidx.recyclerview.") ||
            cls == "android.app.Dialog"
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        instance = null
        handler.removeCallbacks(poller)
        Logger.warn("A11y", "无障碍服务已解绑")
        EngineHost.stop("无障碍解绑")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connected = false
        instance = null
        handler.removeCallbacks(poller)
        Logger.warn("A11y", "无障碍服务已销毁")
        EngineHost.stop("无障碍销毁")
        super.onDestroy()
    }

    companion object {
        const val POLL_MS = 800L
        private const val POLL_LOG_INTERVAL_MS = 3000L
        private const val EVENT_LOG_INTERVAL_MS = 400L

        private val IGNORED = setOf("com.android.systemui")

        @Volatile
        var connected: Boolean = false
            private set

        @Volatile
        var instance: OrientationAccessibilityService? = null
            private set

        @Volatile
        var lastPackage: String? = null
            private set

        @Volatile
        var lastActivityClass: String? = null
            private set
    }
}
