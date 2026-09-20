package com.orient.manager.core

import java.io.File

object ShellChannel {

    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/system/sd/xbin/su",
        "/debug_ramdisk/su",
    )

    fun isRootPossible(): Boolean {
        val hit = SU_PATHS.firstOrNull { File(it).exists() }
        if (hit != null) Logger.logThrottled("Shell", "su", "检测到 su: " + hit, 5000L)
        return hit != null
    }

    fun runAsRoot(command: String): Boolean = try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        process.waitFor()
        val code = process.exitValue()
        if (code == 0) {
            Logger.log("Shell", "执行成功: " + command)
        } else {
            Logger.error("Shell", "执行失败 exit=" + code + " cmd=" + command)
        }
        code == 0
    } catch (t: Throwable) {
        Logger.error("Shell", "执行异常 cmd=" + command, t)
        false
    }

    fun setIgnoreOrientationRequest(ignore: Boolean): Boolean {
        val value = if (ignore) "true" else "false"
        return runAsRoot("wm set-ignore-orientation-request " + value)
    }
}
