package com.orient.manager

import android.app.Application
import android.os.Build
import com.orient.manager.core.AccessibilityAutoStarter
import com.orient.manager.core.Logger
import com.orient.manager.core.PermissionsSnapshot
import com.orient.manager.service.OrientationAccessibilityService

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Logger.init(this)
        Logger.log(
            "App",
            "进程启动 sdk=" + Build.VERSION.SDK_INT + " device=" + Build.MANUFACTURER + " " + Build.MODEL,
        )
        Logger.log(
            "App",
            "无障碍连接状态 = " + OrientationAccessibilityService.connected,
        )
        PermissionsSnapshot.log(this, "应用启动")
        AccessibilityAutoStarter.ensureStandalone(this, "应用启动")
    }
}
