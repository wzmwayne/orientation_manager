package com.orient.manager.core

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager

object OverlayForceController {

    private const val TYPE_ACCESSIBILITY_OVERLAY = 2032

    private const val FLAGS_ORIGINAL =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

    private var view: View? = null
    private var windowManager: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null

    fun canForce(context: Context): Boolean = EngineHost.engineContext() != null

    fun describe(): String {
        val p = params
        return "attached=" + (view?.isAttachedToWindow ?: false) +
            " type=" + TYPE_ACCESSIBILITY_OVERLAY +
            " screenOrientation=" + (p?.screenOrientation ?: -99)
    }

    fun apply(context: Context, screenOrientation: Int) {
        val ctx = EngineHost.engineContext()
        if (ctx == null) {
            Logger.warn("Overlay", "跳过：无障碍宿主未连接（覆盖层只用 2032，不再要求悬浮窗权限）")
            return
        }
        if (windowManager == null) {
            windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            params = WindowManager.LayoutParams(
                1,
                1,
                TYPE_ACCESSIBILITY_OVERLAY,
                FLAGS_ORIGINAL,
                PixelFormat.TRANSPARENT,
            )
            view = null
            Logger.log(
                "Overlay",
                "初始化窗口组件 type=" + TYPE_ACCESSIBILITY_OVERLAY +
                    " flags=" + FLAGS_ORIGINAL + " size=1x1",
            )
        }

        val p = params
        val manager = windowManager
        if (p == null || manager == null) {
            Logger.error("Overlay", "WindowManager 或 LayoutParams 为空")
            return
        }
        val attached = view != null
        if (attached && p.screenOrientation == screenOrientation) {
            Logger.logThrottled("Overlay", "same", "窗口已在位 " + describe(), 3000L)
            return
        }
        p.screenOrientation = screenOrientation
        try {
            val existing = view
            if (existing == null) {
                val v = View(ctx)
                manager.addView(v, p)
                view = v
                Logger.log("Overlay", "已添加强制窗口 screenOrientation=" + screenOrientation + " " + describe())
            } else {
                manager.updateViewLayout(existing, p)
                Logger.log("Overlay", "更新强制窗口 screenOrientation=" + screenOrientation + " " + describe())
            }
        } catch (t: Throwable) {
            Logger.error("Overlay", "添加/更新窗口失败 screenOrientation=" + screenOrientation, t)
            windowManager = null
            params = null
            view = null
        }
    }

    fun refresh(context: Context) {
        val existing = view ?: return
        val manager = windowManager ?: return
        val p = params ?: return
        if (EngineHost.engineContext() == null) {
            Logger.warn("Overlay", "无障碍宿主已丢失，移除覆盖层")
            stop()
            return
        }
        try {
            manager.updateViewLayout(existing, p)
        } catch (t: Throwable) {
            Logger.error("Overlay", "refresh 失败，尝试重建", t)
            view = null
            apply(context, p.screenOrientation)
        }
    }

    fun isAttached(): Boolean = view != null

    fun stop() {
        val existing = view
        val manager = windowManager
        view = null
        if (existing != null && manager != null) {
            try {
                manager.removeViewImmediate(existing)
                Logger.log("Overlay", "已移除强制窗口")
            } catch (t: Throwable) {
                Logger.error("Overlay", "移除窗口失败", t)
            }
        }
        params?.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    fun currentOrientation(): Int = params?.screenOrientation
        ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}
