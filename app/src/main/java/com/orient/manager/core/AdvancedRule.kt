package com.orient.manager.core

import androidx.annotation.StringRes
import com.orient.manager.R

enum class RuleField(
    @StringRes val labelRes: Int,
    val visible: Boolean = true,
) {
    ANY(R.string.rule_field_any),
    PACKAGE(R.string.rule_field_package),
    ACTIVITY(R.string.rule_field_activity),
    WINDOW_TITLE(R.string.rule_field_window_title),
    WINDOW_CLASS(R.string.rule_field_window_class),
    NODE_TEXT(R.string.rule_field_node_text),
    NODE_DESC(R.string.rule_field_node_desc),
    SCREEN_ID(R.string.rule_field_screen_id),
    NODE_CLASS(R.string.rule_field_node_class),
    SCREEN_TEXT(R.string.rule_field_screen_text, visible = false),
    ;

    companion object {
        val visibleEntries: List<RuleField> = entries.filter { it.visible }

        val legacyEntries: List<RuleField> = entries.filter { !it.visible }
    }

    fun valuesFor(snapshot: WindowSnapshot, ownerPackage: String): List<String> {
        val windows = snapshot.windowsExcept(ownerPackage)
        return when (this) {
            ANY -> snapshot.candidateValues(ownerPackage)
            PACKAGE -> buildList {
                snapshot.pkg?.let { add(it) }
                windows.forEach { it.pkg?.let { value -> add(value) } }
            }

            ACTIVITY -> listOfNotNull(snapshot.activityClass)
            WINDOW_TITLE -> windows.mapNotNull { it.title?.takeIf { value -> value.isNotBlank() } }
            WINDOW_CLASS -> windows.mapNotNull { it.rootClass }
            NODE_TEXT -> snapshot.nodes.mapNotNull { it.text?.takeIf { value -> value.isNotBlank() } }
            NODE_DESC -> snapshot.nodes.mapNotNull { it.desc?.takeIf { value -> value.isNotBlank() } }
            SCREEN_ID -> snapshot.nodes.mapNotNull { it.viewId }
            NODE_CLASS -> snapshot.nodes.mapNotNull { it.className }
            SCREEN_TEXT -> buildList {
                snapshot.nodes.forEach { node ->
                    node.text?.takeIf { it.isNotBlank() }?.let { add(it) }
                    node.desc?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }
    }
}

data class RuleCondition(
    val field: RuleField,
    val pattern: String,
    val negate: Boolean = false,
) {
    fun describe(): String =
        (if (negate) "不匹配 " else "匹配 ") + field.name + " " + pattern
}

data class AdvancedRule(
    val conditions: List<RuleCondition>,
    val mode: OrientationMode,
    val name: String = "",
) {
    fun summary(): String = conditions.joinToString(" 且 ") { it.describe() }

    fun title(): String = name.trim().ifBlank { summary() }
}
