package ru.inventory.dc.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.inventory.dc.BuildConfig
import ru.inventory.dc.CHANGELOG
import ru.inventory.dc.VersionNotes
import ru.inventory.dc.ui.theme.BrandColors

val APP_VERSION: String = BuildConfig.VERSION_NAME

private fun versionNumber(version: String): Int =
    version.split(".").map { it.toIntOrNull() ?: 0 }.let { p -> p.getOrElse(0) { 0 } * 10000 + p.getOrElse(1) { 0 } * 100 + p.getOrElse(2) { 0 } }

/** Версии, вышедшие после [lastSeen]; при первом запуске — только текущая. */
fun notesSince(lastSeen: String?): List<VersionNotes> {
    val current = versionNumber(APP_VERSION)
    if (lastSeen == null) return CHANGELOG.filter { versionNumber(it.version) == current }
    val seen = versionNumber(lastSeen)
    return CHANGELOG.filter { versionNumber(it.version) in (seen + 1)..current }
}

/** Какая версия уже показывалась в «Что нового». Не конфиденциально — обычные настройки. */
object WhatsNewPrefs {
    private const val PREFS = "app"
    private const val KEY = "last_seen_version"

    fun lastSeen(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, APP_VERSION).apply()
    }
}

@Composable
fun WhatsNewDialog(title: String, notes: List<VersionNotes>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                notes.forEach { v ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Версия ${v.version}", style = MaterialTheme.typography.titleSmall, color = BrandColors.DarkBlue)
                        v.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } },
    )
}
