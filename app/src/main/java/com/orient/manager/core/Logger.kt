package com.orient.manager.core

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object Logger {

    private const val MAX_ENTRIES = 2000
    private const val MAX_FILE_BYTES = 512 * 1024L
    private const val KEEP_LINES_ON_TRIM = 1000
    private const val TRIM_EVERY_APPENDS = 200

    private val entries = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val timeLock = Any()
    private val throttleAt = HashMap<String, Long>()

    private val pendingLines = ConcurrentLinkedQueue<String>()
    private val flushing = AtomicBoolean(false)
    private val io: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "orient-log").apply { isDaemon = true }
    }

    @Volatile
    private var logFile: File? = null
    private var appendCount = 0

    fun init(context: Context) {
        val file = File(context.filesDir, "orient_log.txt")
        logFile = file
        io.execute { restoreHistory(file) }
        append("I", "Log", "日志系统启动")
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
        val last = synchronized(throttleAt) { throttleAt[key] ?: 0L }
        if (now - last < intervalMs) return
        synchronized(throttleAt) { throttleAt[key] = now }
        append("I", tag, message)
    }

    fun snapshot(): String = synchronized(entries) {
        if (entries.isEmpty()) "" else entries.joinToString("\n")
    }

    fun size(): Int = synchronized(entries) { entries.size }

    fun clear() {
        synchronized(entries) { entries.clear() }
        synchronized(throttleAt) { throttleAt.clear() }
        pendingLines.clear()
        io.execute {
            try {
                logFile?.delete()
            } catch (t: Throwable) {
                // ignore
            }
        }
        append("I", "Log", "日志已清空")
    }

    private fun append(level: String, tag: String, message: String) {
        val line = timestamp() + "  " + level + "/" + tag + "  " + message
        synchronized(entries) {
            entries.addLast(line)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
        enqueue(line)
    }

    private fun timestamp(): String = synchronized(timeLock) { timeFormat.format(Date()) }

    private fun restoreHistory(file: File) {
        try {
            if (!file.exists() || file.length() == 0L) return
            val history = file.readLines().takeLast(MAX_ENTRIES)
            synchronized(entries) {
                if (entries.isNotEmpty()) return
                history.forEach { entries.addLast(it) }
            }
        } catch (t: Throwable) {
            // history is best effort
        }
    }

    private fun enqueue(line: String) {
        pendingLines.add(line)
        if (!flushing.compareAndSet(false, true)) return
        try {
            io.execute { flush() }
        } catch (t: Throwable) {
            flushing.set(false)
        }
    }

    private fun flush() {
        val batch = StringBuilder()
        var count = 0
        while (true) {
            val line = pendingLines.poll() ?: break
            batch.append(line).append('\n')
            count++
        }
        val file = logFile
        if (file != null && count > 0) {
            try {
                FileOutputStream(file, true).use { it.write(batch.toString().toByteArray()) }
                appendCount += count
                if (appendCount >= TRIM_EVERY_APPENDS) {
                    appendCount = 0
                    trimIfNeeded(file)
                }
            } catch (t: Throwable) {
                // disk write is best effort
            }
        }
        flushing.set(false)
        if (pendingLines.isNotEmpty() && flushing.compareAndSet(false, true)) {
            try {
                io.execute { flush() }
            } catch (t: Throwable) {
                flushing.set(false)
            }
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
