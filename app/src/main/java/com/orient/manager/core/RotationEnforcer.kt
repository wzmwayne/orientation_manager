package com.orient.manager.core

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.orient.manager.core.strategy.StrategyId
import com.orient.manager.core.strategy.StrategyManager
import com.orient.manager.pref.Prefs

object RotationEnforcer {

    private const val FOLLOW_UP_MS = 60L
    private const val FOLLOW_UP_MAX = 8

    private var observer: ContentObserver? = null
    private var appContext: Context? = null
    private val handler = Handler(Looper.getMainLooper())
    private var loop: Runnable? = null
    private var followUp: Runnable? = null
    private var followUpCount = 0

    fun sync(context: Context, mode: OrientationMode) {
        val ctx = context.applicationContext
        start(ctx)
        if (!mode.controlling) {
            stop()
            Logger.log("Enforce", "模式=关闭，停止监听")
            return
        }
        val override = Prefs(ctx).overrideEnabled
        if (override) {
            startLoop(ctx)
            Logger.log("Enforce", "覆盖模式开启：" + mode.name + "，轮询 " + Prefs(ctx).overrideIntervalMs + "ms")
        } else {
            stopLoop()
            Logger.log("Enforce", "事件驱动模式：" + mode.name)
        }
    }

    fun stop() {
        stopLoop()
        handler.removeCallbacksAndMessages(null)
        val contentObserver = observer ?: return
        try {
            appContext?.contentResolver?.unregisterContentObserver(contentObserver)
        } catch (t: Throwable) {
            Logger.error("Enforce", "注销 observer 异常", t)
        }
        observer = null
        appContext = null
        Logger.log("Enforce", "已停止监听")
    }

    private fun start(ctx: Context) {
        if (observer != null) return
        val contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                val context = appContext ?: return
                enforce(context)
            }
        }
        ctx.contentResolver.registerContentObserver(
            Settings.System.getUriFor(SystemFields.ACCELEROMETER_ROTATION),
            false,
            contentObserver,
        )
        ctx.contentResolver.registerContentObserver(
            Settings.System.getUriFor(SystemFields.USER_ROTATION),
            false,
            contentObserver,
        )
        observer = contentObserver
        appContext = ctx
        Logger.log("Enforce", "已注册 accelerometer_rotation / user_rotation 监听")
    }

    private fun startLoop(ctx: Context) {
        if (loop != null) return
        val runnable = object : Runnable {
            override fun run() {
                val context = appContext ?: return
                enforce(context)
                handler.postDelayed(this, Prefs(context).overrideIntervalMs)
            }
        }
        loop = runnable
        handler.postDelayed(runnable, Prefs(ctx).overrideIntervalMs)
    }

    private fun stopLoop() {
        loop?.let { handler.removeCallbacks(it) }
        loop = null
    }

    private fun enforce(context: Context) {
        val mode = Prefs(context).mode
        if (!mode.controlling) return
        val override = Prefs(context).overrideEnabled
        val overlayActive = StrategyManager.active(context, StrategyId.OVERLAY)
        val canWrite = OrientationController.canWrite(context)

        if (canWrite && (override || !overlayActive || mode == OrientationMode.AUTO)) {
            assertSettings(context, mode, override)
        } else if (canWrite && !mode.autoRotate) {
            writeUserRotation(context, mode.userRotation ?: 0)
        }

        if (overlayActive && mode != OrientationMode.AUTO) {
            OverlayForceController.refresh(context)
        }
        heartbeat(context, mode, overlayActive)
        SystemFields.claim(context, mode)
    }


    private fun heartbeat(context: Context, mode: OrientationMode, overlayActive: Boolean) {
        Logger.logThrottled(
            "Watch",
            "heartbeat",
            "引擎存活 mode=" + mode.name +
                " overlayActive=" + overlayActive +
                " " + OverlayForceController.describe() +
                " displayRotation=" + DisplayState.rotationName(DisplayState.rotation(context)) +
                " orientation=" + DisplayState.orientation(context),
            3000L,
        )
    }

    private fun assertSettings(context: Context, mode: OrientationMode, override: Boolean) {
        val cr = context.contentResolver
        val targetAuto = if (mode.autoRotate) 1 else 0
        var mismatch = false
        try {
            val currentAuto = Settings.System.getInt(
                cr,
                SystemFields.ACCELEROMETER_ROTATION,
                -1,
            )
            if (currentAuto != targetAuto) {
                mismatch = true
                if (override) SystemFields.bumpForeignHits(context)
                Settings.System.putInt(cr, SystemFields.ACCELEROMETER_ROTATION, targetAuto)
                Logger.logThrottled(
                    "Enforce",
                    "accel",
                    "accelerometer_rotation 被改为 " + currentAuto + "（目标 " + targetAuto + "），已回写",
                    800L,
                )
            }
            if (!mode.autoRotate) {
                val targetUser = mode.userRotation ?: 0
                val currentUser = Settings.System.getInt(cr, SystemFields.USER_ROTATION, -1)
                if (currentUser != targetUser) {
                    mismatch = true
                    Settings.System.putInt(cr, SystemFields.USER_ROTATION, targetUser)
                    Logger.logThrottled(
                        "Enforce",
                        "user",
                        "user_rotation 被改为 " + currentUser + "（目标 " + targetUser + "），已回写",
                        800L,
                    )
                }
            }
        } catch (t: Throwable) {
            Logger.error("Enforce", "读取/写入系统设置异常", t)
            return
        }
        if (mismatch && override) {
            scheduleFollowUp(context)
        } else if (!mismatch) {
            followUpCount = 0
        }
    }

    private fun scheduleFollowUp(context: Context) {
        if (followUpCount >= FOLLOW_UP_MAX) return
        followUpCount++
        Logger.logThrottled("Enforce", "followup", "触发跟进重写 #" + followUpCount, 1500L)
        followUp?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            val ctx = appContext ?: return@Runnable
            enforce(ctx)
        }
        followUp = runnable
        handler.postDelayed(runnable, FOLLOW_UP_MS)
    }

    private fun writeUserRotation(context: Context, target: Int) {
        try {
            val cr = context.contentResolver
            val current = Settings.System.getInt(cr, SystemFields.USER_ROTATION, -1)
            if (current != target) {
                Settings.System.putInt(cr, SystemFields.USER_ROTATION, target)
                Logger.logThrottled(
                    "Enforce",
                    "userOnly",
                    "user_rotation 被改为 " + current + "（目标 " + target + "），已回写",
                    800L,
                )
            }
        } catch (t: Throwable) {
            Logger.error("Enforce", "写 user_rotation 异常", t)
        }
    }
}
