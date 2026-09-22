package com.orient.manager.core

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.orient.manager.R
import com.orient.manager.pref.Prefs

object ActivityInspector {

    private const val MAX_HISTORY = 4
    private const val MAX_WINDOW_LINES = 6

    private var view: View? = null
    private var windowManager: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var currentView: TextView? = null
    private var windowsView: TextView? = null
    private var candidatesView: TextView? = null
    private var nodesView: TextView? = null
    private var historyView: TextView? = null
    private val history = ArrayDeque<String>()
    private val collapsed = HashMap<String, Boolean>()

    private var lastSnapshot: WindowSnapshot? = null

    fun isEnabled(context: Context): Boolean = Prefs(context).inspectorEnabled

    fun setEnabled(context: Context, enabled: Boolean) {
        Prefs(context).inspectorEnabled = enabled
        Logger.log("Inspector", "悬浮窗界面活动检测 -> " + enabled)
        if (enabled) show(context) else {
            hide()
            NodeInspectorOverlay.hide()
        }
    }

    fun show(context: Context) {
        if (view != null) return
        if (!OverlayForceController.canDrawOverlays(context)) {
            Logger.warn("Inspector", "缺少悬浮窗权限，无法显示检测窗")
            return
        }
        val ctx = EngineHost.engineContext() ?: context.applicationContext
        val manager = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (manager == null) {
            Logger.error("Inspector", "无法获取 WindowManager")
            return
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(ctx, 8)
            y = dp(ctx, 80)
        }
        val card = buildCard(ctx)
        attachDrag(card, manager, p)
        try {
            manager.addView(card, p)
            view = card
            windowManager = manager
            params = p
            render()
            Logger.log("Inspector", "检测悬浮窗已显示（可拖动标题移动，文本可长按选中）")
        } catch (t: Throwable) {
            Logger.error("Inspector", "添加检测悬浮窗失败", t)
        }
    }

    fun hide() {
        val existing = view
        val manager = windowManager
        view = null
        currentView = null
        windowsView = null
        candidatesView = null
        nodesView = null
        historyView = null
        if (existing != null && manager != null) {
            try {
                manager.removeViewImmediate(existing)
                Logger.log("Inspector", "检测悬浮窗已移除")
            } catch (t: Throwable) {
                Logger.error("Inspector", "移除检测悬浮窗失败", t)
            }
        }
        windowManager = null
        params = null
    }

    fun update(snapshot: WindowSnapshot, ownerPackage: String) {
        lastSnapshot = snapshot
        val identity = (snapshot.pkg ?: "-") + " / " + (snapshot.activityClass ?: "未知活动") +
            "  [" + snapshot.windowsExcept(ownerPackage).size + " 窗口 / " + snapshot.nodes.size + " 节点]"
        if (history.firstOrNull() != identity) {
            history.remove(identity)
            history.addFirst(identity)
            while (history.size > MAX_HISTORY) history.removeLast()
        }
        render()
    }

    private fun toggle(key: String) {
        collapsed[key] = !(collapsed[key] ?: false)
        render()
    }

    private fun render() {
        val snapshot = lastSnapshot
        currentView?.text = "当前：" + (history.firstOrNull() ?: "-")
        windowsView?.text = section(
            "windows",
            "窗口 / 节点",
            snapshot?.candidateEntries(null)?.take(MAX_WINDOW_LINES)?.joinToString("\n") ?: "-",
        )
        candidatesView?.text = section(
            "candidates",
            "可匹配候选",
            snapshot?.candidateValues(null)?.take(12)?.joinToString("\n") { "· " + it } ?: "-",
        )
        nodesView?.text = section(
            "nodes",
            "节点明细",
            snapshot?.nodes?.take(10)?.joinToString("\n") { "· " + it.label() } ?: "-",
        )
        historyView?.text = section(
            "history",
            "最近不同页面",
            history.drop(1).mapIndexed { i, s -> "  " + (i + 1) + ". " + s }.joinToString("\n")
                .ifBlank { "-" },
        )
    }

    private fun section(key: String, title: String, content: String): String {
        val isCollapsed = collapsed[key] ?: false
        val header = (if (isCollapsed) "▸ " else "▾ ") + title
        return if (isCollapsed) header else header + "\n" + content
    }

    private fun buildCard(context: Context): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
            background = GradientDrawable().apply {
                setColor(0xE6000000.toInt())
                cornerRadius = dp(context, 10).toFloat()
                setStroke(dp(context, 1), 0x66FFFFFF)
            }
        }
        val title = TextView(context).apply {
            text = "⣿ 界面活动检测（拖动我移动）"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 10f
            setTag("handle")
        }
        currentView = selectable(context, 0xFF8FE3FF.toInt(), 11f)
        windowsView = selectable(context, 0xFFCFCFCF.toInt(), 9f)
        candidatesView = selectable(context, 0xFF9FE3A0.toInt(), 9f)
        nodesView = selectable(context, 0xFFE0C080.toInt(), 9f)
        historyView = selectable(context, 0xFFB0B0B0.toInt(), 9f)

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(context, 6), 0, 0)
        }
        buttons.addView(button(context, "边框") {
            lastSnapshot?.let { NodeInspectorOverlay.showBoxes(context, it.nodes) }
                ?: Logger.warn("Inspector", "尚无快照，无法显示边框")
        })
        buttons.addView(button(context, "拾取组件") {
            lastSnapshot?.let { NodeInspectorOverlay.showPicker(context, it.nodes) }
                ?: Logger.warn("Inspector", "尚无快照，无法拾取")
        })
        buttons.addView(button(context, "关闭层") { NodeInspectorOverlay.hide() })

        card.addView(title)
        card.addView(currentView)
        card.addView(windowsView)
        card.addView(candidatesView)
        card.addView(nodesView)
        card.addView(historyView)
        card.addView(buttons)

        listOf(windowsView, candidatesView, nodesView, historyView).forEachIndexed { index, textView ->
            textView?.setOnClickListener {
                toggle(listOf("windows", "candidates", "nodes", "history")[index])
            }
        }
        return card
    }

    private fun selectable(context: Context, color: Int, size: Float): TextView =
        TextView(context).apply {
            setTextColor(color)
            textSize = size
            setPadding(0, dp(context, 3), 0, 0)
            setTextIsSelectable(true)
        }

    private fun button(context: Context, label: String, action: () -> Unit): Button =
        Button(context).apply {
            text = label
            textSize = 10f
            setPadding(0, 0, 0, 0)
            setOnClickListener { action() }
        }

    private fun attachDrag(card: View, manager: WindowManager, p: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        (card as ViewGroup).getChildAt(0)?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = p.x
                    startY = p.y
                    downX = event.rawX
                    downY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    p.x = startX + (event.rawX - downX).toInt()
                    p.y = startY + (event.rawY - downY).toInt()
                    try {
                        manager.updateViewLayout(card, p)
                    } catch (t: Throwable) {
                        Logger.error("Inspector", "拖动更新失败", t)
                    }
                    true
                }

                else -> true
            }
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
