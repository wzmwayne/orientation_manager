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
import com.orient.manager.R
import com.orient.manager.core.AdvancedRule
import com.orient.manager.core.AdvancedRuleStore
import com.orient.manager.core.Logger
import com.orient.manager.core.OrientationMode
import com.orient.manager.core.RuleCondition
import com.orient.manager.core.RuleField
import com.orient.manager.service.OrientationAccessibilityService

class RuleEditActivity : AppCompatActivity() {

    private lateinit var store: AdvancedRuleStore
    private lateinit var adapter: ConditionsAdapter
    private lateinit var patternInput: EditText
    private lateinit var fieldSpinner: Spinner
    private lateinit var modeSpinner: Spinner

    private val fields = RuleField.entries
    private val modes = OrientationMode.entries
    private val conditions = mutableListOf<RuleCondition>()
    private var index = NEW_RULE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rule_edit)

        store = AdvancedRuleStore(this)
        index = intent.getIntExtra(EXTRA_INDEX, NEW_RULE)
        patternInput = findViewById(R.id.rule_pattern)
        fieldSpinner = findViewById(R.id.rule_field)
        modeSpinner = findViewById(R.id.rule_mode)

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

        val existing = store.all().getOrNull(index)
        if (existing != null) {
            conditions.addAll(existing.conditions)
            modeSpinner.setSelection(modes.indexOf(existing.mode).coerceAtLeast(0))
            title = getString(R.string.rule_edit_title) + " #" + (index + 1)
        } else {
            index = NEW_RULE
            modeSpinner.setSelection(modes.indexOf(OrientationMode.PORTRAIT).coerceAtLeast(0))
        }

        findViewById<Button>(R.id.rule_add_condition).setOnClickListener { addCondition() }
        findViewById<Button>(R.id.rule_save).setOnClickListener { save() }
        findViewById<Button>(R.id.rule_delete).setOnClickListener { delete() }

        adapter = ConditionsAdapter()
        findViewById<ListView>(R.id.rule_conditions).adapter = adapter
        render()
    }

    private fun addCondition() {
        val pattern = patternInput.text.toString().trim()
        if (pattern.isEmpty()) {
            Toast.makeText(this, getString(R.string.advanced_need_pattern), Toast.LENGTH_SHORT).show()
            return
        }
        if (runCatching { Regex(pattern) }.isFailure) {
            Toast.makeText(this, getString(R.string.advanced_invalid), Toast.LENGTH_SHORT).show()
            return
        }
        val field = fields[fieldSpinner.selectedItemPosition.coerceIn(fields.indices)]
        conditions.add(RuleCondition(field, pattern))
        Logger.log("UI", "规则添加条件：字段=" + field.name + " 正则=" + pattern)
        patternInput.setText("")
        render()
    }

    private fun save() {
        if (conditions.isEmpty()) {
            Toast.makeText(this, getString(R.string.rule_edit_no_condition), Toast.LENGTH_SHORT).show()
            return
        }
        val mode = modes[modeSpinner.selectedItemPosition.coerceIn(modes.indices)]
        val rule = AdvancedRule(conditions.toList(), mode)
        if (index == NEW_RULE) {
            store.add(rule)
            Logger.log("UI", "新建高级规则：" + rule.summary() + " → " + mode.name)
        } else {
            store.update(index, rule)
            Logger.log("UI", "更新高级规则 #" + (index + 1) + "：" + rule.summary() + " → " + mode.name)
        }
        OrientationAccessibilityService.instance?.requestActiveDetection("规则保存")
        Toast.makeText(this, getString(R.string.rule_edit_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun delete() {
        if (index != NEW_RULE) {
            store.remove(index)
            Logger.log("UI", "删除高级规则 #" + (index + 1))
            OrientationAccessibilityService.instance?.requestActiveDetection("规则删除")
        }
        finish()
    }

    private fun render() {
        adapter.notifyDataSetChanged()
        findViewById<Button>(R.id.rule_delete).visibility =
            if (index == NEW_RULE) View.GONE else View.VISIBLE
    }

    private inner class ConditionsAdapter : BaseAdapter() {

        override fun getCount(): Int = conditions.size

        override fun getItem(position: Int): Any = conditions[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView
                ?: LayoutInflater.from(this@RuleEditActivity)
                    .inflate(R.layout.condition_row, parent, false)
            val condition = conditions[position]
            view.findViewById<TextView>(R.id.condition_field).text = getString(condition.field.labelRes)
            view.findViewById<TextView>(R.id.condition_pattern).text = condition.pattern
            view.setOnClickListener {
                conditions.removeAt(position)
                Logger.log("UI", "移除条件 #" + (position + 1))
                render()
            }
            return view
        }
    }

    companion object {
        const val EXTRA_INDEX = "rule_index"
        private const val NEW_RULE = -1
    }
}
