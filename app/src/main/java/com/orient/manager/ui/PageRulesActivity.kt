package com.orient.manager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.orient.manager.R
import com.orient.manager.core.ActivityInspector
import com.orient.manager.core.Logger
import com.orient.manager.core.OrientationMode
import com.orient.manager.core.PageRuleStore
import com.orient.manager.service.OrientationAccessibilityService

class PageRulesActivity : AppCompatActivity() {

    private lateinit var store: PageRuleStore
    private lateinit var adapter: RulesAdapter
    private lateinit var emptyView: TextView
    private lateinit var currentView: TextView
    private lateinit var input: EditText
    private lateinit var enableSwitch: MaterialSwitch

    private val entries = mutableListOf<Pair<String, OrientationMode>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pages)

        store = PageRuleStore(this)
        emptyView = findViewById(R.id.pages_empty)
        currentView = findViewById(R.id.pages_current)
        input = findViewById(R.id.pages_input)
        enableSwitch = findViewById(R.id.pages_enable)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        enableSwitch.setOnCheckedChangeListener { _, checked ->
            store.enabled = checked
            Logger.log("UI", "页面规则启用 = " + checked)
            refresh()
        }

        val inspector = findViewById<MaterialSwitch>(R.id.pages_inspector)
        inspector.isChecked = ActivityInspector.isEnabled(this)
        inspector.setOnCheckedChangeListener { _, checked ->
            ActivityInspector.setEnabled(this, checked)
        }

        findViewById<Button>(R.id.pages_detect).setOnClickListener {
            val service = OrientationAccessibilityService.instance
            if (service == null) {
                Toast.makeText(this, getString(R.string.pages_current_none), Toast.LENGTH_SHORT).show()
            } else {
                service.requestActiveDetection("用户手动点击")
                Toast.makeText(this, getString(R.string.pages_detect), Toast.LENGTH_SHORT).show()
            }
            currentView.postDelayed({ refresh() }, 300)
        }

        findViewById<Button>(R.id.pages_add).setOnClickListener {
            val pattern = input.text.toString().trim()
            if (pattern.isEmpty()) {
                Toast.makeText(this, getString(R.string.pages_invalid), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            chooseMode(pattern, null)
        }

        adapter = RulesAdapter()
        findViewById<ListView>(R.id.pages_list).adapter = adapter
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        entries.clear()
        entries.addAll(store.all().toList())
        enableSwitch.isChecked = store.enabled
        emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        val pkg = OrientationAccessibilityService.lastPackage
        val cls = OrientationAccessibilityService.lastActivityClass
        currentView.text = if (pkg.isNullOrEmpty() && cls.isNullOrEmpty()) {
            getString(R.string.pages_current_none)
        } else {
            getString(R.string.pages_current, (pkg ?: "") + " / " + (cls ?: ""))
        }
        adapter.notifyDataSetChanged()
    }

    private fun chooseMode(pattern: String, existing: OrientationMode?) {
        val modes = OrientationMode.entries
        val labels = modes.map { getString(it.labelRes) }.toMutableList()
        if (existing != null) labels.add(getString(R.string.pages_delete))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pages_choose_mode)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < modes.size) {
                    store.set(pattern, modes[which])
                    Logger.log("UI", "页面规则 " + pattern + " -> " + modes[which].name)
                    input.setText("")
                } else {
                    store.set(pattern, null)
                    Logger.log("UI", "删除页面规则 " + pattern)
                }
                refresh()
            }
            .show()
    }

    private inner class RulesAdapter : BaseAdapter() {

        override fun getCount(): Int = entries.size

        override fun getItem(position: Int): Any = entries[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView
                ?: LayoutInflater.from(this@PageRulesActivity)
                    .inflate(R.layout.page_rule_row, parent, false)
            val entry = entries[position]
            view.findViewById<TextView>(R.id.title).text = entry.first
            view.findViewById<TextView>(R.id.subtitle).text = getString(entry.second.labelRes)
            view.setOnClickListener { chooseMode(entry.first, entry.second) }
            return view
        }
    }
}
