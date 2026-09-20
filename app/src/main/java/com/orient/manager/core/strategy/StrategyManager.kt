package com.orient.manager.core.strategy

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.orient.manager.R
import com.orient.manager.core.OrientationController
import com.orient.manager.core.OverlayForceController
import com.orient.manager.core.ShellChannel
import com.orient.manager.core.ShizukuChannel
import com.orient.manager.pref.Prefs
import com.orient.manager.service.OrientationAccessibilityService

data class StrategyState(
    val id: StrategyId,
    val available: Boolean,
    val reason: String?,
    val note: String?,
    val enabled: Boolean,
)

object StrategyManager {

    fun isEnabled(context: Context, id: StrategyId): Boolean =
        Prefs(context).strategyEnabled(id.key, id.defaultEnabled)

    fun setEnabled(context: Context, id: StrategyId, enabled: Boolean) {
        Prefs(context).setStrategyEnabled(id.key, enabled)
    }

    fun active(context: Context, id: StrategyId): Boolean =
        isEnabled(context, id) && isAvailable(context, id)

    fun isAvailable(context: Context, id: StrategyId): Boolean = when (id) {
        StrategyId.SETTINGS, StrategyId.OVERLAY, StrategyId.ACCESSIBILITY -> true
        StrategyId.SHIZUKU -> ShizukuChannel.hasPermission()
        StrategyId.SHELL -> Prefs(context).shellGranted
    }

    fun states(context: Context): List<StrategyState> =
        StrategyId.entries.map { state(context, it) }

    fun state(context: Context, id: StrategyId): StrategyState = when (id) {
        StrategyId.SETTINGS -> StrategyState(
            id,
            true,
            null,
            if (OrientationController.canWrite(context)) {
                context.getString(R.string.strategy_state_ready)
            } else {
                context.getString(R.string.strategy_state_need_write)
            },
            isEnabled(context, id),
        )

        StrategyId.OVERLAY -> {
            val a11y = OrientationAccessibilityService.connected
            val canDraw = OverlayForceController.canDrawOverlays(context)
            StrategyState(
                id,
                true,
                null,
                when {
                    a11y -> context.getString(R.string.strategy_state_ready_a11y_layer)
                    canDraw -> context.getString(R.string.strategy_state_ready)
                    else -> context.getString(R.string.strategy_state_need_overlay)
                },
                isEnabled(context, id),
            )
        }

        StrategyId.ACCESSIBILITY -> StrategyState(
            id,
            true,
            null,
            if (isAccessibilityEnabled(context)) {
                context.getString(R.string.strategy_state_ready)
            } else {
                context.getString(R.string.strategy_state_need_accessibility)
            },
            isEnabled(context, id),
        )

        StrategyId.SHIZUKU -> {
            val granted = ShizukuChannel.hasPermission()
            val running = ShizukuChannel.isRunning()
            StrategyState(
                id = id,
                available = granted,
                reason = when {
                    granted -> null
                    running -> context.getString(R.string.strategy_reason_shizuku_not_granted)
                    else -> context.getString(R.string.strategy_reason_shizuku_missing)
                },
                note = when {
                    granted -> context.getString(R.string.strategy_state_ready)
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ->
                        context.getString(R.string.strategy_state_need_android12)
                    else -> context.getString(R.string.strategy_state_need_shizuku)
                },
                enabled = isEnabled(context, id) && granted,
            )
        }

        StrategyId.SHELL -> {
            val granted = Prefs(context).shellGranted
            val possible = ShellChannel.isRootPossible() || granted
            StrategyState(
                id = id,
                available = granted,
                reason = when {
                    granted -> null
                    possible -> context.getString(R.string.strategy_reason_shell_not_granted)
                    else -> context.getString(R.string.strategy_reason_shell_no_root)
                },
                note = if (granted) {
                    context.getString(R.string.strategy_state_ready)
                } else {
                    context.getString(R.string.strategy_state_need_shell)
                },
                enabled = isEnabled(context, id) && granted,
            )
        }
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any {
            it.contains(context.packageName) && it.contains("OrientationAccessibilityService")
        }
    }
}
