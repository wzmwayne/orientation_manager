package com.orient.manager.core

import androidx.annotation.StringRes
import com.orient.manager.R

enum class RuleField(@StringRes val labelRes: Int) {
    ANY(R.string.rule_field_any),
    PACKAGE(R.string.rule_field_package),
    ACTIVITY(R.string.rule_field_activity),
    WINDOW_TITLE(R.string.rule_field_window_title),
    WINDOW_CLASS(R.string.rule_field_window_class),
    ;

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
        }
    }
}

data class AdvancedRule(
    val pattern: String,
    val field: RuleField,
    val mode: OrientationMode,
)
