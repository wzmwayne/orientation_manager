package com.orient.manager.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.orient.manager.core.Logger
import com.orient.manager.core.OrientationController
import com.orient.manager.notif.NotificationController
import com.orient.manager.pref.Prefs

class RotationForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Logger.log("Service", "onCreate")
        NotificationController.ensureChannel(this)
        val mode = Prefs(this).mode
        val notification = NotificationController.build(this, mode)
        try {
            startForeground(NotificationController.NOTIF_ID, notification)
            Logger.log("Service", "startForeground 成功，常驻通知已显示")
        } catch (t: Throwable) {
            Logger.error("Service", "startForeground 失败，退化为普通常驻通知", t)
            NotificationController.postPlain(this, mode)
            stopSelf()
            return
        }
        OrientationController.apply(this, mode)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = Prefs(this).mode
        Logger.log("Service", "onStartCommand mode=" + mode.name)
        OrientationController.apply(this, mode)
        try {
            NotificationManagerCompat.from(this)
                .notify(NotificationController.NOTIF_ID, NotificationController.build(this, mode))
        } catch (t: Throwable) {
            Logger.error("Service", "更新通知失败", t)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Logger.log("Service", "onTaskRemoved，尝试自拉起")
        if (Prefs(this).serviceEnabled) {
            try {
                startForegroundService(Intent(applicationContext, RotationForegroundService::class.java))
            } catch (t: Throwable) {
                Logger.error("Service", "自拉起失败", t)
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Logger.log("Service", "onDestroy")
        super.onDestroy()
    }
}
