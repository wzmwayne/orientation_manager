package com.orient.manager.core

data class WindowInfo(
    val id: Int,
    val type: Int,
    val layer: Int,
    val pkg: String?,
    val rootClass: String?,
    val title: String?,
    val focused: Boolean,
    val active: Boolean,
)

data class WindowSnapshot(
    val pkg: String?,
    val activityClass: String?,
    val windows: List<WindowInfo>,
) {
    fun windowsExcept(ownerPackage: String?): List<WindowInfo> =
        windows.filter { it.pkg == null || it.pkg != ownerPackage }

    fun candidateValues(ownerPackage: String?): List<String> {
        val values = LinkedHashSet<String>()
        pkg?.let { values.add(it) }
        activityClass?.let { values.add(it) }
        windowsExcept(ownerPackage).forEach { window ->
            window.pkg?.let { values.add(it) }
            window.rootClass?.let { values.add(it) }
            window.title?.takeIf { it.isNotBlank() }?.let { values.add(it) }
        }
        return values.toList()
    }

    fun candidateEntries(ownerPackage: String?): List<String> {
        val entries = ArrayList<String>()
        pkg?.let { entries.add("包名: " + it) }
        activityClass?.let { entries.add("活动: " + it) }
        windowsExcept(ownerPackage).forEachIndexed { index, window ->
            val parts = ArrayList<String>()
            parts.add("类型" + window.type)
            window.layer.let { parts.add("层" + it) }
            window.rootClass?.let { parts.add(it) }
            window.title?.takeIf { it.isNotBlank() }?.let { parts.add("标题=" + it) }
            window.pkg?.let { parts.add(it) }
            if (window.focused) parts.add("焦点")
            entries.add("窗口" + (index + 1) + ": " + parts.joinToString(" / "))
        }
        return entries
    }

    fun signature(ownerPackage: String?): String {
        val windowsKey = windowsExcept(ownerPackage)
            .map { it.type.toString() + ":" + (it.rootClass ?: "-") }
            .sorted()
            .joinToString(",")
        return (pkg ?: "-") + "|" + (activityClass ?: "-") + "|" + windowsKey
    }
}
