package com.orient.manager.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.orient.manager.core.EngineState
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
        EngineState.initFromPrefs(this)
        val mode = EngineState.mode
        val source = EngineState.source
        val notification = NotificationController.build(this, mode, source)
        try {
            startForeground(NotificationController.NOTIF_ID, notification)
            Logger.log("Service", "startForeground 成功：" + mode.name + " · " + source)
        } catch (t: Throwable) {
            Logger.error("Service", "startForeground 失败，退化为普通常驻通知", t)
            NotificationController.postPlain(this, mode, source)
            stopSelf()
            return
        }
        OrientationController.apply(this, Prefs(this).mode, EngineState.source)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        EngineState.initFromPrefs(this)
        Logger.log(
            "Service",
            "onStartCommand，当前生效 " + EngineState.mode.name + " · " + EngineState.source,
        )
        NotificationController.refreshCurrent(this)
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
