package com.orient.manager.core

import java.util.concurrent.atomic.AtomicInteger

object UiState {

    private val startedActivities = AtomicInteger(0)

    @Volatile
    var foreground: Boolean = false
        private set

    fun onActivityStarted() {
        foreground = startedActivities.incrementAndGet() > 0
    }

    fun onActivityStopped() {
        foreground = startedActivities.updateAndGet { if (it > 0) it - 1 else 0 } > 0
    }
}
