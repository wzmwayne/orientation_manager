package com.orient.manager.core

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.provider.Settings
import com.orient.manager.core.strategy.StrategyId
import com.orient.manager.core.strategy.StrategyManager
import com.orient.manager.notif.NotificationController

object OrientationController {

    fun canWrite(context: Context): Boolean = try {
        Settings.System.canWrite(context)
    } catch (t: Throwable) {
        Logger.error("Settings", "canWrite 探测异常", t)
        false
    }

    fun apply(context: Context, mode: OrientationMode, source: String = "全局"): Boolean {
        val settings = StrategyManager.active(context, StrategyId.SETTINGS)
        val overlay = StrategyManager.active(context, StrategyId.OVERLAY)
        val shizuku = StrategyManager.active(context, StrategyId.SHIZUKU)
        val shell = StrategyManager.active(context, StrategyId.SHELL)
        Logger.log(
            "Apply",
            "mode=" + mode.name + " source=" + source +
                " settings=" + settings + " overlay=" + overlay +
                " shizuku=" + shizuku + " shell=" + shell +
                " override=" + com.orient.manager.pref.Prefs(context).overrideEnabled,
        )

        if (!mode.controlling) {
            Logger.log("Apply", "模式=关闭：不写入任何系统设置，仅撤销强制窗口与监听")
            OverlayForceController.stop()
            RotationEnforcer.stop()
            EngineState.update(mode, source)
            NotificationController.refresh(context, mode, source)
            return true
        }

        var ok = true
        if (settings) {
            ok = applySettingsOnly(context, mode)
        }
        if (overlay) {
            applyOverlay(context, mode)
        } else {
            OverlayForceController.stop()
        }
        applyIgnoreOrientationRequest(context, mode)
        if (settings) {
            RotationEnforcer.sync(context, mode)
        } else {
            RotationEnforcer.stop()
        }

        EngineState.update(mode, source)
        NotificationController.refresh(context, mode, source)
        Logger.log(
            "Apply",
            "完成后 displayRotation=" + DisplayState.rotationName(DisplayState.rotation(context)) +
                " overlay=" + OverlayForceController.describe(),
        )
        return ok
    }

    private fun applyIgnoreOrientationRequest(context: Context, mode: OrientationMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Logger.warn("Privileged", "跳过 set-ignore-orientation-request：SDK " + Build.VERSION.SDK_INT)
            return
        }
        val ignore = mode != OrientationMode.AUTO
        when {
            StrategyManager.active(context, StrategyId.SHIZUKU) ->
                ShizukuChannel.setIgnoreOrientationRequest(ignore)

            StrategyManager.active(context, StrategyId.SHELL) ->
                ShellChannel.setIgnoreOrientationRequest(ignore)
        }
    }

    fun applySettingsOnly(context: Context, mode: OrientationMode): Boolean {
        if (!canWrite(context)) {
            Logger.warn("Settings", "写入失败：无修改系统设置权限")
            return false
        }
        return try {
            val cr = context.contentResolver
            if (mode.autoRotate) {
                val wrote = Settings.System.putInt(cr, SystemFields.ACCELEROMETER_ROTATION, 1)
                val read = Settings.System.getInt(cr, SystemFields.ACCELEROMETER_ROTATION, -1)
                Logger.log("Settings", "写 accelerometer_rotation=1 返回=" + wrote + " 回读=" + read)
            } else {
                val wroteAuto = Settings.System.putInt(cr, SystemFields.ACCELEROMETER_ROTATION, 0)
                val rotation = mode.userRotation ?: 0
                val wroteUser = Settings.System.putInt(cr, SystemFields.USER_ROTATION, rotation)
                val readAuto = Settings.System.getInt(cr, SystemFields.ACCELEROMETER_ROTATION, -1)
                val readUser = Settings.System.getInt(cr, SystemFields.USER_ROTATION, -1)
                Logger.log(
                    "Settings",
                    "写 accelerometer_rotation=0 返回=" + wroteAuto + " 回读=" + readAuto +
                        "；写 user_rotation=" + rotation + " 返回=" + wroteUser + " 回读=" + readUser,
                )
            }
            SystemFields.claim(context, mode)
            true
        } catch (t: Throwable) {
            Logger.error("Settings", "写入系统设置异常", t)
            false
        }
    }

    fun applyOverlay(context: Context, mode: OrientationMode) {
        if (mode.screenOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            OverlayForceController.stop()
        } else {
            OverlayForceController.apply(context, mode.screenOrientation)
        }
    }

    fun isAutoRotate(context: Context): Boolean = try {
        Settings.System.getInt(
            context.contentResolver,
            SystemFields.ACCELEROMETER_ROTATION,
            0,
        ) == 1
    } catch (t: Throwable) {
        Logger.error("Settings", "读取 accelerometer_rotation 异常", t)
        false
    }
}
