package com.orient.manager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.orient.manager.R
import com.orient.manager.core.ActivityInspector
import com.orient.manager.core.AdvancedRule
import com.orient.manager.core.AdvancedRuleStore
import com.orient.manager.core.Logger
import com.orient.manager.core.OrientationMode
import com.orient.manager.core.RuleField
import com.orient.manager.service.OrientationAccessibilityService

class AdvancedRulesActivity : AppCompatActivity() {

    private lateinit var store: AdvancedRuleStore
    private lateinit var adapter: RulesAdapter
    private lateinit var emptyView: TextView
    private lateinit var input: EditText
    private lateinit var fieldSpinner: Spinner
    private lateinit var modeSpinner: Spinner
    private lateinit var enableSwitch: MaterialSwitch

    private val rules = mutableListOf<AdvancedRule>()
    private val modes = OrientationMode.entries
    private val fields = RuleField.entries

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced)

        store = AdvancedRuleStore(this)
        emptyView = findViewById(R.id.advanced_empty)
        input = findViewById(R.id.advanced_input)
        fieldSpinner = findViewById(R.id.advanced_field)
        modeSpinner = findViewById(R.id.advanced_mode)
        enableSwitch = findViewById(R.id.advanced_enable)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        fieldSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            fields.map { getString(it.labelRes) },
        )
        modeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            modes.map { getString(it.labelRes) },
        )
        modeSpinner.setSelection(modes.indexOf(OrientationMode.PORTRAIT).coerceAtLeast(0))

        enableSwitch.setOnCheckedChangeListener { _, checked ->
            store.enabled = checked
            Logger.log("UI", "高级规则启用 = " + checked)
            if (checked) OrientationAccessibilityService.instance?.requestActiveDetection("规则开关变化")
        }

        val inspector = findViewById<MaterialSwitch>(R.id.pages_inspector)
        inspector.isChecked = ActivityInspector.isEnabled(this)
        inspector.setOnCheckedChangeListener { _, checked ->
            ActivityInspector.setEnabled(this, checked)
        }

        findViewById<Button>(R.id.advanced_add).setOnClickListener { addRule() }

        adapter = RulesAdapter()
        findViewById<ListView>(R.id.advanced_list).adapter = adapter
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun addRule() {
        val pattern = input.text.toString().trim()
        if (pattern.isEmpty()) {
            Toast.makeText(this, getString(R.string.advanced_need_pattern), Toast.LENGTH_SHORT).show()
            return
        }
        if (runCatching { Regex(pattern) }.isFailure) {
            Toast.makeText(this, getString(R.string.advanced_invalid), Toast.LENGTH_SHORT).show()
            return
        }
        val field = fields[fieldSpinner.selectedItemPosition.coerceIn(fields.indices)]
        val mode = modes[modeSpinner.selectedItemPosition.coerceIn(modes.indices)]
        store.add(AdvancedRule(pattern, field, mode))
        Logger.log(
            "UI",
            "添加高级规则：字段=" + field.name + " 正则=" + pattern + " 方向=" + mode.name,
        )
        input.setText("")
        Toast.makeText(this, getString(R.string.advanced_added), Toast.LENGTH_SHORT).show()
        OrientationAccessibilityService.instance?.requestActiveDetection("新增规则")
        refresh()
    }

    private fun refresh() {
        rules.clear()
        rules.addAll(store.all())
        enableSwitch.isChecked = store.enabled
        emptyView.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun editRule(index: Int) {
        val rule = rules.getOrNull(index) ?: return
        val labels = (modes.map { getString(it.labelRes) } +
            listOf(getString(R.string.advanced_change_field), getString(R.string.advanced_delete)))
            .toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(rule.pattern)
            .setItems(labels) { _, which ->
                when {
                    which < modes.size -> {
                        store.update(index, rule.copy(mode = modes[which]))
                        Logger.log("UI", "规则 #" + (index + 1) + " 方向改为 " + modes[which].name)
                    }

                    which == modes.size -> changeField(index, rule)

                    else -> {
                        store.remove(index)
                        Logger.log("UI", "删除规则 #" + (index + 1))
                    }
                }
                OrientationAccessibilityService.instance?.requestActiveDetection("规则修改")
                refresh()
            }
            .show()
    }

    private fun changeField(index: Int, rule: AdvancedRule) {
        val labels = fields.map { getString(it.labelRes) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.advanced_field_prompt)
            .setItems(labels) { _, which ->
                store.update(index, rule.copy(field = fields[which]))
                Logger.log("UI", "规则 #" + (index + 1) + " 字段改为 " + fields[which].name)
                refresh()
            }
            .show()
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
            view.findViewById<TextView>(R.id.title).text = rule.pattern
            view.findViewById<TextView>(R.id.subtitle).text =
                getString(rule.field.labelRes) + "  →  " + getString(rule.mode.labelRes)
            view.setOnClickListener { editRule(position) }
            return view
        }
    }
}
