package com.orient.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.orient.manager.core.AccessibilityAutoStarter
import com.orient.manager.core.Logger
import com.orient.manager.pref.Prefs
import com.orient.manager.service.RotationForegroundService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Logger.log("Boot", "收到开机广播")
        Logger.log(
            "Boot",
            "说明：无障碍服务由系统托管，开机后系统会自行拉回；此处仅维护启用状态与常驻通知",
        )
        AccessibilityAutoStarter.ensureStandalone(context, "开机广播")
        if (!Prefs(context).serviceEnabled) return
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RotationForegroundService::class.java),
            )
            Logger.log("Boot", "已启动常驻通知服务")
        } catch (t: Throwable) {
            Logger.error("Boot", "启动常驻通知服务失败", t)
        }
    }
}
