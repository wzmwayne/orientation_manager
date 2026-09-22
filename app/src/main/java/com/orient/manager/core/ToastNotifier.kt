package com.orient.manager.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object ToastNotifier {

    private const val DEDUPE_MS = 1500L

    private val handler = Handler(Looper.getMainLooper())
    private var lastMessage: String? = null
    private var lastAt = 0L

    fun show(context: Context, message: String) {
        val now = System.currentTimeMillis()
        if (message == lastMessage && now - lastAt < DEDUPE_MS) return
        lastMessage = message
        lastAt = now
        val appContext = context.applicationContext
        try {
            handler.post {
                try {
                    Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    Logger.error("Toast", "Toast 显示失败: " + message, t)
                }
            }
        } catch (t: Throwable) {
            Logger.error("Toast", "投递 Toast 失败: " + message, t)
        }
    }
}
