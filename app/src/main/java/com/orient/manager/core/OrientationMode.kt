package com.orient.manager.core

import android.content.pm.ActivityInfo
import android.view.Surface
import androidx.annotation.StringRes
import com.orient.manager.R

enum class OrientationMode(
    @StringRes val labelRes: Int,
    val autoRotate: Boolean,
    val userRotation: Int?,
    val screenOrientation: Int,
    val controlling: Boolean = true,
) {
    OFF(
        R.string.mode_off,
        false,
        null,
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        controlling = false,
    ),
    AUTO(
        R.string.mode_auto,
        true,
        null,
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
    ),
    SENSOR_FORCED(
        R.string.mode_sensor_forced,
        true,
        null,
        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
    ),
    PORTRAIT(
        R.string.mode_portrait,
        false,
        Surface.ROTATION_0,
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
    ),
    LANDSCAPE(
        R.string.mode_landscape,
        false,
        Surface.ROTATION_90,
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
    ),
    REVERSE_PORTRAIT(
        R.string.mode_reverse_portrait,
        false,
        Surface.ROTATION_180,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
    ),
    REVERSE_LANDSCAPE(
        R.string.mode_reverse_landscape,
        false,
        Surface.ROTATION_270,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
    ),
    ;
}
