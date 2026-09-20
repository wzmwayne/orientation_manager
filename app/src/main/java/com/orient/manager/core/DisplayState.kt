package com.orient.manager.core

import android.content.Context
import android.content.res.Configuration
import android.view.WindowManager

object DisplayState {

    fun rotation(context: Context): Int = try {
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        manager?.defaultDisplay?.rotation ?: -1
    } catch (t: Throwable) {
        Logger.error("Display", "读取 display rotation 异常", t)
        -1
    }

    fun orientation(context: Context): String = try {
        when (context.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> "landscape"
            Configuration.ORIENTATION_PORTRAIT -> "portrait"
            else -> "undefined"
        }
    } catch (t: Throwable) {
        "unknown"
    }

    fun rotationName(rotation: Int): String = when (rotation) {
        0 -> "0(竖)"
        1 -> "1(横)"
        2 -> "2(反向竖)"
        3 -> "3(反向横)"
        else -> rotation.toString()
    }
}
