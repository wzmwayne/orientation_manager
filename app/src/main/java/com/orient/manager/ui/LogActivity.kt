package com.orient.manager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.orient.manager.R
import com.orient.manager.core.Logger

class LogActivity : AppCompatActivity() {

    private lateinit var logView: TextView
    private lateinit var scrollView: ScrollView
    private val handler = Handler(Looper.getMainLooper())
    private var lastSize = -1

    private val refresher = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        logView = findViewById(R.id.log_text)
        scrollView = findViewById(R.id.log_scroll)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        findViewById<Button>(R.id.btn_copy).setOnClickListener { copyAll() }
        findViewById<Button>(R.id.btn_clear).setOnClickListener {
            Logger.clear()
            lastSize = -1
            refresh()
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(refresher, REFRESH_MS)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresher)
    }

    private fun refresh() {
        val size = Logger.size()
        if (size == lastSize) return
        lastSize = size
        val content = Logger.snapshot()
        logView.text = if (content.isEmpty()) getString(R.string.log_empty) else content
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun copyAll() {
        val content = Logger.snapshot()
        if (content.isEmpty()) {
            Toast.makeText(this, getString(R.string.log_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(getString(R.string.log_title), content))
        Toast.makeText(this, getString(R.string.log_copied), Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val REFRESH_MS = 700L
    }
}
