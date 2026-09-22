package com.orient.manager.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.orient.manager.R
import com.orient.manager.pref.Prefs

object ActivityInspector {

    private const val MAX_HISTORY = 4
    private const val MAX_WINDOW_LINES = 8
    private const val TAP_SLOP_DP = 8

    private var view: View? = null
    private var windowManager: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var currentView: TextView? = null
    private var windowsView: TextView? = null
    private var candidatesView: TextView? = null
    private var historyView: TextView? = null
    private val history = ArrayDeque<String>()

    private var lastCandidates: List<String> = emptyList()
    private var lastEntries: List<String> = emptyList()

    fun isEnabled(context: Context): Boolean = Prefs(context).inspectorEnabled

    fun setEnabled(context: Context, enabled: Boolean) {
        Prefs(context).inspectorEnabled = enabled
        Logger.log("Inspector", "悬浮窗界面活动检测 -> " + enabled)
        if (enabled) show(context) else hide()
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
        attachTouch(card, manager, p)
        try {
            manager.addView(card, p)
            view = card
            windowManager = manager
            params = p
            render()
            Logger.log("Inspector", "检测悬浮窗已显示（type=2038，可拖动，点击复制）")
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
        lastCandidates = snapshot.candidateValues(ownerPackage)
        lastEntries = snapshot.candidateEntries(ownerPackage)
        val identity = (snapshot.pkg ?: "-") + " / " + (snapshot.activityClass ?: "未知活动") +
            "  [" + snapshot.windowsExcept(ownerPackage).size + " 窗口]"
        if (history.firstOrNull() != identity) {
            history.remove(identity)
            history.addFirst(identity)
            while (history.size > MAX_HISTORY) history.removeLast()
        }
        render()
    }

    private fun render() {
        val currentView = this.currentView ?: return
        val windowsView = this.windowsView ?: return
        val candidatesView = this.candidatesView ?: return
        val historyView = this.historyView ?: return

        currentView.text = "当前：" + (history.firstOrNull() ?: "-")
        windowsView.text = if (lastEntries.isEmpty()) {
            "窗口：-"
        } else {
            lastEntries.take(MAX_WINDOW_LINES).joinToString("\n") +
                if (lastEntries.size > MAX_WINDOW_LINES) "\n… 共 " + lastEntries.size + " 项" else ""
        }
        candidatesView.text = if (lastCandidates.isEmpty()) {
            "可匹配候选：-"
        } else {
            "可匹配候选（" + lastCandidates.size + "）：\n" +
                lastCandidates.take(8).joinToString("\n") { "· " + it } +
                if (lastCandidates.size > 8) "\n… 共 " + lastCandidates.size + " 项" else ""
        }
        historyView.text = if (history.size <= 1) {
            "最近不同页面：-"
        } else {
            "最近不同页面：\n" +
                history.drop(1).mapIndexed { i, s -> "  " + (i + 1) + ". " + s }.joinToString("\n")
        }
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
            text = "界面活动检测（点击复制候选）"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 10f
        }
        currentView = TextView(context).apply {
            setTextColor(0xFF8FE3FF.toInt())
            textSize = 11f
            setPadding(0, dp(context, 3), 0, 0)
        }
        windowsView = TextView(context).apply {
            setTextColor(0xFFCFCFCF.toInt())
            textSize = 9f
            setPadding(0, dp(context, 3), 0, 0)
        }
        candidatesView = TextView(context).apply {
            setTextColor(0xFF9FE3A0.toInt())
            textSize = 9f
            setPadding(0, dp(context, 3), 0, 0)
        }
        historyView = TextView(context).apply {
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 9f
            setPadding(0, dp(context, 3), 0, 0)
        }
        card.addView(title)
        card.addView(currentView)
        card.addView(windowsView)
        card.addView(candidatesView)
        card.addView(historyView)
        return card
    }

    private fun attachTouch(card: View, manager: WindowManager, p: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        var moved = false
        val slop = dp(card.context, TAP_SLOP_DP)
        card.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = p.x
                    startY = p.y
                    downX = event.rawX
                    downY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (Math.abs(dx) > slop || Math.abs(dy) > slop) moved = true
                    p.x = startX + dx
                    p.y = startY + dy
                    try {
                        manager.updateViewLayout(card, p)
                    } catch (t: Throwable) {
                        Logger.error("Inspector", "拖动更新失败", t)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) copyCandidates(card.context)
                    true
                }

                else -> true
            }
        }
    }

    private fun copyCandidates(context: Context) {
        val text = (lastCandidates.joinToString("\n"))
        if (text.isBlank()) {
            Toast.makeText(context, "暂无可复制的候选值", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(
                ClipData.newPlainText(context.getString(R.string.pages_inspector), text),
            )
            Toast.makeText(context, "已复制 " + lastCandidates.size + " 个候选值", Toast.LENGTH_SHORT).show()
            Logger.log("Inspector", "已复制候选值：" + lastCandidates.joinToString(" | "))
        } catch (t: Throwable) {
            Logger.error("Inspector", "复制候选值失败", t)
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
