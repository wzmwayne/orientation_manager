package com.orient.manager.core

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.orient.manager.pref.Prefs
import com.orient.manager.service.RotationForegroundService

object EngineHost {

    private var running = false
    private var context: Context? = null

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
    }

    fun stop(reason: String) {
        if (!running) return
        running = false
        Logger.warn("Engine", "引擎停止（" + reason + "）：撤销强制窗口与监听")
        RotationEnforcer.stop()
        OverlayForceController.stop()
        val ctx = context
        context = null
        if (ctx != null) {
            stopNotification(ctx, reason)
        }
        Logger.log(
            "Engine",
            "注意：无障碍服务由系统托管，本应用被停止不影响它随系统重启；本应用仅维护其启用状态",
        )
    }

    fun syncNotification(context: Context) {
        if (Prefs(context).serviceEnabled) {
            startNotification(context)
        } else {
            stopNotification(context, "用户关闭常驻通知")
        }
    }

    private fun startNotification(context: Context) {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RotationForegroundService::class.java),
            )
            Logger.log("Engine", "已随无障碍服务启动常驻通知")
        } catch (t: Throwable) {
            Logger.error("Engine", "随无障碍启动常驻通知失败（可回前台手动开启）", t)
        }
    }

    private fun stopNotification(context: Context, reason: String) {
        try {
            context.stopService(Intent(context, RotationForegroundService::class.java))
            Logger.log("Engine", "已停止常驻通知（" + reason + "）")
        } catch (t: Throwable) {
            Logger.error("Engine", "停止常驻通知失败", t)
        }
    }
}
