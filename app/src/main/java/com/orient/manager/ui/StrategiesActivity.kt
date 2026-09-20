package com.orient.manager.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.orient.manager.R
import com.orient.manager.core.Logger
import com.orient.manager.core.OrientationController
import com.orient.manager.core.ShellChannel
import com.orient.manager.core.ShizukuChannel
import com.orient.manager.core.strategy.StrategyId
import com.orient.manager.core.strategy.StrategyManager
import com.orient.manager.core.strategy.StrategyState
import com.orient.manager.pref.Prefs

class StrategiesActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_strategies)

        container = findViewById(R.id.container)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        rebuild()
    }

    override fun onResume() {
        super.onResume()
        rebuild()
    }

    private fun rebuild() {
        container.removeAllViews()
        val states = StrategyManager.states(this)
        val available = states.filter { it.available }
        val unavailable = states.filter { !it.available }

        if (available.isNotEmpty()) {
            addHeader(getString(R.string.strategies_section_available))
            addHint(getString(R.string.strategies_hint))
            available.forEach { addRow(it) }
        }
        if (unavailable.isNotEmpty()) {
            addHeader(getString(R.string.strategies_section_unavailable))
            addHint(getString(R.string.strategies_hint_unavailable))
            unavailable.forEach { addRow(it) }
        }
    }

    private fun addHeader(text: String) {
        container.addView(
            TextView(this).apply {
                this.text = text
                textSize = 13f
                alpha = 0.6f
                setPadding(dp(20), dp(20), dp(20), dp(6))
            },
        )
    }

    private fun addHint(text: String) {
        container.addView(
            TextView(this).apply {
                this.text = text
                textSize = 12f
                alpha = 0.45f
                setPadding(dp(20), 0, dp(20), dp(6))
            },
        )
    }

    private fun addRow(state: StrategyState) {
        val row = layoutInflater.inflate(R.layout.strategy_row, container, false)
        row.findViewById<TextView>(R.id.title).text = getString(state.id.titleRes)

        val subtitle = getString(state.id.descRes) +
            if (state.note.isNullOrEmpty()) "" else "\n" + state.note
        row.findViewById<TextView>(R.id.subtitle).text = subtitle

        val reasonView = row.findViewById<TextView>(R.id.reason)
        if (state.reason.isNullOrEmpty()) {
            reasonView.visibility = View.GONE
        } else {
            reasonView.text = state.reason
            reasonView.visibility = View.VISIBLE
        }

        val toggle = row.findViewById<MaterialSwitch>(R.id.switch_strategy)
        if (state.available) {
            toggle.isChecked = state.enabled
            toggle.setOnCheckedChangeListener { _, checked -> onToggle(state.id, checked) }
        } else {
            row.alpha = 0.6f
            toggle.isEnabled = false
            toggle.isChecked = false
            row.setOnClickListener { onGrant(state.id) }
        }
        container.addView(row)
    }

    private fun onToggle(id: StrategyId, checked: Boolean) {
        StrategyManager.setEnabled(this, id, checked)
        Logger.log("UI", "旋转方案 " + id.key + " -> " + checked)
        OrientationController.apply(this, Prefs(this).mode)
        rebuild()
    }

    private fun onGrant(id: StrategyId) {
        when (id) {
            StrategyId.SHIZUKU -> {
                if (!ShizukuChannel.isRunning()) {
                    Toast.makeText(
                        this,
                        getString(R.string.strategy_reason_shizuku_missing),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    ShizukuChannel.requestPermission()
                    Toast.makeText(
                        this,
                        getString(R.string.strategy_reason_shizuku_not_granted),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }

            StrategyId.SHELL -> {
                if (!ShellChannel.isRootPossible() && !Prefs(this).shellGranted) {
                    Toast.makeText(
                        this,
                        getString(R.string.strategy_reason_shell_no_root),
                        Toast.LENGTH_SHORT,
                    ).show()
                    return
                }
                Toast.makeText(
                    this,
                    getString(R.string.strategy_reason_shell_not_granted),
                    Toast.LENGTH_SHORT,
                ).show()
                Thread {
                    val granted = ShellChannel.runAsRoot("id")
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        Prefs(this).shellGranted = granted
                        if (granted) OrientationController.apply(this, Prefs(this).mode)
                        rebuild()
                    }
                }.start()
            }

            else -> Unit
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
