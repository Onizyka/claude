package ru.inventory.dc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.inventory.dc.data.EquipmentRecord
import ru.inventory.dc.data.displayTitle
import ru.inventory.dc.data.formatDateTime
import ru.inventory.dc.data.locationLine
import ru.inventory.dc.data.matches
import ru.inventory.dc.ui.theme.BrandColors

@Composable
fun HistoryScreen(
    vm: AppViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (EquipmentRecord) -> Unit,
) {
    val records by vm.records.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<EquipmentRecord?>(null) }
    val filtered = remember(records, query) { records.filter { it.matches(query) } }

    Scaffold(
        topBar = {
            BrandTopBar(
                title = "Журнал",
                subtitle = "Записей: ${records.size}",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Field(
                    query, { query = it },
                    label = "Поиск: S/N, IP, модель, стойка…",
                    leadingIcon = Icons.Filled.Search,
                )
            }
            if (filtered.isEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.Inventory2,
                            contentDescription = null,
                            tint = BrandColors.LightBlue,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            if (records.isEmpty()) "Журнал пуст. Сохранённые и отправленные записи появятся здесь."
                            else "Ничего не найдено",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(filtered, key = { it.id }) { record ->
                RecordCard(
                    record = record,
                    sending = vm.sending,
                    onClick = { onOpen(record) },
                    onResend = { vm.resend(record) },
                    onDelete = { toDelete = record },
                )
            }
        }
    }

    toDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить запись?") },
            text = { Text("«${record.displayTitle()}» будет удалена из журнала на телефоне. Отправленные письма не затрагиваются.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRecord(record.id)
                    toDelete = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordCard(
    record: EquipmentRecord,
    sending: Boolean,
    onClick: () -> Unit,
    onResend: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                Text(
                    record.displayTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    color = BrandColors.DarkBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(record.status)
            }
            if (record.type.isNotBlank()) {
                Text(record.type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(6.dp))
            record.locationLine().takeIf { it.isNotBlank() }?.let { IconLine(Icons.Filled.LocationOn, it) }
            record.mgmtIp.takeIf { it.isNotBlank() }?.let { IconLine(Icons.Filled.Lan, "MGMT: $it") }
            if (record.serials.isNotEmpty()) IconLine(Icons.Filled.QrCode2, "S/N: " + record.serials.joinToString(", "))
            if (record.photos.isNotEmpty()) IconLine(Icons.Filled.PhotoCamera, "Фото: ${record.photos.size}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDateTime(record.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onResend, enabled = !sending) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить ещё раз", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun IconLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp, horizontal = 0.dp)) {
        Icon(icon, contentDescription = null, tint = BrandColors.LightBlue, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}
