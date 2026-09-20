package com.orient.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
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
                OrientationController.apply(context, mode)
                Prefs(context).mode = mode
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, RotationForegroundService::class.java),
                )
            }

            ACTION_TOGGLE_SERVICE -> {
                val prefs = Prefs(context)
                prefs.serviceEnabled = !prefs.serviceEnabled
                val serviceIntent = Intent(context, RotationForegroundService::class.java)
                if (prefs.serviceEnabled) {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    context.stopService(serviceIntent)
                }
            }
        }
    }

    companion object {
        const val ACTION_SET_MODE = "com.orient.manager.intent.action.SET_MODE"
        const val ACTION_TOGGLE_SERVICE = "com.orient.manager.intent.action.TOGGLE_SERVICE"
        const val EXTRA_MODE = "mode"
    }
}
