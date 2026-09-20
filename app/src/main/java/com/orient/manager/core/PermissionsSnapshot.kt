package com.orient.manager.core

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.orient.manager.core.strategy.StrategyId
import com.orient.manager.core.strategy.StrategyManager
import com.orient.manager.pref.Prefs
import com.orient.manager.service.OrientationAccessibilityService

object PermissionsSnapshot {

    fun log(context: Context, reason: String) {
        val a11yEnabled = StrategyManager.isAccessibilityEnabled(context)
        val a11yConnected = OrientationAccessibilityService.connected
        Logger.log("Perm", "===== 权限体检（" + reason + "）=====")
        Logger.log("Perm", "修改系统设置 = " + OrientationController.canWrite(context))
        Logger.log("Perm", "悬浮窗 = " + OverlayForceController.canForce(context))
        Logger.log("Perm", "无障碍 已开启 = " + a11yEnabled + "，已连接 = " + a11yConnected)
        Logger.log(
            "Perm",
            "通知 = " + NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )
        Logger.log("Perm", "忽略电池优化 = " + KeepAlive.isIgnoringBatteryOptimizations(context))
        Logger.log(
            "Perm",
            "Shizuku 运行/授权 = " + ShizukuChannel.isRunning() + " / " + ShizukuChannel.hasPermission(),
        )
        Logger.log("Perm", "root = " + Prefs(context).shellGranted)
        Logger.log(
            "Perm",
            "方案开关 settings/overlay/a11y/shizuku/shell = " +
                StrategyManager.isEnabled(context, StrategyId.SETTINGS) + "/" +
                StrategyManager.isEnabled(context, StrategyId.OVERLAY) + "/" +
                StrategyManager.isEnabled(context, StrategyId.ACCESSIBILITY) + "/" +
                StrategyManager.isEnabled(context, StrategyId.SHIZUKU) + "/" +
                StrategyManager.isEnabled(context, StrategyId.SHELL),
        )
        val mode = Prefs(context).mode
        Logger.log("Perm", "当前模式 = " + mode.name + "，覆盖模式 = " + Prefs(context).overrideEnabled)
    }
}
