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
import com.orient.manager.core.WindowInfo
import com.orient.manager.core.WindowSnapshot
import com.orient.manager.pref.Prefs

class OrientationAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val activityByPackage = HashMap<String, String>()
    private var lastSignature: String? = null
    private var lastApplied: String? = null

    private val poller = object : Runnable {
        override fun run() {
            refreshIdentity("主动轮询")
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
                "活动类名取自 WINDOW_STATE_CHANGED 并按包缓存；候选=包名/活动/窗口类名/窗口标题",
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
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString()
                val cls = event.className?.toString()
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
                refreshIdentity("事件:WINDOW_STATE_CHANGED")
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                Logger.logThrottled(
                    "A11y",
                    "eventContent",
                    "事件识别[WINDOW_CONTENT_CHANGED] pkg=" + event.packageName +
                        " cls=" + event.className + "（视图类名，仅用于触发检测）",
                    EVENT_LOG_INTERVAL_MS,
                )
                refreshIdentity("事件:WINDOW_CONTENT_CHANGED")
            }

            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                Logger.logThrottled(
                    "A11y",
                    "eventWindows",
                    "事件识别[WINDOWS_CHANGED]（触发主动检测）",
                    EVENT_LOG_INTERVAL_MS,
                )
                refreshIdentity("事件:WINDOWS_CHANGED")
            }
        }
    }

    fun requestActiveDetection(reason: String) {
        Logger.log("A11y", "收到主动检测请求：" + reason)
        refreshIdentity(reason, force = true)
    }

    private fun buildSnapshot(): WindowSnapshot {
        val root = rootInActiveWindow
        val windowList = windows
        val pkg = root?.packageName?.toString()
            ?: windowList.lastOrNull()?.root?.packageName?.toString()
        val activity = pkg?.let { activityByPackage[it] }
        val infos = ArrayList<WindowInfo>(windowList.size)
        for (window in windowList) {
            infos.add(
                WindowInfo(
                    id = window.id,
                    type = window.type,
                    layer = window.layer,
                    pkg = window.root?.packageName?.toString(),
                    rootClass = window.root?.className?.toString(),
                    title = try {
                        window.title?.toString()
                    } catch (t: Throwable) {
                        null
                    },
                    focused = window.isFocused,
                    active = window.isActive,
                ),
            )
        }
        return WindowSnapshot(pkg, activity, infos)
    }

    private fun refreshIdentity(source: String, force: Boolean = false) {
        val snapshot = try {
            buildSnapshot()
        } catch (t: Throwable) {
            Logger.error("A11y", "构建窗口快照异常(" + source + ")", t)
            return
        }
        Logger.logThrottled(
            "A11y",
            "snapshot",
            "快照(" + source + ")：前台包=" + snapshot.pkg +
                " 活动=" + (snapshot.activityClass ?: "未知") +
                " 窗口=" + snapshot.windows.size +
                " 候选=" + snapshot.candidateValues(packageName).size,
            POLL_LOG_INTERVAL_MS,
        )

        val pkg = snapshot.pkg
        if (pkg.isNullOrBlank()) {
            Logger.logThrottled(
                "A11y",
                "noPkg",
                "未取到前台包名(" + source + ")（检查 canRetrieveWindowContent / flagRetrieveInteractiveWindows）",
                5000L,
            )
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

        val signature = snapshot.signature(packageName)
        if (!force && signature == lastSignature) {
            Logger.logThrottled(
                "A11y",
                "same",
                "界面未变化(" + source + ")：" + pkg + " / " + (snapshot.activityClass ?: "未知"),
                5000L,
            )
            return
        }
        Logger.log(
            "A11y",
            "界面变化[" + source + "] " + (lastSignature ?: "-") + "  →  " + signature,
        )
        lastSignature = signature
        lastPackage = pkg
        lastActivityClass = snapshot.activityClass

        ActivityInspector.update(snapshot, packageName)

        val resolution = RuleEngine.applyForForeground(this, snapshot) ?: return
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
