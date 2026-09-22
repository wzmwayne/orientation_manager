package com.orient.manager.ui

import android.content.Intent
import android.os.Bundle
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
    private lateinit var enableSwitch: MaterialSwitch
    private val rules = mutableListOf<AdvancedRule>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced)

        store = AdvancedRuleStore(this)
        emptyView = findViewById(R.id.advanced_empty)
        enableSwitch = findViewById(R.id.advanced_enable)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        enableSwitch.setOnCheckedChangeListener { _, checked ->
            store.enabled = checked
            Logger.log("UI", "高级规则启用 = " + checked)
            if (checked) OrientationAccessibilityService.instance?.requestActiveDetection("规则开关")
        }

        val inspector = findViewById<MaterialSwitch>(R.id.pages_inspector)
        inspector.isChecked = ActivityInspector.isEnabled(this)
        inspector.setOnCheckedChangeListener { _, checked -> ActivityInspector.setEnabled(this, checked) }

        findViewById<Button>(R.id.advanced_add).setOnClickListener {
            startActivity(Intent(this, RuleEditActivity::class.java).putExtra(RuleEditActivity.EXTRA_INDEX, -1))
        }

        adapter = RulesAdapter()
        findViewById<ListView>(R.id.advanced_list).adapter = adapter
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        rules.clear()
        rules.addAll(store.all())
        enableSwitch.isChecked = store.enabled
        emptyView.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun move(index: Int, delta: Int) {
        if (store.move(index, delta)) {
            Logger.log("UI", "规则 #" + (index + 1) + " 移动 " + (if (delta < 0) "上" else "下"))
            OrientationAccessibilityService.instance?.requestActiveDetection("规则排序")
            refresh()
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
