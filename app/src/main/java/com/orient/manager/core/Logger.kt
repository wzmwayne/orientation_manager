package com.orient.manager.core

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

object Logger {

    private const val MAX_ENTRIES = 2000
    private const val MAX_FILE_BYTES = 512 * 1024L
    private const val KEEP_LINES_ON_TRIM = 1000

    private val entries = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val throttleAt = HashMap<String, Long>()
    private var logFile: File? = null
    private var appendCount = 0

    fun init(context: Context) {
        try {
            val file = File(context.filesDir, "orient_log.txt")
            logFile = file
            if (file.exists() && file.length() > 0) {
                val history = file.readLines().takeLast(MAX_ENTRIES)
                synchronized(entries) {
                    entries.clear()
                    history.forEach { entries.addLast(it) }
                }
            }
            append("I", "Log", "日志系统启动，已恢复历史 " + size() + " 行")
        } catch (t: Throwable) {
            logFile = null
        }
    }

    fun log(tag: String, message: String) = append("I", tag, message)

    fun warn(tag: String, message: String) = append("W", tag, message)

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        val detail = if (throwable == null) {
            message
        } else {
            message + " :: " + throwable.javaClass.simpleName + ": " + (throwable.message ?: "")
        }
        append("E", tag, detail)
    }

    fun logThrottled(tag: String, key: String, message: String, intervalMs: Long = 1000L) {
        val now = System.currentTimeMillis()
        val last = throttleAt[key] ?: 0L
        if (now - last < intervalMs) return
        throttleAt[key] = now
        append("I", tag, message)
    }

    fun snapshot(): String = synchronized(entries) {
        if (entries.isEmpty()) "" else entries.joinToString("\n")
    }

    fun size(): Int = synchronized(entries) { entries.size }

    fun clear() {
        synchronized(entries) { entries.clear() }
        throttleAt.clear()
        try {
            logFile?.delete()
        } catch (t: Throwable) {
            // ignore
        }
        append("I", "Log", "日志已清空")
    }

    private fun append(level: String, tag: String, message: String) {
        val line = timeFormat.format(Date()) + "  " + level + "/" + tag + "  " + message
        synchronized(entries) {
            entries.addLast(line)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
        writeToFile(line)
    }

    private fun writeToFile(line: String) {
        val file = logFile ?: return
        try {
            FileOutputStream(file, true).use { it.write((line + "\n").toByteArray()) }
            appendCount++
            if (appendCount % 200 == 0) trimIfNeeded(file)
        } catch (t: Throwable) {
            // disk write is best effort
        }
    }

    private fun trimIfNeeded(file: File) {
        try {
            if (file.length() <= MAX_FILE_BYTES) return
            val kept = file.readLines().takeLast(KEEP_LINES_ON_TRIM)
            file.writeText(kept.joinToString("\n") + "\n")
        } catch (t: Throwable) {
            // ignore
        }
    }
}
