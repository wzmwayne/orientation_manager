package com.orient.manager.core

import android.graphics.Rect

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

data class NodeInfo(
    val className: String?,
    val text: String?,
    val desc: String?,
    val viewId: String?,
    val pkg: String?,
    val bounds: Rect?,
    val clickable: Boolean,
    val depth: Int,
) {
    fun label(): String {
        val parts = ArrayList<String>()
        className?.let { parts.add(simpleName(it)) }
        text?.takeIf { it.isNotBlank() }?.let { parts.add("文本=" + it) }
        desc?.takeIf { it.isNotBlank() }?.let { parts.add("描述=" + it) }
        viewId?.let { parts.add("id=" + it.substringAfterLast('/')) }
        return parts.joinToString(" / ")
    }

    fun detail(): String = buildString {
        append("类名: ").append(className ?: "-").append('\n')
        append("文本: ").append(text ?: "-").append('\n')
        append("描述: ").append(desc ?: "-").append('\n')
        append("id: ").append(viewId ?: "-").append('\n')
        append("包名: ").append(pkg ?: "-").append('\n')
        append("可点击: ").append(clickable).append("  深度: ").append(depth).append('\n')
        bounds?.let { append("边界: ").append(it.toShortString()) }
    }

    private fun simpleName(value: String): String = value.substringAfterLast('.')
}

data class WindowSnapshot(
    val pkg: String?,
    val activityClass: String?,
    val windows: List<WindowInfo>,
    val nodes: List<NodeInfo>,
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
        nodes.forEach { node ->
            node.className?.let { values.add(it) }
            node.text?.takeIf { it.isNotBlank() }?.let { values.add(it) }
            node.desc?.takeIf { it.isNotBlank() }?.let { values.add(it) }
            node.viewId?.let { values.add(it) }
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
            parts.add("层" + window.layer)
            window.rootClass?.let { parts.add(simple(it)) }
            window.title?.takeIf { value -> value.isNotBlank() }?.let { parts.add("标题=" + it) }
            window.pkg?.let { parts.add(it) }
            if (window.focused) parts.add("焦点")
            entries.add("窗口" + (index + 1) + ": " + parts.joinToString(" / "))
        }
        entries.add("节点: " + nodes.size + " 个（可匹配 文本/描述/id/类名）")
        return entries
    }

    fun signature(ownerPackage: String?): String {
        val windowsKey = windowsExcept(ownerPackage)
            .map { it.type.toString() + ":" + (it.rootClass ?: "-") }
            .sorted()
            .joinToString(",")
        val nodesKey = nodes.take(40).joinToString(",") {
            (it.className ?: "-") + ":" + (it.viewId ?: "-")
        }
        return (pkg ?: "-") + "|" + (activityClass ?: "-") + "|" + windowsKey + "|" + nodesKey
    }

    private fun simple(value: String): String = value.substringAfterLast('.')
}
