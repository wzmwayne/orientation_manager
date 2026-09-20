package com.orient.manager.ui

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.orient.manager.R
import com.orient.manager.core.AppRuleStore
import com.orient.manager.core.OrientationMode
import java.text.Collator
import java.util.Locale

class AppsActivity : AppCompatActivity() {

    private lateinit var store: AppRuleStore
    private lateinit var emptyView: TextView
    private lateinit var loadingView: CircularProgressIndicator
    private lateinit var searchView: EditText
    private lateinit var adapter: AppsAdapter

    private val allEntries = mutableListOf<AppEntry>()
    private val shown = mutableListOf<AppEntry>()
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)

        store = AppRuleStore(this)
        emptyView = findViewById(R.id.empty)
        loadingView = findViewById(R.id.loading)
        searchView = findViewById(R.id.search)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        adapter = AppsAdapter()
        findViewById<ListView>(R.id.apps).adapter = adapter

        searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim().orEmpty()
                applyFilter()
            }
        })

        loadAppsAsync()
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
    }

    private fun loadAppsAsync() {
        loadingView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        searchView.isEnabled = false

        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val collator = Collator.getInstance(Locale.getDefault())
            val loaded = pm.queryIntentActivities(intent, 0)
                .asSequence()
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != packageName }
                .map { AppEntry(it.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
                .toList()

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                allEntries.clear()
                allEntries.addAll(loaded)
                loadingView.visibility = View.GONE
                searchView.isEnabled = true
                applyFilter()
            }
        }.start()
    }

    private fun applyFilter() {
        shown.clear()
        if (query.isEmpty()) {
            shown.addAll(allEntries)
        } else {
            val q = query.lowercase(Locale.getDefault())
            allEntries.filter {
                it.label.lowercase(Locale.getDefault()).contains(q) ||
                    it.pkg.lowercase(Locale.getDefault()).contains(q)
            }.forEach { shown.add(it) }
        }
        emptyView.visibility =
            if (allEntries.isNotEmpty() && shown.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun pick(entry: AppEntry) {
        val modes: List<OrientationMode?> = listOf(null) + OrientationMode.entries
        val labels = modes
            .map { mode -> mode?.let { getString(it.labelRes) } ?: getString(R.string.rule_default) }
            .toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(entry.label)
            .setItems(labels) { _, which ->
                store.set(entry.pkg, modes[which])
                adapter.notifyDataSetChanged()
            }
            .show()
    }

    private inner class AppsAdapter : BaseAdapter() {

        override fun getCount(): Int = shown.size

        override fun getItem(position: Int): Any = shown[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView
                ?: LayoutInflater.from(this@AppsActivity).inflate(R.layout.app_row, parent, false)
            val entry = shown[position]
            view.findViewById<ImageView>(R.id.icon).setImageDrawable(entry.icon)
            view.findViewById<TextView>(R.id.title).text = entry.label
            val rule = store.modeOf(entry.pkg)
            view.findViewById<TextView>(R.id.subtitle).text =
                rule?.let { getString(it.labelRes) } ?: getString(R.string.rule_default)
            view.setOnClickListener { pick(entry) }
            return view
        }
    }

    private data class AppEntry(val pkg: String, val label: String, val icon: Drawable)
}
