package com.orient.manager.core

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings

object SystemFields {

    const val ACCELEROMETER_ROTATION = Settings.System.ACCELEROMETER_ROTATION

    const val USER_ROTATION = Settings.System.USER_ROTATION

    private const val PREF_NAME = "orient_state"
    private const val KEY_CLAIM = "claim"
    private const val KEY_HITS = "foreign_hits"
    private const val CLAIM_INTERVAL_MS = 2000L

    private var lastClaimAt = 0L

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun claim(context: Context, mode: OrientationMode) {
        val now = System.currentTimeMillis()
        if (now - lastClaimAt < CLAIM_INTERVAL_MS) return
        lastClaimAt = now
        prefs(context).edit()
            .putString(KEY_CLAIM, context.packageName + "|" + now + "|" + mode.name)
            .apply()
    }

    fun ownClaim(context: Context): String? = prefs(context).getString(KEY_CLAIM, null)

    fun foreignHits(context: Context): Int = prefs(context).getInt(KEY_HITS, 0)

    fun bumpForeignHits(context: Context): Int {
        val next = foreignHits(context) + 1
        prefs(context).edit().putInt(KEY_HITS, next).apply()
        return next
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
