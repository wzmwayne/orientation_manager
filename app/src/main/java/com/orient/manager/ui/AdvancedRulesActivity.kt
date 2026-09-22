package com.orient.manager.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.orient.manager.R
import com.orient.manager.core.ActivityInspector
import com.orient.manager.core.AdvancedRule
import com.orient.manager.core.AdvancedRuleStore
import com.orient.manager.core.Logger
import com.orient.manager.service.OrientationAccessibilityService

class AdvancedRulesActivity : AppCompatActivity() {

    private lateinit var store: AdvancedRuleStore
    private lateinit var adapter: RulesAdapter
    private lateinit var emptyView: TextView
    private lateinit var loadingView: CircularProgressIndicator
    private lateinit var enableSwitch: MaterialSwitch
    private lateinit var addButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private val rules = mutableListOf<AdvancedRule>()
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced)

        store = AdvancedRuleStore(this)
        emptyView = findViewById(R.id.advanced_empty)
        loadingView = findViewById(R.id.advanced_loading)
        enableSwitch = findViewById(R.id.advanced_enable)
        addButton = findViewById(R.id.advanced_add)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        enableSwitch.setOnCheckedChangeListener { _, checked ->
            store.enabled = checked
            Logger.log("UI", "高级规则启用 = " + checked)
            if (checked) OrientationAccessibilityService.instance?.requestActiveDetection("规则开关")
        }

        val inspector = findViewById<MaterialSwitch>(R.id.pages_inspector)
        inspector.isChecked = ActivityInspector.isEnabled(this)
        inspector.setOnCheckedChangeListener { _, checked -> ActivityInspector.setEnabled(this, checked) }

        addButton.setOnClickListener {
            startActivity(
                Intent(this, RuleEditActivity::class.java)
                    .putExtra(RuleEditActivity.EXTRA_INDEX, -1),
            )
        }

        adapter = RulesAdapter()
        findViewById<ListView>(R.id.advanced_list).adapter = adapter
        loadAsync()
    }

    override fun onResume() {
        super.onResume()
        loadAsync()
    }

    private fun loadAsync() {
        if (loading) return
        loading = true
        loadingView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        enableSwitch.isEnabled = false
        addButton.isEnabled = false
        Thread {
            val loaded = try {
                store.all()
            } catch (t: Throwable) {
                Logger.error("UI", "后台加载高级规则失败", t)
                emptyList()
            }
            val enabled = store.enabled
            handler.post {
                if (isFinishing || isDestroyed) return@post
                rules.clear()
                rules.addAll(loaded)
                enableSwitch.isChecked = enabled
                enableSwitch.isEnabled = true
                addButton.isEnabled = true
                loading = false
                loadingView.visibility = View.GONE
                emptyView.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
                adapter.notifyDataSetChanged()
                Logger.log("UI", "高级规则加载完成：" + rules.size + " 条")
            }
        }.start()
    }

    private fun move(index: Int, delta: Int) {
        if (store.move(index, delta)) {
            Logger.log("UI", "规则 #" + (index + 1) + " 移动 " + (if (delta < 0) "上" else "下"))
            OrientationAccessibilityService.instance?.requestActiveDetection("规则排序")
            loadAsync()
        } else {
            Toast.makeText(this, "已到边界", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class RulesAdapter : BaseAdapter() {

        override fun getCount(): Int = rules.size

        override fun getItem(position: Int): Any = rules[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView
                ?: LayoutInflater.from(this@AdvancedRulesActivity)
                    .inflate(R.layout.advanced_rule_row, parent, false)
            val rule = rules[position]
            view.findViewById<TextView>(R.id.index).text = "#" + (position + 1)
            view.findViewById<TextView>(R.id.title).text = rule.summary()
            view.findViewById<TextView>(R.id.subtitle).text =
                rule.conditions.size.toString() + " 个条件（全部满足） → " + getString(rule.mode.labelRes)
            view.findViewById<View>(R.id.body).setOnClickListener {
                startActivity(
                    Intent(this@AdvancedRulesActivity, RuleEditActivity::class.java)
                        .putExtra(RuleEditActivity.EXTRA_INDEX, position),
                )
            }
            view.findViewById<View>(R.id.up).setOnClickListener { move(position, -1) }
            view.findViewById<View>(R.id.down).setOnClickListener { move(position, 1) }
            return view
        }
    }
}
