package com.orient.manager.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.orient.manager.R
import com.orient.manager.core.ActivityInspector
import com.orient.manager.core.EngineHost
import com.orient.manager.core.Logger
import com.orient.manager.core.NodeInfo
import com.orient.manager.core.RuleEngine
import com.orient.manager.core.ToastNotifier
import com.orient.manager.core.UiState
import com.orient.manager.core.WindowInfo
import com.orient.manager.core.WindowSnapshot
import com.orient.manager.pref.Prefs
import java.util.concurrent.ConcurrentHashMap

class OrientationAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val activityByPackage = ConcurrentHashMap<String, String>()

    private var workerThread: HandlerThread? = null
    private var worker: Handler? = null
    private val queueLock = Any()
    private var pendingSource: String? = null
    private var pendingForce = false
    private var scheduled = false
    private var lastRunAt = 0L

    private var lastSignature: String? = null
    private var lastIdentity = "-"

    @Volatile
    private var imePackage: String? = null

    private var lastApplied: String? = null

    private val poller = object : Runnable {
        override fun run() {
            schedule("主动轮询", false)
            handler.postDelayed(this, POLL_MS)
        }
    }

    private val drainTask = Runnable { drain() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        instance = this
        imePackage = currentImePackage()
        val thread = HandlerThread("orient-a11y").also { it.start() }
        workerThread = thread
        worker = Handler(thread.looper)
        Logger.log(
            "A11y",
            "无障碍服务已连接：事件在后台线程检测（每 " + POLL_MS + "ms 轮询）；" +
                "活动类名取自 WINDOW_STATE_CHANGED 并按包缓存；本应用界面与输入法窗口一律跳过",
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
        if (
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }
        val pkg = event.packageName?.toString()
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val cls = event.className?.toString()
            if (!pkg.isNullOrBlank() && !isViewClass(cls)) {
                activityByPackage[pkg] = cls!!
                Logger.log("A11y", "缓存活动类名：" + pkg + " → " + cls)
            }
        }
        if (shouldSkip(pkg)) return
        Logger.logThrottled(
            "A11y",
            "event" + type,
            "事件识别[type=" + type + "] pkg=" + pkg + " cls=" + event.className + "（触发后台检测）",
            EVENT_LOG_INTERVAL_MS,
        )
        schedule("事件:type=" + type, false)
    }

    private fun shouldSkip(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return true
        if (pkg == packageName) return true
        if (IGNORED.contains(pkg)) return true
        return pkg == imePackage
    }

    private fun currentImePackage(): String? = try {
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() }
    } catch (t: Throwable) {
        null
    }

    fun requestActiveDetection(reason: String) {
        Logger.log("A11y", "收到主动检测请求：" + reason)
        schedule(reason, true)
    }

    private fun schedule(source: String, force: Boolean) {
        val current = worker ?: return
        val delay: Long
        synchronized(queueLock) {
            pendingSource = source
            if (force) pendingForce = true
            if (scheduled) return
            scheduled = true
            val elapsed = SystemClock.uptimeMillis() - lastRunAt
            delay = if (elapsed >= MIN_INTERVAL_MS) 0L else MIN_INTERVAL_MS - elapsed
        }
        current.postDelayed(drainTask, delay)
    }

    private fun drain() {
        var rounds = 0
        while (rounds < MAX_ROUNDS_PER_DRAIN) {
            var source: String? = null
            var force = false
            synchronized(queueLock) {
                source = pendingSource
                force = pendingForce
                pendingSource = null
                pendingForce = false
            }
            val requested = source ?: break
            rounds++
            lastRunAt = SystemClock.uptimeMillis()
            try {
                detect(requested, force)
            } catch (t: Throwable) {
                Logger.error("A11y", "检测异常(" + requested + ")", t)
            }
        }
        var next: String? = null
        var nextForce = false
        synchronized(queueLock) {
            scheduled = false
            next = pendingSource
            nextForce = pendingForce
        }
        val nextSource = next
        if (nextSource != null) schedule(nextSource, nextForce)
    }

    private fun detect(source: String, force: Boolean) {
        if (UiState.foreground) {
            Logger.logThrottled(
                "A11y",
                "selfUi",
                "本应用界面在前台，跳过窗口检测(" + source + ")",
                5000L,
            )
            return
        }
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
        val identity = pkg + " / " + (snapshot.activityClass ?: "未知")
        Logger.log("A11y", "界面变化[" + source + "] " + lastIdentity + "  →  " + identity)
        lastSignature = signature
        lastIdentity = identity
        lastPackage = pkg
        lastActivityClass = snapshot.activityClass

        handler.post { onSnapshot(snapshot) }
    }

    private fun onSnapshot(snapshot: WindowSnapshot) {
        if (!connected) return
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
        return WindowSnapshot(pkg, activity, infos, collectNodes(root))
    }

    private fun collectNodes(root: AccessibilityNodeInfo?): List<NodeInfo> {
        if (root == null) return emptyList()
        val result = ArrayList<NodeInfo>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var scanned = 0
        while (queue.isNotEmpty() && result.size < MAX_NODES && scanned < MAX_SCAN) {
            scanned++
            val (node, depth) = queue.removeFirst()
            try {
                if (node.isVisibleToUser) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0) {
                        result.add(
                            NodeInfo(
                                className = node.className?.toString(),
                                text = node.text?.toString(),
                                desc = node.contentDescription?.toString(),
                                viewId = node.viewIdResourceName,
                                pkg = node.packageName?.toString(),
                                bounds = rect,
                                clickable = node.isClickable,
                                depth = depth,
                            ),
                        )
                    }
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    queue.add(child to (depth + 1))
                }
            } catch (t: Throwable) {
                Logger.error("A11y", "节点遍历异常", t)
            }
        }
        return result
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

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        connected = false
        instance = null
        stopWorker()
        Logger.warn("A11y", "无障碍服务已解绑")
        EngineHost.stop("无障碍解绑")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connected = false
        instance = null
        stopWorker()
        Logger.warn("A11y", "无障碍服务已销毁")
        EngineHost.stop("无障碍销毁")
        super.onDestroy()
    }

    private fun stopWorker() {
        handler.removeCallbacks(poller)
        val thread = workerThread ?: return
        workerThread = null
        worker = null
        synchronized(queueLock) {
            pendingSource = null
            pendingForce = false
            scheduled = false
        }
        thread.quitSafely()
    }

    companion object {
        const val POLL_MS = 800L
        private const val POLL_LOG_INTERVAL_MS = 3000L
        private const val EVENT_LOG_INTERVAL_MS = 400L
        private const val MAX_NODES = 200
        private const val MAX_SCAN = 800
        private const val MIN_INTERVAL_MS = 60L
        private const val MAX_ROUNDS_PER_DRAIN = 3

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
