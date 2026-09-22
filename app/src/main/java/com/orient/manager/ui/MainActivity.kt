package com.orient.manager.ui

import android.Manifest
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.orient.manager.R
import com.orient.manager.core.AccessibilityAutoStarter
import com.orient.manager.core.AppRuleStore
import com.orient.manager.core.EngineHost
import com.orient.manager.core.KeepAlive
import com.orient.manager.core.Logger
import com.orient.manager.core.OrientationController
import com.orient.manager.core.PermissionsSnapshot
import com.orient.manager.core.OrientationMode
import com.orient.manager.core.OverlayForceController
import com.orient.manager.core.RotationEnforcer
import com.orient.manager.core.SystemFields
import com.orient.manager.core.strategy.StrategyManager
import com.orient.manager.pref.Prefs
import com.orient.manager.service.OrientationAccessibilityService
import com.orient.manager.service.RotationForegroundService

class MainActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var overrideDesc: TextView
    private lateinit var batteryDesc: TextView
    private lateinit var autostartDesc: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var rowWrite: View
    private lateinit var rowAccessibility: View
    private lateinit var rowNotifications: View
    private lateinit var rowSecure: View
    private lateinit var switchPerApp: MaterialSwitch
    private lateinit var switchService: MaterialSwitch
    private lateinit var switchOverride: MaterialSwitch

    private val chipModes = LinkedHashMap<Int, OrientationMode>()
    private var updating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.status)
        overrideDesc = findViewById(R.id.override_desc)
        batteryDesc = findViewById(R.id.battery_desc)
        autostartDesc = findViewById(R.id.autostart_desc)
        chipGroup = findViewById(R.id.chip_group)
        rowWrite = findViewById(R.id.row_write)
        rowAccessibility = findViewById(R.id.row_accessibility)
        rowNotifications = findViewById(R.id.row_notifications)
        rowSecure = findViewById(R.id.row_secure)
        switchPerApp = findViewById(R.id.switch_per_app)
        switchService = findViewById(R.id.switch_service)
        switchOverride = findViewById(R.id.switch_override)

        chipModes[R.id.chip_off] = OrientationMode.OFF
        chipModes[R.id.chip_auto] = OrientationMode.AUTO
        chipModes[R.id.chip_sensor_forced] = OrientationMode.SENSOR_FORCED
        chipModes[R.id.chip_portrait] = OrientationMode.PORTRAIT
        chipModes[R.id.chip_landscape] = OrientationMode.LANDSCAPE
        chipModes[R.id.chip_reverse_portrait] = OrientationMode.REVERSE_PORTRAIT
        chipModes[R.id.chip_reverse_landscape] = OrientationMode.REVERSE_LANDSCAPE

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (updating) return@setOnCheckedStateChangeListener
            val mode = chipModes[checkedIds.firstOrNull() ?: -1]
                ?: return@setOnCheckedStateChangeListener
            applyMode(mode)
        }

        switchPerApp.setOnCheckedChangeListener { _, checked ->
            if (updating) return@setOnCheckedChangeListener
            AppRuleStore(this).perAppEnabled = checked
        }

        switchService.setOnCheckedChangeListener { _, checked ->
            if (updating) return@setOnCheckedChangeListener
            val prefs = Prefs(this)
            prefs.serviceEnabled = checked
            val intent = Intent(this, RotationForegroundService::class.java)
            if (checked) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                stopService(intent)
            }
        }

        switchOverride.setOnCheckedChangeListener { _, checked ->
            if (updating) return@setOnCheckedChangeListener
            val prefs = Prefs(this)
            prefs.overrideEnabled = checked
            if (checked) ensureService()
            RotationEnforcer.sync(this, prefs.mode)
            updateOverrideDesc()
        }

        findViewById<View>(R.id.row_apps).setOnClickListener {
            startActivity(Intent(this, AppsActivity::class.java))
        }

        findViewById<View>(R.id.row_strategies).setOnClickListener {
            startActivity(Intent(this, StrategiesActivity::class.java))
        }

        findViewById<View>(R.id.row_pages).setOnClickListener {
            startActivity(Intent(this, AdvancedRulesActivity::class.java))
        }

        findViewById<View>(R.id.row_log).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        rowWrite.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                    .setData(Uri.parse("package:" + packageName)),
            )
        }

        rowAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        rowNotifications.setOnClickListener { grantNotifications() }
        rowSecure.setOnClickListener { requestSecureWrite() }

        findViewById<View>(R.id.row_battery).setOnClickListener { requestBattery() }
        findViewById<View>(R.id.row_autostart).setOnClickListener { openAutoStart() }
    }

    override fun onResume() {
        super.onResume()
        Logger.log(
            "UI",
            "常驻通知开关=" + Prefs(this).serviceEnabled +
                "，系统通知可用=" + NotificationManagerCompat.from(this).areNotificationsEnabled() +
                "，前台服务已请求=" + EngineHost.isRunning(),
        )
        PermissionsSnapshot.log(this, "进入界面")
        AccessibilityAutoStarter.ensureStandalone(this, "进入界面")
        EngineHost.syncNotification(this)
        refresh()
    }

    private fun refresh() {
        val current = Prefs(this).mode
        updating = true
        chipModes.entries.firstOrNull { it.value == current }?.let { chipGroup.check(it.key) }
        switchPerApp.isChecked = AppRuleStore(this).perAppEnabled
        switchService.isChecked = Prefs(this).serviceEnabled
        switchOverride.isChecked = Prefs(this).overrideEnabled
        updating = false

        rowWrite.visibility = if (OrientationController.canWrite(this)) View.GONE else View.VISIBLE
        val a11yEnabled = StrategyManager.isAccessibilityEnabled(this)
        val a11yConnected = OrientationAccessibilityService.connected
        rowAccessibility.visibility =
            if (a11yEnabled && a11yConnected) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.accessibility_desc).text =
            if (a11yEnabled && !a11yConnected) {
                getString(R.string.perm_accessibility_desc_disconnected)
            } else {
                getString(R.string.perm_accessibility_desc)
            }
        rowNotifications.visibility =
            if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        rowSecure.visibility =
            if (AccessibilityAutoStarter.hasSecureWrite(this)) View.GONE else View.VISIBLE

        batteryDesc.text = if (KeepAlive.isIgnoringBatteryOptimizations(this)) {
            getString(R.string.keepalive_battery_ok)
        } else {
            getString(R.string.keepalive_battery_need)
        }
        autostartDesc.text = if (KeepAlive.hasOemAutoStartSettings(this)) {
            getString(R.string.keepalive_autostart_check)
        } else {
            getString(R.string.keepalive_autostart_app_details)
        }

        val engine = if (OrientationAccessibilityService.connected) {
            ""
        } else {
            "\n" + getString(R.string.status_engine_offline)
        }
        statusView.text = getString(R.string.status_current, getString(current.labelRes)) + engine
        updateOverrideDesc()
    }

    private fun ensureService() {
        val prefs = Prefs(this)
        if (prefs.serviceEnabled) return
        prefs.serviceEnabled = true
        Logger.log("UI", "为保证强制生效，自动开启常驻服务")
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, RotationForegroundService::class.java),
            )
        } catch (t: Throwable) {
            Logger.error("UI", "启动常驻服务失败", t)
        }
    }

    private fun updateOverrideDesc() {
        val prefs = Prefs(this)
        overrideDesc.text = if (prefs.overrideEnabled) {
            val hits = SystemFields.foreignHits(this)
            if (hits > 0) {
                getString(R.string.override_desc_hits, hits)
            } else {
                getString(R.string.override_desc_on, prefs.overrideIntervalMs)
            }
        } else {
            getString(R.string.override_desc)
        }
    }

    private fun requestBattery() {
        Logger.log("UI", "申请忽略电池优化")
        try {
            startActivity(KeepAlive.batteryOptimizationIntent(this))
        } catch (t: Throwable) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun openAutoStart() {
        Logger.log("UI", "跳转开机自启设置")
        val intent = KeepAlive.autoStartIntent(this) ?: KeepAlive.settingsIntent(this)
        try {
            startActivity(intent)
        } catch (t: Throwable) {
            try {
                startActivity(KeepAlive.settingsIntent(this))
            } catch (t2: Throwable) {
                Toast.makeText(this, getString(R.string.toast_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestSecureWrite() {
        if (AccessibilityAutoStarter.hasSecureWrite(this)) {
            Toast.makeText(this, getString(R.string.secure_granted), Toast.LENGTH_SHORT).show()
            refresh()
            return
        }
        Logger.log("UI", "申请 WRITE_SECURE_SETTINGS")
        if (AccessibilityAutoStarter.tryGrantSecureWrite(this, "用户点击授权")) {
            Toast.makeText(this, getString(R.string.secure_granted), Toast.LENGTH_SHORT).show()
            refresh()
            return
        }
        showSecureAdbDialog()
    }

    private fun showSecureAdbDialog() {
        val command = AccessibilityAutoStarter.adbCommand(this)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.secure_adb_title)
            .setMessage(command)
            .setPositiveButton(R.string.secure_copy) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.perm_secure_title), command),
                )
                Toast.makeText(this, getString(R.string.secure_copied), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun grantNotifications() {
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsRuntimePermission) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS,
            )
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }
    }

    private fun applyMode(mode: OrientationMode) {
        val canWrite = OrientationController.canWrite(this)
        val canOverlay = OverlayForceController.canForce(this)
        val canPrivileged = StrategyManager.active(this, com.orient.manager.core.strategy.StrategyId.SHIZUKU) ||
            StrategyManager.active(this, com.orient.manager.core.strategy.StrategyId.SHELL)
        if (!canWrite && !canOverlay && !canPrivileged) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        if (!OrientationAccessibilityService.connected) {
            Logger.warn("UI", "无障碍未连接，引擎不会运行，引导用户开启")
            runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        OrientationController.apply(this, mode, "全局")
        Prefs(this).mode = mode
        Logger.log("UI", "选择方向: " + mode.name)
        if (mode.controlling && mode != OrientationMode.AUTO) {
            ensureService()
        }
        refresh()
        statusView.text = getString(R.string.status_current, getString(mode.labelRes))
        updateOverrideDesc()
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 10
    }
}
