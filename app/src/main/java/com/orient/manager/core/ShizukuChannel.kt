package com.orient.manager.core

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuChannel {

    const val REQUEST_CODE = 4021

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        Logger.error("Shizuku", "pingBinder 异常", t)
        false
    }

    fun hasPermission(): Boolean = try {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        Logger.error("Shizuku", "checkSelfPermission 异常", t)
        false
    }

    fun requestPermission(): Boolean = try {
        when {
            !isRunning() -> {
                Logger.warn("Shizuku", "申请权限失败：服务未运行")
                false
            }
            hasPermission() -> true
            else -> {
                Shizuku.requestPermission(REQUEST_CODE)
                Logger.log("Shizuku", "已发起权限申请 code=" + REQUEST_CODE)
                false
            }
        }
    } catch (t: Throwable) {
        Logger.error("Shizuku", "requestPermission 异常", t)
        false
    }

    fun runShell(command: String): Boolean {
        if (!hasPermission()) {
            Logger.warn("Shizuku", "跳过：无权限 cmd=" + command)
            return false
        }
        val process = newProcess(arrayOf("sh", "-c", command))
        if (process == null) {
            Logger.error("Shizuku", "newProcess 调用失败（反射可能失效）")
            return false
        }
        return try {
            process.waitFor()
            val code = process.exitValue()
            if (code == 0) {
                Logger.log("Shizuku", "执行成功: " + command)
            } else {
                Logger.error("Shizuku", "执行失败 exit=" + code + " cmd=" + command)
            }
            code == 0
        } catch (t: Throwable) {
            Logger.error("Shizuku", "等待进程异常 cmd=" + command, t)
            false
        }
    }

    fun setIgnoreOrientationRequest(ignore: Boolean): Boolean =
        runShell("wm set-ignore-orientation-request " + (if (ignore) "true" else "false"))

    private fun newProcess(command: Array<String>): Process? = try {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        method.invoke(null, command, null, null) as? Process
    } catch (t: Throwable) {
        Logger.error("Shizuku", "反射 newProcess 异常", t)
        null
    }
}
