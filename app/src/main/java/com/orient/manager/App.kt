package com.orient.manager

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import com.orient.manager.core.AccessibilityAutoStarter
import com.orient.manager.core.EngineState
import com.orient.manager.core.Logger
import com.orient.manager.core.PermissionsSnapshot
import com.orient.manager.core.UiState
import com.orient.manager.service.OrientationAccessibilityService

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Logger.init(this)
        EngineState.initFromPrefs(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = UiState.onActivityStarted()
            override fun onActivityStopped(activity: Activity) = UiState.onActivityStopped()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
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
