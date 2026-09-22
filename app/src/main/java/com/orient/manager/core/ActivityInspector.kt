package com.orient.manager.core

import android.content.ClipData
import android.content.ClipboardManager
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
import android.widget.Toast
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
    private val headerViews = HashMap<String, TextView>()
    private val history = ArrayDeque<String>()
    private val collapsed = HashMap<String, Boolean>()
    private var selectMode = false

    private var lastSnapshot: WindowSnapshot? = null

    fun isEnabled(context: Context): Boolean = Prefs(context).inspectorEnabled

    fun setEnabled(context: Context, enabled: Boolean) {
        Prefs(context).inspectorEnabled = enabled
        Logger.log("Inspector", "悬浮窗界面活动检测 -> " + enabled)
        if (enabled) {
            show(context)
        } else {
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
            selectMode = false
            render()
            Logger.log("Inspector", "检测悬浮窗已显示（拖动标题移动；内置复制按钮；可切换选择模式）")
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
        headerViews.clear()
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
        windowsView?.text = snapshot?.candidateEntries(null)?.take(MAX_WINDOW_LINES)?.joinToString("\n") ?: "-"
        candidatesView?.text = snapshot?.candidateValues(null)?.take(12)?.joinToString("\n") { "· " + it } ?: "-"
        nodesView?.text = snapshot?.nodes?.take(10)?.joinToString("\n") { "· " + it.label() } ?: "-"
        historyView?.text = history.drop(1).mapIndexed { i, s -> "  " + (i + 1) + ". " + s }
            .joinToString("\n").ifBlank { "-" }
        applyCollapse()
    }

    private fun applyCollapse() {
        val map = mapOf(
            "windows" to windowsView,
            "candidates" to candidatesView,
            "nodes" to nodesView,
            "history" to historyView,
        )
        for ((key, textView) in map) {
            val isCollapsed = collapsed[key] ?: false
            textView?.visibility = if (isCollapsed) View.GONE else View.VISIBLE
            headerViews[key]?.text = (if (isCollapsed) "▸ " else "▾ ") + headerTitle(key)
        }
    }

    private fun headerTitle(key: String): String = when (key) {
        "windows" -> "窗口 / 节点"
        "candidates" -> "可匹配候选"
        "nodes" -> "节点明细"
        "history" -> "最近不同页面"
        else -> key
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
            text = "⣿ 界面活动检测（拖动标题移动）"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 10f
        }
        currentView = selectable(context, 0xFF8FE3FF.toInt(), 11f)
        card.addView(title)
        card.addView(sectionHeader(context, "current", "当前", currentView!!, false))
        card.addView(currentView)

        windowsView = selectable(context, 0xFFCFCFCF.toInt(), 9f)
        card.addView(sectionHeader(context, "windows", "窗口 / 节点", windowsView!!, true))
        card.addView(windowsView)

        candidatesView = selectable(context, 0xFF9FE3A0.toInt(), 9f)
        card.addView(sectionHeader(context, "candidates", "可匹配候选", candidatesView!!, true))
        card.addView(candidatesView)

        nodesView = selectable(context, 0xFFE0C080.toInt(), 9f)
        card.addView(sectionHeader(context, "nodes", "节点明细", nodesView!!, true))
        card.addView(nodesView)

        historyView = selectable(context, 0xFFB0B0B0.toInt(), 9f)
        card.addView(sectionHeader(context, "history", "最近不同页面", historyView!!, true))
        card.addView(historyView)

        val row1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(context, 6), 0, 0)
        }
        row1.addView(button(context, "边框") {
            lastSnapshot?.let { NodeInspectorOverlay.showBoxes(context, it.nodes) }
                ?: Logger.warn("Inspector", "尚无快照，无法显示边框")
        })
        row1.addView(button(context, "拾取组件") {
            lastSnapshot?.let { NodeInspectorOverlay.showPicker(context, it.nodes) }
                ?: Logger.warn("Inspector", "尚无快照，无法拾取")
        })
        row1.addView(button(context, "关闭层") { NodeInspectorOverlay.hide() })

        val row2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)
        }
        row2.addView(button(context, context.getString(R.string.inspector_copy_all)) {
            copyText(context, allText(), context.getString(R.string.inspector_copy_all))
        })
        row2.addView(button(context, context.getString(R.string.inspector_select_mode)) {
            toggleSelectMode(context)
        })
        card.addView(row1)
        card.addView(row2)
        return card
    }

    private fun sectionHeader(
        context: Context,
        key: String,
        title: String,
        target: TextView,
        collapsible: Boolean,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 4), 0, 0)
        }
        val header = TextView(context).apply {
            text = "▾ " + title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 10f
            if (collapsible) {
                setOnClickListener { toggle(key) }
            }
        }
        headerViews[key] = header
        val copy = TextView(context).apply {
            text = "[复制]"
            setTextColor(0xFF80CBC4.toInt())
            textSize = 10f
            setPadding(dp(context, 8), 0, 0, 0)
            setOnClickListener { copyText(context, target.text.toString(), title) }
        }
        row.addView(header, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(copy)
        return row
    }

    private fun allText(): String = buildString {
        append(currentView?.text ?: "").append('\n')
        append("窗口 / 节点\n").append(windowsView?.text ?: "").append('\n')
        append("可匹配候选\n").append(candidatesView?.text ?: "").append('\n')
        append("节点明细\n").append(nodesView?.text ?: "").append('\n')
        append("最近不同页面\n").append(historyView?.text ?: "")
    }

    private fun copyText(context: Context, text: String, label: String) {
        if (text.isBlank() || text == "-") {
            Toast.makeText(context, "暂无可复制内容", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
            Toast.makeText(context, "已复制：" + label, Toast.LENGTH_SHORT).show()
            Logger.log("Inspector", "已复制[" + label + "]：" + text.replace('\n', ' ').take(200))
        } catch (t: Throwable) {
            Logger.error("Inspector", "复制失败：" + label, t)
        }
    }

    private fun toggleSelectMode(context: Context) {
        val p = params ?: return
        val manager = windowManager ?: return
        val card = view ?: return
        selectMode = !selectMode
        p.flags = if (selectMode) {
            p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        try {
            manager.updateViewLayout(card, p)
        } catch (t: Throwable) {
            Logger.error("Inspector", "切换选择模式失败", t)
        }
        val message = if (selectMode) {
            context.getString(R.string.inspector_select_on)
        } else {
            context.getString(R.string.inspector_select_off)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        Logger.log("Inspector", "选择模式 = " + selectMode + "（可聚焦窗口才能弹出系统复制菜单）")
    }

    private fun selectable(context: Context, color: Int, size: Float): TextView =
        TextView(context).apply {
            setTextColor(color)
            textSize = size
            setPadding(0, dp(context, 2), 0, dp(context, 2))
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
