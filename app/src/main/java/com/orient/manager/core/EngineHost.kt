package com.orient.manager.core

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.orient.manager.R
import com.orient.manager.pref.Prefs
import com.orient.manager.service.RotationForegroundService

object EngineHost {

    private var running = false
    private var context: Context? = null
    private var notificationRequested = false

    fun isRunning(): Boolean = running

    fun engineContext(): Context? = context

    fun start(serviceContext: Context) {
        context = serviceContext
        if (running) {
            Logger.log("Engine", "引擎已在运行，仅更新宿主上下文")
            OrientationController.apply(serviceContext, Prefs(serviceContext).mode)
            syncNotification(serviceContext)
            return
        }
        running = true
        Logger.log("Engine", "引擎启动（宿主：无障碍服务，可用 2032 覆盖层）")
        PermissionsSnapshot.log(serviceContext, "无障碍连接后")
        AccessibilityAutoStarter.ensureStandalone(serviceContext, "无障碍已连接")
        if (!OrientationController.canWrite(serviceContext)) {
            Logger.warn("Engine", "无「修改系统设置」权限，仅能依赖覆盖层")
        }
        OrientationController.apply(serviceContext, Prefs(serviceContext).mode)
        syncNotification(serviceContext)
        if (ActivityInspector.isEnabled(serviceContext)) ActivityInspector.show(serviceContext)
    }

    fun stop(reason: String) {
        if (!running) return
        running = false
        Logger.warn("Engine", "引擎停止（" + reason + "）：撤销强制窗口与监听")
        RotationEnforcer.stop()
        OverlayForceController.stop()
        ActivityInspector.hide()
        val ctx = context
        context = null
        if (ctx != null) {
            stopNotification(ctx, reason)
            ToastNotifier.show(
                ctx,
                ctx.getString(R.string.toast_engine_stopped, reason),
            )
        }
        Logger.log(
            "Engine",
            "注意：无障碍服务由系统托管，本应用被停止不影响它随系统重启；本应用仅维护其启用状态",
        )
    }

    fun syncNotification(context: Context) {
        if (!Prefs(context).serviceEnabled) {
            Logger.log("Engine", "常驻通知开关=关，不启动前台服务")
            stopNotification(context, "用户关闭常驻通知")
            return
        }
        startNotification(context)
    }

    private fun startNotification(context: Context) {
        if (notificationRequested) {
            Logger.logThrottled("Engine", "notif", "常驻通知已请求过，跳过重复启动", 5000L)
            return
        }
        notificationRequested = true
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RotationForegroundService::class.java),
            )
            Logger.log(
                "Engine",
                "已请求启动常驻通知服务（SYSTEM_ALERT_WINDOW=" +
                    OverlayForceController.canDrawOverlays(context) + "）",
            )
        } catch (t: Throwable) {
            notificationRequested = false
            Logger.error(
                "Engine",
                "启动常驻通知失败：Android 12+ 禁止后台启动前台服务；打开应用时会自动重试",
                t,
            )
        }
    }

    private fun stopNotification(context: Context, reason: String) {
        notificationRequested = false
        try {
            context.stopService(Intent(context, RotationForegroundService::class.java))
            Logger.log("Engine", "已停止常驻通知（" + reason + "）")
        } catch (t: Throwable) {
            Logger.error("Engine", "停止常驻通知失败", t)
        }
    }
}
