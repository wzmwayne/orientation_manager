package com.orient.manager.ui

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private lateinit var toolbar: MaterialToolbar
    private lateinit var patternInput: EditText
    private lateinit var fieldSpinner: Spinner
    private lateinit var modeSpinner: Spinner

    private val fields = RuleField.visibleEntries
    private val modes = OrientationMode.entries
    private val conditions = mutableListOf<RuleCondition>()
    private val handler = Handler(Looper.getMainLooper())
    private var pendingDeletePosition = -1
    private var pendingDeleteAt = 0L
    private var index = NEW_RULE

    private val resetPending = Runnable {
        pendingDeletePosition = -1
        adapter.notifyDataSetChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rule_edit)

        store = AdvancedRuleStore(this)
        index = intent.getIntExtra(EXTRA_INDEX, NEW_RULE)
        patternInput = findViewById(R.id.rule_pattern)
        fieldSpinner = findViewById(R.id.rule_field)
        modeSpinner = findViewById(R.id.rule_mode)
        toolbar = findViewById(R.id.toolbar)

        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.rule_edit)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_delete_rule) {
                delete()
                true
            } else {
                false
            }
        }

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
            toolbar.title = getString(R.string.rule_edit_title) + " #" + (index + 1)
        } else {
            index = NEW_RULE
            modeSpinner.setSelection(modes.indexOf(OrientationMode.PORTRAIT).coerceAtLeast(0))
            toolbar.title = getString(R.string.rule_edit_title)
        }

        findViewById<Button>(R.id.rule_add_condition).setOnClickListener { addCondition() }
        findViewById<Button>(R.id.rule_save).setOnClickListener { save() }

        adapter = ConditionsAdapter()
        findViewById<ListView>(R.id.rule_conditions).adapter = adapter
        render()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
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
        clearPending()
        render()
    }

    private fun onConditionTap(position: Int) {
        val now = System.currentTimeMillis()
        if (pendingDeletePosition == position && now - pendingDeleteAt <= CONFIRM_WINDOW_MS) {
            conditions.removeAt(position)
            Logger.log("UI", "二次点击确认，删除条件 #" + (position + 1))
            clearPending()
            render()
            return
        }
        pendingDeletePosition = position
        pendingDeleteAt = now
        handler.removeCallbacks(resetPending)
        handler.postDelayed(resetPending, CONFIRM_WINDOW_MS)
        Toast.makeText(this, getString(R.string.condition_delete_hint), Toast.LENGTH_SHORT).show()
        Logger.log("UI", "条件 #" + (position + 1) + " 进入待删除状态（2 秒内再次点击删除）")
        adapter.notifyDataSetChanged()
    }

    private fun clearPending() {
        pendingDeletePosition = -1
        handler.removeCallbacks(resetPending)
    }

    private fun editCondition(position: Int) {
        val condition = conditions.getOrNull(position) ?: return
        if (pendingDeletePosition == position) {
            clearPending()
            adapter.notifyDataSetChanged()
        }
        val labels = arrayOf(
            getString(R.string.cond_menu_behavior),
            getString(R.string.cond_menu_pattern),
            getString(R.string.cond_menu_field),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(condition.describe())
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> editBehavior(position, condition)
                    1 -> editPattern(position, condition)
                    else -> editField(position, condition)
                }
            }
            .show()
    }

    private fun editBehavior(position: Int, condition: RuleCondition) {
        val labels = arrayOf(
            getString(R.string.cond_behavior_match),
            getString(R.string.cond_behavior_not_match),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cond_edit_behavior)
            .setItems(labels) { _, which ->
                conditions[position] = condition.copy(negate = which == 1)
                Logger.log("UI", "条件 #" + (position + 1) + " 行为改为 " + labels[which])
                render()
            }
            .show()
    }

    private fun editPattern(position: Int, condition: RuleCondition) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(condition.pattern)
            setSelection(condition.pattern.length)
        }
        val container = FrameLayout(this).apply {
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cond_edit_pattern)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = input.text.toString().trim()
                if (value.isEmpty()) {
                    Toast.makeText(this, getString(R.string.advanced_need_pattern), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (runCatching { Regex(value) }.isFailure) {
                    Toast.makeText(this, getString(R.string.advanced_invalid), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                conditions[position] = condition.copy(pattern = value)
                Logger.log("UI", "条件 #" + (position + 1) + " 内容改为 " + value)
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun editField(position: Int, condition: RuleCondition) {
        val labels = fields.map { getString(it.labelRes) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cond_edit_field)
            .setItems(labels) { _, which ->
                conditions[position] = condition.copy(field = fields[which])
                Logger.log("UI", "条件 #" + (position + 1) + " 字段改为 " + fields[which].name)
                render()
            }
            .show()
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
        toolbar.menu.findItem(R.id.action_delete_rule)?.isVisible = index != NEW_RULE
    }

    private inner class ConditionsAdapter : BaseAdapter() {

        override fun getCount(): Int = conditions.size

        override fun getItem(position: Int): Any = conditions[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView
                ?: LayoutInflater.from(this@RuleEditActivity)
                    .inflate(R.layout.condition_row, parent, false)
            if (view.tag == null) view.tag = view.background
            val condition = conditions[position]
            view.findViewById<TextView>(R.id.condition_field).text =
                getString(condition.field.labelRes) + (if (condition.negate) "（不匹配）" else "")
            view.findViewById<TextView>(R.id.condition_pattern).text = condition.pattern

            val pending = position == pendingDeletePosition
            if (pending) {
                view.setBackgroundColor(PENDING_COLOR)
                view.findViewById<TextView>(R.id.condition_pattern).setTextColor(0xFFFF5252.toInt())
            } else {
                (view.tag as? Drawable)?.let { view.background = it }
                view.findViewById<TextView>(R.id.condition_pattern).setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        this@RuleEditActivity,
                        android.R.color.primary_text_dark,
                    ),
                )
            }

            view.setOnClickListener { onConditionTap(position) }
            view.setOnLongClickListener {
                editCondition(position)
                true
            }
            return view
        }
    }

    companion object {
        const val EXTRA_INDEX = "rule_index"
        private const val NEW_RULE = -1
        private const val CONFIRM_WINDOW_MS = 2000L
        private const val PENDING_COLOR = 0x33FF0000
    }
}
