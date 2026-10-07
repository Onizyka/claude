package ru.inventory.dc.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ru.inventory.dc.data.EQUIPMENT_TYPES
import ru.inventory.dc.data.CABLE_TYPES
import ru.inventory.dc.data.DictCategory
import ru.inventory.dc.data.DictEntry
import ru.inventory.dc.data.PortConnection
import ru.inventory.dc.data.SuggestionItem
import ru.inventory.dc.data.suggestions
import ru.inventory.dc.data.VENDORS
import ru.inventory.dc.data.findVendor
import ru.inventory.dc.data.SendStatus
import ru.inventory.dc.data.formatDateTime
import ru.inventory.dc.data.hasOwnData
import ru.inventory.dc.data.isValidIp

private fun scanOptions(): ScanOptions = ScanOptions().apply {
    setPrompt("Наведите камеру на QR-код или штрихкод")
    setBeepEnabled(true)
    setOrientationLocked(false)
    setCaptureActivity(ScannerActivity::class.java)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    vm: AppViewModel,
    snackbar: SnackbarHostState,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onChangePlacement: () -> Unit,
) {
    val draft = vm.draft
    val settings by vm.settings.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val isExisting = records.any { it.id == draft.id }

    var confirmNew by remember { mutableStateOf(false) }
    var modelExpandRequest by remember { mutableIntStateOf(0) }
    val dictionary by vm.dictionary.collectAsStateWithLifecycle()
    // Каталог + запомненные значения; часто используемые предлагаются первыми.
    val typeOptions = remember(dictionary) {
        EQUIPMENT_TYPES.map { SuggestionItem(it) } + dictionary.suggestions(DictCategory.TYPE)
    }
    val vendorOptions = remember(dictionary) {
        dictionary.suggestions(DictCategory.VENDOR) + VENDORS.map { SuggestionItem(it.name) }
    }
    val presets = findVendor(draft.vendor)?.models.orEmpty()
    val modelOptions = remember(dictionary, draft.vendor) {
        presets.map { SuggestionItem(it.name, it.description, weight = 1000) } +
            dictionary.suggestions(DictCategory.MODEL, parent = draft.vendor.takeIf { it.isNotBlank() })
    }
    val deviceOptions = remember(dictionary) { dictionary.suggestions(DictCategory.DEVICE) }
    val cableOptions = remember(dictionary) { dictionary.suggestions(DictCategory.CABLE) }

    var scanTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val value = result.contents?.trim().orEmpty()
        val target = scanTarget
        scanTarget = null
        if (value.isNotEmpty() && target != null) vm.applyScan(target, value)
    }
    val scan: (String) -> Unit = { target ->
        scanTarget = target
        scanLauncher.launch(scanOptions())
    }

    Scaffold(
        topBar = {
            BrandTopBar(
                title = "Инвентаризация ЦОД",
                subtitle = if (isExisting) "Редактирование записи" else "Новое оборудование",
                actions = {
                    IconButton(onClick = {
                        if (draft.hasOwnData()) confirmNew = true else vm.newRecord(keepRack = true)
                    }) { Icon(Icons.Filled.NoteAdd, contentDescription = "Новая запись") }
                    IconButton(onClick = onOpenHistory) { Icon(Icons.Filled.History, contentDescription = "Журнал") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Настройки") }
                },
            )
        },
        bottomBar = { EditorBottomBar(sending = vm.sending, onSave = vm::saveDraft, onSend = vm::send) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!settings.isConfigured) {
                InfoBanner(
                    text = "Отправка почты не настроена",
                    actionLabel = "Настроить",
                    onAction = onOpenSettings,
                )
            }
            when (draft.status) {
                SendStatus.FAILED -> InfoBanner(
                    text = "Отправка не удалась: ${draft.lastError.orEmpty()}",
                    isError = true,
                )
                SendStatus.SENT -> InfoBanner(
                    text = "Отправлено ${draft.sentAt?.let(::formatDateTime).orEmpty()}. Изменения можно отправить повторно.",
                )
                SendStatus.DRAFT -> Unit
            }

            // ---------- Размещение ----------
            SectionCard("Размещение", Icons.Filled.LocationOn) {
                PlacementSummary(site = draft.site, hall = draft.room, onChange = onChangePlacement)
                Field(
                    draft.rack, { v -> vm.updateDraft { it.copy(rack = v) } },
                    label = "Стойка",
                    capitalization = KeyboardCapitalization.Characters,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(
                        draft.unit, { v -> vm.updateDraft { it.copy(unit = v) } },
                        label = "Юнит",
                        placeholder = "12",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                    )
                    Field(
                        draft.heightU, { v -> vm.updateDraft { it.copy(heightU = v) } },
                        label = "Высота, U",
                        placeholder = "2",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                    )
                }
            }

            // ---------- Оборудование ----------
            SectionCard("Оборудование", Icons.Filled.Dns) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EQUIPMENT_TYPES.forEach { type ->
                        FilterChip(
                            selected = draft.type == type,
                            onClick = { vm.updateDraft { it.copy(type = if (it.type == type) "" else type) } },
                            label = { Text(type) },
                        )
                    }
                }
                SuggestField(
                    value = draft.type,
                    onValueChange = { v -> vm.updateDraft { it.copy(type = v) } },
                    label = "Тип оборудования",
                    options = typeOptions,
                    onSelect = { option -> vm.updateDraft { it.copy(type = option.title) } },
                    capitalization = KeyboardCapitalization.Sentences,
                    onRemove = vm::forgetSuggestion,
                )
                SuggestField(
                    value = draft.vendor,
                    onValueChange = { v -> vm.updateDraft { it.copy(vendor = v) } },
                    label = "Производитель",
                    placeholder = "Начните вводить: kr → Kraftway",
                    options = vendorOptions,
                    onSelect = { option ->
                        vm.updateDraft { it.copy(vendor = option.title) }
                        // У производителя есть модели — сразу раскрываем список.
                        val hasModels = findVendor(option.title)?.models?.isNotEmpty() == true ||
                            dictionary.suggestions(DictCategory.MODEL, option.title).isNotEmpty()
                        if (hasModels) modelExpandRequest++
                    },
                    capitalization = KeyboardCapitalization.Words,
                    onRemove = vm::forgetSuggestion,
                )
                SuggestField(
                    value = draft.model,
                    onValueChange = { v -> vm.updateDraft { it.copy(model = v) } },
                    label = "Модель",
                    options = modelOptions,
                    onSelect = { option ->
                        val preset = presets.firstOrNull { it.name == option.title }
                        if (preset != null) vm.applyModel(preset) else vm.updateDraft { it.copy(model = option.title) }
                    },
                    expandRequest = modelExpandRequest,
                    onRemove = vm::forgetSuggestion,
                )
                Field(
                    draft.hostname, { v -> vm.updateDraft { it.copy(hostname = v) } },
                    label = "Hostname / имя",
                    keyboardType = KeyboardType.Uri,
                )
            }

            // ---------- MGMT ----------
            SectionCard("MGMT-интерфейс", Icons.Filled.Lan) {
                val ipValid = isValidIp(draft.mgmtIp)
                Field(
                    draft.mgmtIp, { v -> vm.updateDraft { it.copy(mgmtIp = v) } },
                    label = "IP-адрес MGMT",
                    placeholder = "10.0.0.10",
                    keyboardType = KeyboardType.Decimal,
                    isError = !ipValid,
                    supportingText = if (!ipValid) "Проверьте формат IP-адреса" else null,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(
                        draft.mgmtMask, { v -> vm.updateDraft { it.copy(mgmtMask = v) } },
                        label = "Маска / префикс",
                        placeholder = "/24",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Uri,
                    )
                    val gwValid = isValidIp(draft.mgmtGateway)
                    Field(
                        draft.mgmtGateway, { v -> vm.updateDraft { it.copy(mgmtGateway = v) } },
                        label = "Шлюз",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Decimal,
                        isError = !gwValid,
                    )
                }
            }

            // ---------- Серийные номера ----------
            SectionCard("Серийный и инвентарный номер", Icons.Filled.QrCode2) {
                draft.serials.forEachIndexed { index, serial ->
                    Field(
                        serial,
                        { v -> vm.updateDraft { r -> r.copy(serials = r.serials.toMutableList().also { it[index] = v }) } },
                        label = if (draft.serials.size > 1) "Серийный номер ${index + 1}" else "Серийный номер",
                        capitalization = KeyboardCapitalization.Characters,
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { scan(AppViewModel.SCAN_SERIAL_PREFIX + index) }) {
                                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "Сканировать")
                                }
                                if (draft.serials.size > 1 || serial.isNotEmpty()) {
                                    IconButton(onClick = { vm.removeSerial(index) }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Удалить")
                                    }
                                }
                            }
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { vm.updateDraft { it.copy(serials = it.serials + "") } },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Добавить")
                    }
                    Button(
                        onClick = { scan(AppViewModel.SCAN_SERIAL_NEW) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Сканировать")
                    }
                }
                Field(
                    draft.inventoryNumber, { v -> vm.updateDraft { it.copy(inventoryNumber = v) } },
                    label = "Инвентарный номер",
                    capitalization = KeyboardCapitalization.Characters,
                    trailingIcon = {
                        IconButton(onClick = { scan(AppViewModel.SCAN_INVENTORY) }) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Сканировать")
                        }
                    },
                )
            }

            // ---------- Фотографии ----------
            PhotosSection(vm)

            // ---------- Подключения ----------
            SectionCard("Подключения", Icons.Filled.Cable) {
                if (draft.connections.isEmpty()) {
                    Text(
                        "Укажите, какой порт этого устройства к какому порту и какого оборудования подключён",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                draft.connections.forEachIndexed { index, connection ->
                    androidx.compose.runtime.key(connection.id) {
                        ConnectionEditor(
                            index = index,
                            connection = connection,
                            onChange = vm::updateConnection,
                            onRemove = { vm.removeConnection(connection.id) },
                            deviceOptions = deviceOptions,
                            cableOptions = cableOptions,
                            onForget = vm::forgetSuggestion,
                        )
                    }
                }
                OutlinedButton(
                    onClick = vm::addConnection,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Добавить подключение")
                }
            }

            // ---------- Комментарий ----------
            SectionCard("Комментарий", Icons.Filled.EditNote) {
                Field(
                    draft.comment, { v -> vm.updateDraft { it.copy(comment = v) } },
                    label = "Комментарий",
                    singleLine = false,
                    minLines = 3,
                    capitalization = KeyboardCapitalization.Sentences,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text("Новая запись") },
            text = { Text("Очистить форму? ЦОД, машзал и стойка останутся.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.newRecord(keepRack = true)
                    confirmNew = false
                }) { Text("Очистить") }
            },
            dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionEditor(
    index: Int,
    connection: PortConnection,
    onChange: (PortConnection) -> Unit,
    onRemove: () -> Unit,
    deviceOptions: List<SuggestionItem>,
    cableOptions: List<SuggestionItem>,
    onForget: (DictEntry) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Подключение ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Удалить подключение", tint = MaterialTheme.colorScheme.error)
                }
            }
            Field(
                connection.localPort, { onChange(connection.copy(localPort = it)) },
                label = "Порт этого устройства",
                placeholder = "NIC1, eth0, iLO",
            )
            SuggestField(
                value = connection.remoteDevice,
                onValueChange = { onChange(connection.copy(remoteDevice = it)) },
                label = "Подключено к устройству",
                placeholder = "SW-CORE-01",
                options = deviceOptions,
                onSelect = { onChange(connection.copy(remoteDevice = it.title)) },
                onRemove = onForget,
            )
            Field(
                connection.remotePort, { onChange(connection.copy(remotePort = it)) },
                label = "Порт на устройстве",
                placeholder = "Gi1/0/24",
            )

            // Кабель: медь / оптика / свой вариант
            val preset = CABLE_TYPES.firstOrNull { it.equals(connection.cableType, ignoreCase = true) }
            var custom by rememberSaveable(connection.id) {
                mutableStateOf(connection.cableType.isNotBlank() && preset == null)
            }
            Text("Кабель", style = MaterialTheme.typography.labelLarge)
            val choices = CABLE_TYPES + "Другой"
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                choices.forEachIndexed { i, title ->
                    val isCustom = i == CABLE_TYPES.size
                    val selected = if (isCustom) custom else !custom && preset == title
                    SegmentedButton(
                        selected = selected,
                        onClick = {
                            if (isCustom) {
                                custom = true
                                if (preset != null) onChange(connection.copy(cableType = ""))
                            } else {
                                custom = false
                                // Повторное нажатие снимает выбор — поле необязательное.
                                onChange(connection.copy(cableType = if (selected) "" else title))
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                    ) { Text(title) }
                }
            }
            if (custom) {
                SuggestField(
                    value = connection.cableType,
                    onValueChange = { onChange(connection.copy(cableType = it)) },
                    label = "Свой тип кабеля",
                    placeholder = "DAC, AOC, силовой C13-C14…",
                    options = cableOptions,
                    onSelect = { onChange(connection.copy(cableType = it.title)) },
                    onRemove = onForget,
                )
            }
            Field(
                connection.note, { onChange(connection.copy(note = it)) },
                label = "Примечание / маркировка",
                placeholder = "2 м, метка C-015",
            )
        }
    }
}

@Composable
private fun EditorBottomBar(sending: Boolean, onSave: () -> Unit, onSend: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onSave,
                enabled = !sending,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Сохранить")
            }
            Button(
                onClick = onSend,
                enabled = !sending,
                modifier = Modifier
                    .weight(1.4f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(if (sending) "Отправка…" else "Отправить")
            }
        }
    }
}
