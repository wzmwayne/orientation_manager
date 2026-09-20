package com.orient.manager.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object KeepAlive {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return try {
            manager.isIgnoringBatteryOptimizations(context.packageName)
        } catch (t: Throwable) {
            false
        }
    }

    fun batteryOptimizationIntent(context: Context): Intent = try {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:" + context.packageName))
    } catch (t: Throwable) {
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    private val AUTO_START_COMPONENTS = listOf(
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
        ),
        ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        ),
        ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
        ),
        ComponentName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.startupapp.StartupAppListActivity",
        ),
        ComponentName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        ),
        ComponentName(
            "com.oppo.safe",
            "com.oppo.safe.permission.startup.StartupAppListActivity",
        ),
        ComponentName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        ),
        ComponentName(
            "com.iqoo.secure",
            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        ),
        ComponentName(
            "com.letv.android.letvsafe",
            "com.letv.android.letvsafe.AutobootManageActivity",
        ),
        ComponentName(
            "com.samsung.android.lool",
            "com.samsung.android.sm.ui.battery.BatteryActivity",
        ),
        ComponentName(
            "com.asus.mobilemanager",
            "com.asus.mobilemanager.autostart.AutoStartActivity",
        ),
        ComponentName(
            "com.transsion.phonemaster",
            "com.cyin.himgr.autostart.AutoStartActivity",
        ),
    )

    fun autoStartIntent(context: Context): Intent? {
        val packageManager = context.packageManager
        for (component in AUTO_START_COMPONENTS) {
            val intent = Intent().setComponent(component)
            if (packageManager.resolveActivity(intent, 0) != null) return intent
        }
        return null
    }

    fun hasOemAutoStartSettings(context: Context): Boolean = autoStartIntent(context) != null

    fun settingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:" + context.packageName),
    )
}
