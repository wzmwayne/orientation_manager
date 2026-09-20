package com.orient.manager.core.strategy

import androidx.annotation.StringRes
import com.orient.manager.R

enum class StrategyId(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val defaultEnabled: Boolean,
) {
    SETTINGS(
        "settings",
        R.string.strategy_settings_title,
        R.string.strategy_settings_desc,
        true,
    ),
    OVERLAY(
        "overlay",
        R.string.strategy_overlay_title,
        R.string.strategy_overlay_desc,
        true,
    ),
    ACCESSIBILITY(
        "accessibility",
        R.string.strategy_accessibility_title,
        R.string.strategy_accessibility_desc,
        true,
    ),
    SHIZUKU(
        "shizuku",
        R.string.strategy_shizuku_title,
        R.string.strategy_shizuku_desc,
        true,
    ),
    SHELL(
        "shell",
        R.string.strategy_shell_title,
        R.string.strategy_shell_desc,
        true,
    ),
}
