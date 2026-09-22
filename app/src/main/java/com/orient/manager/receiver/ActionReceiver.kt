package com.orient.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.orient.manager.core.Logger
import com.orient.manager.core.OrientationController
import com.orient.manager.core.OrientationMode
import com.orient.manager.pref.Prefs
import com.orient.manager.service.RotationForegroundService

class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SET_MODE -> {
                val mode = intent.getStringExtra(EXTRA_MODE)
                    ?.let { name -> OrientationMode.entries.firstOrNull { it.name == name } }
                    ?: return
                Logger.log("Action", "通知动作切换 -> " + mode.name)
                OrientationController.apply(context, mode, "通知动作")
                Prefs(context).mode = mode
                launchService(context)
            }

            ACTION_TOGGLE_SERVICE -> {
                val prefs = Prefs(context)
                prefs.serviceEnabled = !prefs.serviceEnabled
                Logger.log("Action", "常驻通知开关 -> " + prefs.serviceEnabled)
                val serviceIntent = Intent(context, RotationForegroundService::class.java)
                if (prefs.serviceEnabled) {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    context.stopService(serviceIntent)
                }
            }
        }
    }

    private fun launchService(context: Context) {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RotationForegroundService::class.java),
            )
        } catch (t: Throwable) {
            Logger.error("Action", "启动常驻通知服务失败", t)
        }
    }

    companion object {
        const val ACTION_SET_MODE = "com.orient.manager.intent.action.SET_MODE"
        const val ACTION_TOGGLE_SERVICE = "com.orient.manager.intent.action.TOGGLE_SERVICE"
        const val EXTRA_MODE = "mode"
    }
}
