package com.orient.manager.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object NodeInspectorOverlay {

    private var root: View? = null
    private var windowManager: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null

    fun isShowing(): Boolean = root != null

    fun showBoxes(context: Context, nodes: List<NodeInfo>) {
        show(context, nodes, pickMode = false)
    }

    fun showPicker(context: Context, nodes: List<NodeInfo>) {
        show(context, nodes, pickMode = true)
    }

    fun hide() {
        val existing = root
        val manager = windowManager
        root = null
        if (existing != null && manager != null) {
            try {
                manager.removeViewImmediate(existing)
                Logger.log("NodeOverlay", "组件检查层已关闭")
            } catch (t: Throwable) {
                Logger.error("NodeOverlay", "移除组件检查层失败", t)
            }
        }
        windowManager = null
        params = null
    }

    private fun show(context: Context, nodes: List<NodeInfo>, pickMode: Boolean) {
        hide()
        if (!OverlayForceController.canDrawOverlays(context)) {
            Logger.warn("NodeOverlay", "缺少悬浮窗权限，无法显示组件检查层")
            return
        }
        val ctx = EngineHost.engineContext() ?: context.applicationContext
        val manager = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (manager == null) {
            Logger.error("NodeOverlay", "无法获取 WindowManager")
            return
        }
        val layout = FrameLayout(ctx)
        val panel = TextView(ctx).apply {
            setTextColor(0xFF8FE3FF.toInt())
            textSize = 10f
            setPadding(16, 12, 16, 4)
            text = "点击任意组件查看详细信息"
            setTextIsSelectable(true)
        }
        val copyButton = Button(ctx).apply {
            text = "复制"
            textSize = 11f
            setOnClickListener { copyDetails(ctx, panel.text.toString()) }
        }
        val panelBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xE6000000.toInt())
            setPadding(8, 8, 8, 8)
            addView(panel)
            addView(copyButton)
            visibility = if (pickMode) View.VISIBLE else View.GONE
        }

        val canvas = NodeCanvasView(ctx, nodes, pickMode) { node ->
            panel.text = node.detail()
        }
        layout.addView(
            canvas,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        layout.addView(
            panelBox,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
        if (pickMode) {
            val close = Button(ctx).apply {
                text = "关闭"
                textSize = 11f
                setOnClickListener { hide() }
            }
            layout.addView(
                close,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START,
                ),
            )
        }
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!pickMode) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        )
        try {
            manager.addView(layout, p)
            root = layout
            windowManager = manager
            params = p
            Logger.log(
                "NodeOverlay",
                "组件检查层已显示：" + nodes.size + " 个组件，模式=" + (if (pickMode) "点击拾取" else "仅显示边框"),
            )
        } catch (t: Throwable) {
            Logger.error("NodeOverlay", "添加组件检查层失败", t)
        }
    }

    private fun copyDetails(context: Context, detail: String) {
        if (detail.isBlank()) {
            Toast.makeText(context, "暂无组件详情", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("组件详情", detail))
            Toast.makeText(context, "组件详情已复制", Toast.LENGTH_SHORT).show()
            Logger.log("NodeOverlay", "已复制组件详情：" + detail.replace('\n', ' '))
        } catch (t: Throwable) {
            Logger.error("NodeOverlay", "复制组件详情失败", t)
        }
    }

    private class NodeCanvasView(
        context: Context,
        private val nodes: List<NodeInfo>,
        private val pickMode: Boolean,
        private val onPicked: (NodeInfo) -> Unit,
    ) : View(context) {

        private val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#8033B5E5")
        }
        private val highlightPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.parseColor("#FFFF7043")
        }
        private val fillPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#33FF7043")
        }
        private val textPaint = Paint().apply {
            color = Color.parseColor("#CCFFFFFF")
            textSize = 22f
        }
        private var highlight: NodeInfo? = null

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            for (node in nodes) {
                val rect = node.bounds ?: continue
                canvas.drawRect(rect, boxPaint)
                val label = node.label()
                if (label.isNotBlank() && rect.height() > 24) {
                    val clipped = TextUtils.ellipsize(
                        label,
                        android.text.TextPaint(textPaint),
                        (rect.width() - 4).toFloat(),
                        TextUtils.TruncateAt.END,
                    ).toString()
                    canvas.drawText(clipped, rect.left.toFloat() + 2, rect.top.toFloat() + 20, textPaint)
                }
            }
            highlight?.let { node ->
                node.bounds?.let {
                    canvas.drawRect(it, fillPaint)
                    canvas.drawRect(it, highlightPaint)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!pickMode) return false
            if (event.action == MotionEvent.ACTION_UP) {
                val x = event.x.toInt()
                val y = event.y.toInt()
                val node = nodes
                    .filter { it.bounds?.contains(x, y) == true }
                    .maxByOrNull { it.depth }
                if (node != null) {
                    highlight = node
                    invalidate()
                    onPicked(node)
                    Logger.log("NodeOverlay", "选中组件：" + node.label())
                }
            }
            return true
        }
    }
}
