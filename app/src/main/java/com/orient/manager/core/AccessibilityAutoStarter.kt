package com.orient.manager.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.orient.manager.pref.Prefs

object AccessibilityAutoStarter {

    private const val SERVICE_CLASS = "OrientationAccessibilityService"
    private const val ATTEMPT_INTERVAL_MS = 10000L
    private const val PERMISSION = Manifest.permission.WRITE_SECURE_SETTINGS

    private var lastAttemptAt = 0L

    fun component(context: Context): String =
        context.packageName + "/" + context.packageName + ".service." + SERVICE_CLASS

    fun hasSecureWrite(context: Context): Boolean = try {
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        Logger.error("A11yStart", "检查 WRITE_SECURE_SETTINGS 异常", t)
        false
    }

    fun adbCommand(context: Context): String =
        "adb shell pm grant " + context.packageName + " " + PERMISSION

    fun isServiceListed(context: Context): Boolean {
        val enabled = readEnabledServices(context) ?: return false
        return enabled.split(':').any {
            it.contains(context.packageName) && it.contains(SERVICE_CLASS)
        }
    }

    fun isAccessibilityFlagOn(context: Context): Boolean = try {
        Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
    } catch (t: Throwable) {
        Logger.error("A11yStart", "读取 accessibility_enabled 异常", t)
        false
    }

    fun ensureStandalone(context: Context, reason: String): Boolean {
        val listed = isServiceListed(context)
        val flag = isAccessibilityFlagOn(context)
        val secure = hasSecureWrite(context)
        Logger.log(
            "A11yStart",
            "自持检查（" + reason + "）：本服务在启用列表=" + listed +
                "，accessibility_enabled=" + flag + "，WRITE_SECURE_SETTINGS=" + secure,
        )
        if (listed && flag) {
            Logger.log("A11yStart", "无障碍由系统托管并持久化，不依赖本应用自启动")
            return true
        }
        if (!secure) {
            tryGrantSecureWrite(context, reason)
        }
        val target = composeTarget(context, listed)
        if (hasSecureWrite(context)) {
            if (writeDirect(context, target, reason)) return true
        }
        return writeViaShell(context, target, reason)
    }

    fun tryGrantSecureWrite(context: Context, reason: String): Boolean {
        if (hasSecureWrite(context)) return true
        val command = "pm grant " + context.packageName + " " + PERMISSION
        val shizuku = ShizukuChannel.hasPermission()
        val root = Prefs(context).shellGranted
        if (!shizuku && !root) {
            Logger.warn(
                "A11yStart",
                "无 Shizuku/root，无法代为授权（" + reason + "）；请手动执行: " + adbCommand(context),
            )
            return false
        }
        Logger.log(
            "A11yStart",
            "尝试代为授予 WRITE_SECURE_SETTINGS（" + reason + "），通道=" + (if (shizuku) "Shizuku" else "root"),
        )
        val ok = if (shizuku) {
            ShizukuChannel.runShell(command)
        } else {
            ShellChannel.runAsRoot(command)
        }
        Logger.log(
            "A11yStart",
            "授权结果=" + ok + "，checkSelfPermission=" + hasSecureWrite(context),
        )
        return ok
    }

    private fun composeTarget(context: Context, listed: Boolean): String {
        val current = readEnabledServices(context).orEmpty()
        return when {
            listed -> current
            current.isBlank() -> component(context)
            else -> current.trimEnd(':') + ":" + component(context)
        }
    }

    private fun writeDirect(context: Context, target: String, reason: String): Boolean {
        Logger.log("A11yStart", "持有 WRITE_SECURE_SETTINGS，直接写入无障碍启用状态（" + reason + "）")
        return try {
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                target,
            )
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                "1",
            )
            Logger.log(
                "A11yStart",
                "直接写入完成，回读列表=" + readEnabledServices(context) +
                    "，accessibility_enabled=" + isAccessibilityFlagOn(context),
            )
            true
        } catch (t: Throwable) {
            Logger.error("A11yStart", "直接写入安全设置异常", t)
            false
        }
    }

    private fun writeViaShell(context: Context, target: String, reason: String): Boolean {
        val shizuku = ShizukuChannel.hasPermission()
        val root = Prefs(context).shellGranted
        if (!shizuku && !root) {
            Logger.warn(
                "A11yStart",
                "无 WRITE_SECURE_SETTINGS 且无 Shizuku/root，无法自动维护无障碍状态（" + reason + "）",
            )
            return false
        }
        val now = System.currentTimeMillis()
        if (now - lastAttemptAt < ATTEMPT_INTERVAL_MS) return false
        lastAttemptAt = now
        val cmdServices = "settings put secure enabled_accessibility_services '" + target + "'"
        val cmdEnabled = "settings put secure accessibility_enabled 1"
        Logger.log(
            "A11yStart",
            "回退到 shell 写入无障碍启用状态（" + reason + "），通道=" + (if (shizuku) "Shizuku" else "root"),
        )
        val ok = if (shizuku) {
            ShizukuChannel.runShell(cmdServices) && ShizukuChannel.runShell(cmdEnabled)
        } else {
            ShellChannel.runAsRoot(cmdServices) && ShellChannel.runAsRoot(cmdEnabled)
        }
        Logger.log(
            "A11yStart",
            "shell 写入结果=" + ok +
                "，回读列表=" + readEnabledServices(context) +
                "，accessibility_enabled=" + isAccessibilityFlagOn(context),
        )
        return ok
    }

    private fun readEnabledServices(context: Context): String? = try {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
    } catch (t: Throwable) {
        Logger.error("A11yStart", "读取 enabled_accessibility_services 异常", t)
        null
    }
}
