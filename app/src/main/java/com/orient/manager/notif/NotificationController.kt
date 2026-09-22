package com.orient.manager.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.orient.manager.R
import com.orient.manager.core.EngineState
import com.orient.manager.core.Logger
import com.orient.manager.core.OrientationMode
import com.orient.manager.pref.Prefs
import com.orient.manager.receiver.ActionReceiver
import com.orient.manager.ui.MainActivity

object NotificationController {

    const val CHANNEL_ID = "orientation_service"
    const val NOTIF_ID = 1

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
            Logger.log("Notif", "已创建通知渠道 " + CHANNEL_ID)
        }
    }

    fun build(context: Context, mode: OrientationMode, source: String) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_orientation)
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText(context.getString(mode.labelRes) + " · " + source)
        .setOngoing(true)
        .setShowWhen(false)
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(0, context.getString(R.string.mode_auto), modeAction(context, OrientationMode.AUTO))
        .addAction(0, context.getString(R.string.mode_portrait), modeAction(context, OrientationMode.PORTRAIT))
        .addAction(0, context.getString(R.string.mode_landscape), modeAction(context, OrientationMode.LANDSCAPE))
        .build()

    fun refresh(context: Context, mode: OrientationMode, source: String) {
        if (!Prefs(context).serviceEnabled) return
        try {
            ensureChannel(context)
            NotificationManagerCompat.from(context).notify(NOTIF_ID, build(context, mode, source))
            Logger.logThrottled(
                "Notif",
                "refresh",
                "通知已更新为 " + mode.name + " · " + source,
                600L,
            )
        } catch (t: Throwable) {
            Logger.error("Notif", "刷新通知失败", t)
        }
    }

    fun refreshCurrent(context: Context) {
        EngineState.initFromPrefs(context)
        refresh(context, EngineState.mode, EngineState.source)
    }

    fun postPlain(context: Context, mode: OrientationMode, source: String): Boolean = try {
        ensureChannel(context)
        NotificationManagerCompat.from(context).notify(NOTIF_ID, build(context, mode, source))
        Logger.log("Notif", "已投递普通常驻通知")
        true
    } catch (t: Throwable) {
        Logger.error("Notif", "投递普通通知失败", t)
        false
    }

    private fun modeAction(context: Context, mode: OrientationMode) = PendingIntent.getBroadcast(
        context,
        mode.ordinal,
        Intent(context, ActionReceiver::class.java)
            .setAction(ActionReceiver.ACTION_SET_MODE)
            .putExtra(ActionReceiver.EXTRA_MODE, mode.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
