package com.orient.manager.core

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
import com.orient.manager.pref.Prefs

object ActivityInspector {

    private var view: View? = null
    private var windowManager: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var currentView: TextView? = null
    private var historyView: TextView? = null
    private val history = ArrayDeque<String>()

    fun isEnabled(context: Context): Boolean = Prefs(context).inspectorEnabled

    fun setEnabled(context: Context, enabled: Boolean) {
        Prefs(context).inspectorEnabled = enabled
        Logger.log("Inspector", "悬浮窗界面活动检测 -> " + enabled)
        if (enabled) show(context) else hide()
    }

    fun show(context: Context) {
        if (view != null) return
        if (!OverlayForceController.canDrawOverlays(context)) {
            Logger.warn("Inspector", "缺少悬浮窗权限，无法显示活动检测窗")
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
            x = dp(ctx, 12)
            y = dp(ctx, 80)
        }
        val card = buildCard(ctx)
        attachDrag(card, manager, p)
        try {
            manager.addView(card, p)
            view = card
            windowManager = manager
            params = p
            updateViews()
            Logger.log("Inspector", "活动检测悬浮窗已显示（type=2038，可拖动）")
        } catch (t: Throwable) {
            Logger.error("Inspector", "添加活动检测悬浮窗失败", t)
        }
    }

    fun hide() {
        val existing = view
        val manager = windowManager
        view = null
        currentView = null
        historyView = null
        if (existing != null && manager != null) {
            try {
                manager.removeViewImmediate(existing)
                Logger.log("Inspector", "活动检测悬浮窗已移除")
            } catch (t: Throwable) {
                Logger.error("Inspector", "移除活动检测悬浮窗失败", t)
            }
        }
        windowManager = null
        params = null
    }

    fun onIdentityChanged(pkg: String, activityClass: String?) {
        val entry = pkg + " / " + (activityClass ?: "未知活动")
        if (history.firstOrNull() != entry) {
            history.remove(entry)
            history.addFirst(entry)
            while (history.size > 4) history.removeLast()
        }
        updateViews(entry)
    }

    private fun updateViews(current: String? = history.firstOrNull()) {
        val currentView = this.currentView ?: return
        val historyView = this.historyView ?: return
        currentView.text = "当前：" + (current ?: "-")
        historyView.text = if (history.size <= 1) {
            "最近：-"
        } else {
            "最近不同活动：\n" + history.drop(1).mapIndexed { i, s -> "  " + (i + 1) + ". " + s }.joinToString("\n")
        }
    }

    private fun buildCard(context: Context): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
            background = GradientDrawable().apply {
                setColor(0xE6000000.toInt())
                cornerRadius = dp(context, 10).toFloat()
                setStroke(dp(context, 1), 0x66FFFFFF)
            }
        }
        val title = TextView(context).apply {
            text = "界面活动检测"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
        }
        currentView = TextView(context).apply {
            setTextColor(0xFF8FE3FF.toInt())
            textSize = 11f
            setPadding(0, dp(context, 4), 0, 0)
        }
        historyView = TextView(context).apply {
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 10f
            setPadding(0, dp(context, 4), 0, 0)
        }
        card.addView(title)
        card.addView(currentView)
        card.addView(historyView)
        return card
    }

    private fun attachDrag(card: View, manager: WindowManager, p: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        card.setOnTouchListener { _, event ->
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
