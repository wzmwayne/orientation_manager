package com.orient.manager.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.orient.manager.R
import com.orient.manager.core.EngineHost
import com.orient.manager.core.Logger
import com.orient.manager.core.RuleEngine
import com.orient.manager.core.ToastNotifier
import com.orient.manager.pref.Prefs

class OrientationAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastPkg: String? = null
    private var lastClass: String? = null
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
            "无障碍服务已连接：启动事件监听 + 主动检测（每 " + POLL_MS + "ms），" +
                "canRetrieveWindowContent=" + (serviceInfo?.capabilities?.let { true } ?: true),
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
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }
        val pkg = event.packageName?.toString()
        val cls = event.className?.toString()
        Logger.logThrottled(
            "A11y",
            "event",
            "事件识别[" + eventTypeName(type) + "] pkg=" + pkg + " cls=" + cls,
            EVENT_LOG_INTERVAL_MS,
        )
        handleWindow("事件:" + eventTypeName(type), pkg, cls)
    }

    fun requestActiveDetection(reason: String) {
        Logger.log("A11y", "收到主动检测请求：" + reason)
        detectActiveWindow(reason)
    }

    private fun detectActiveWindow(source: String) {
        try {
            val root = rootInActiveWindow
            val windowList = windows
            val pkgRoot = root?.packageName?.toString()
            val clsRoot = root?.className?.toString()
            val top = windowList.lastOrNull()
            val pkgTop = top?.root?.packageName?.toString()
            val clsTop = top?.root?.className?.toString()
            Logger.logThrottled(
                "A11y",
                "poll",
                "主动检测(" + source + ")：窗口数=" + windowList.size +
                    " rootInActiveWindow=" + pkgRoot + "/" + clsRoot +
                    " 顶层窗口=" + pkgTop + "/" + clsTop,
                POLL_LOG_INTERVAL_MS,
            )
            val pkg = pkgRoot ?: pkgTop
            val cls = clsRoot ?: clsTop
            if (pkg.isNullOrBlank() && cls.isNullOrBlank()) {
                Logger.logThrottled(
                    "A11y",
                    "pollEmpty",
                    "主动检测未取到窗口信息：请确认无障碍配置已开启 canRetrieveWindowContent " +
                        "与 flagRetrieveInteractiveWindows（需重新开启无障碍服务生效）",
                    5000L,
                )
                return
            }
            handleWindow(source, pkg, cls)
        } catch (t: Throwable) {
            Logger.error("A11y", "主动检测异常(" + source + ")", t)
        }
    }

    private fun handleWindow(source: String, pkg: String?, cls: String?) {
        if (pkg.isNullOrBlank()) {
            Logger.logThrottled("A11y", "noPkg", "无法识别前台包名(" + source + ")", 5000L)
            return
        }
        if (pkg == packageName) {
            Logger.logThrottled("A11y", "self", "前台为本应用，跳过(" + source + ")", 5000L)
            return
        }
        if (IGNORED.contains(pkg)) {
            Logger.logThrottled("A11y", "ignored", "忽略系统界面 " + pkg, 5000L)
            return
        }
        if (pkg == lastPkg && cls == lastClass) {
            Logger.logThrottled(
                "A11y",
                "same",
                "界面未变化(" + source + ")：" + pkg + " / " + cls,
                5000L,
            )
            return
        }
        Logger.log(
            "A11y",
            "界面切换[" + source + "] " + (lastPkg ?: "-") + "/" + (lastClass ?: "-") +
                "  →  " + pkg + " / " + cls,
        )
        lastPkg = pkg
        lastClass = cls
        lastPackage = pkg
        lastActivityClass = cls

        val resolution = RuleEngine.applyForForeground(this, pkg, cls) ?: return
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

    private fun eventTypeName(type: Int): String = when (type) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "WINDOWS_CHANGED"
        else -> type.toString()
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
