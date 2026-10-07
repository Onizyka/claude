package ru.inventory.dc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.inventory.dc.data.DEFAULT_HALL_TITLE
import ru.inventory.dc.data.HALL_TITLES
import ru.inventory.dc.data.Placement
import ru.inventory.dc.data.addHall
import ru.inventory.dc.data.addRack
import ru.inventory.dc.data.addSite
import ru.inventory.dc.data.editSite
import ru.inventory.dc.data.findHall
import ru.inventory.dc.data.findSite
import ru.inventory.dc.data.hallLabel
import ru.inventory.dc.data.removeHall
import ru.inventory.dc.data.removeRack
import ru.inventory.dc.data.removeSite
import ru.inventory.dc.data.renameHall
import ru.inventory.dc.data.renameRack
import ru.inventory.dc.ui.theme.BrandColors

private enum class PlaceKind { SITE, HALL, RACK }

/** Открытый диалог: добавление ([original] = null) или изменение элемента справочника. */
private data class PlaceDialog(val kind: PlaceKind, val original: String? = null)

/**
 * «Где устанавливается оборудование?» — выбор из справочника мест, который ведёт пользователь.
 * Площадки, помещения и стойки добавляются кнопкой «Добавить», долгое нажатие — изменить или удалить.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlacementScreen(
    vm: AppViewModel,
    initial: Placement,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onDone: (Placement) -> Unit,
) {
    val sites by vm.sites.collectAsStateWithLifecycle()
    var siteName by rememberSaveable { mutableStateOf(initial.site) }
    var hallName by rememberSaveable { mutableStateOf(initial.hall) }
    var rackName by rememberSaveable { mutableStateOf(initial.rack) }
    var dialog by remember { mutableStateOf<PlaceDialog?>(null) }

    val site = sites.findSite(siteName)
    val hall = site?.findHall(hallName)
    val hallTitle = site?.hallTitle ?: DEFAULT_HALL_TITLE

    Scaffold(
        topBar = {
            BrandTopBar(
                title = "Место установки",
                subtitle = "Задаётся один раз для серии устройств",
                navigationIcon = if (canGoBack) {
                    {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                } else null,
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Button(
                    onClick = {
                        onDone(
                            Placement(
                                site = site?.name ?: "",
                                hall = hall?.name ?: "",
                                rack = if (hall != null) rackName else "",
                                hallTitle = hallTitle,
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Продолжить") }
            }
        },
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
            Text("Где будет стоять оборудование?", style = MaterialTheme.typography.headlineSmall, color = BrandColors.DarkBlue)
            Text(
                "Площадка и помещение подставятся во все следующие записи. Список мест вы ведёте сами: " +
                    "«+ Добавить» — новое место, долгое нажатие — переименовать или удалить.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard("Площадка", Icons.Filled.Apartment) {
                if (sites.isEmpty()) {
                    Text(
                        "Пока нет ни одной площадки — добавьте первую (здание, ЦОД, адрес).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sites.forEach { s ->
                        PlaceChip(
                            text = s.name,
                            selected = s.id == site?.id,
                            onClick = {
                                if (s.id != site?.id) {
                                    siteName = s.name
                                    hallName = ""
                                    rackName = ""
                                }
                            },
                            onLongClick = { dialog = PlaceDialog(PlaceKind.SITE, s.name) },
                        )
                    }
                    AddChip { dialog = PlaceDialog(PlaceKind.SITE) }
                }
            }

            if (site != null) {
                SectionCard(hallTitle, Icons.Filled.MeetingRoom) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        site.halls.forEach { h ->
                            PlaceChip(
                                text = hallLabel(hallTitle, h.name),
                                selected = h.id == hall?.id,
                                onClick = {
                                    if (h.id != hall?.id) {
                                        hallName = h.name
                                        rackName = ""
                                    }
                                },
                                onLongClick = { dialog = PlaceDialog(PlaceKind.HALL, h.name) },
                            )
                        }
                        AddChip { dialog = PlaceDialog(PlaceKind.HALL) }
                    }
                }
            }

            if (site != null && hall != null) {
                SectionCard("Стойка (необязательно)", Icons.Filled.Dns) {
                    Text(
                        "Если всё оборудование серии ставится в одну стойку — выберите её. Иначе стойку можно указать в каждой записи.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        hall.racks.forEach { r ->
                            PlaceChip(
                                text = r,
                                selected = r.equals(rackName, ignoreCase = true),
                                onClick = { rackName = if (r.equals(rackName, ignoreCase = true)) "" else r },
                                onLongClick = { dialog = PlaceDialog(PlaceKind.RACK, r) },
                            )
                        }
                        AddChip { dialog = PlaceDialog(PlaceKind.RACK) }
                    }
                }
            }

            val summary = Placement(site?.name ?: "", hall?.name ?: "", if (hall != null) rackName else "", hallTitle).summary()
            if (summary.isNotBlank()) InfoBanner(text = summary)
            Spacer(Modifier.height(8.dp))
        }
    }

    val d = dialog
    if (d != null) {
        when (d.kind) {
            PlaceKind.SITE -> {
                val editing = d.original?.let { sites.findSite(it) }
                PlaceEditDialog(
                    title = if (editing == null) "Новая площадка" else "Площадка",
                    label = "Название площадки",
                    initialName = editing?.name.orEmpty(),
                    initialHallTitle = editing?.hallTitle ?: DEFAULT_HALL_TITLE,
                    askHallTitle = true,
                    exists = { name -> sites.any { it.id != editing?.id && it.name.equals(name.trim(), ignoreCase = true) } },
                    onSave = { name, title ->
                        if (editing == null) {
                            vm.editSites { it.addSite(name, title) }
                        } else {
                            vm.editSites { it.editSite(editing.id, name, title) }
                        }
                        siteName = name.trim()
                        if (editing == null) {
                            hallName = ""
                            rackName = ""
                        }
                        dialog = null
                    },
                    onDelete = editing?.let {
                        {
                            vm.editSites { list -> list.removeSite(it.id) }
                            if (it.id == site?.id) {
                                siteName = ""
                                hallName = ""
                                rackName = ""
                            }
                            dialog = null
                        }
                    },
                    deleteNote = "Удалятся и все её помещения и стойки из списка. Записи в журнале не изменятся.",
                    onDismiss = { dialog = null },
                )
            }
            PlaceKind.HALL -> if (site != null) {
                val editing = d.original?.let { site.findHall(it) }
                PlaceEditDialog(
                    title = if (editing == null) "Новый: ${hallTitle.lowercase()}" else hallTitle,
                    label = "Номер или название",
                    initialName = editing?.name.orEmpty(),
                    exists = { name -> site.halls.any { it.id != editing?.id && it.name.equals(name.trim(), ignoreCase = true) } },
                    onSave = { name, _ ->
                        if (editing == null) {
                            vm.editSites { it.addHall(site.id, name) }
                        } else {
                            vm.editSites { it.renameHall(site.id, editing.id, name) }
                        }
                        hallName = name.trim()
                        if (editing == null) rackName = ""
                        dialog = null
                    },
                    onDelete = editing?.let {
                        {
                            vm.editSites { list -> list.removeHall(site.id, it.id) }
                            if (it.id == hall?.id) {
                                hallName = ""
                                rackName = ""
                            }
                            dialog = null
                        }
                    },
                    deleteNote = "Удалятся и все его стойки из списка. Записи в журнале не изменятся.",
                    onDismiss = { dialog = null },
                )
            }
            PlaceKind.RACK -> if (site != null && hall != null) {
                val editing = d.original
                PlaceEditDialog(
                    title = if (editing == null) "Новая стойка" else "Стойка",
                    label = "Номер или название стойки",
                    initialName = editing.orEmpty(),
                    capitalization = KeyboardCapitalization.Characters,
                    exists = { name -> hall.racks.any { !it.equals(editing, ignoreCase = true) && it.equals(name.trim(), ignoreCase = true) } },
                    onSave = { name, _ ->
                        if (editing == null) {
                            vm.editSites { it.addRack(site.id, hall.id, name) }
                        } else {
                            vm.editSites { it.renameRack(site.id, hall.id, editing, name) }
                        }
                        rackName = name.trim()
                        dialog = null
                    },
                    onDelete = editing?.let {
                        {
                            vm.editSites { list -> list.removeRack(site.id, hall.id, it) }
                            if (it.equals(rackName, ignoreCase = true)) rackName = ""
                            dialog = null
                        }
                    },
                    deleteNote = "Записи в журнале не изменятся.",
                    onDismiss = { dialog = null },
                )
            }
        }
    }
}

/** Добавление или изменение площадки, помещения или стойки. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceEditDialog(
    title: String,
    label: String,
    initialName: String,
    exists: (String) -> Boolean,
    onSave: (name: String, hallTitle: String) -> Unit,
    onDelete: (() -> Unit)?,
    deleteNote: String,
    onDismiss: () -> Unit,
    initialHallTitle: String = DEFAULT_HALL_TITLE,
    askHallTitle: Boolean = false,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    var name by remember { mutableStateOf(initialName) }
    var hallTitle by remember { mutableStateOf(initialHallTitle) }
    var confirmDelete by remember { mutableStateOf(false) }
    val duplicate = name.isNotBlank() && exists(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(
                    name, { name = it },
                    label = label,
                    capitalization = capitalization,
                    isError = duplicate,
                    supportingText = if (duplicate) "Такое название уже есть" else null,
                )
                if (askHallTitle) {
                    Text("Как называются помещения", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        HALL_TITLES.forEachIndexed { i, t ->
                            SegmentedButton(
                                selected = hallTitle == t,
                                onClick = { hallTitle = t },
                                shape = SegmentedButtonDefaults.itemShape(i, HALL_TITLES.size),
                            ) { Text(t) }
                        }
                    }
                }
                if (onDelete != null) {
                    if (confirmDelete) {
                        InfoBanner(deleteNote, isError = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { confirmDelete = false }) { Text("Не удалять") }
                            TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                        }
                    } else {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("Удалить из списка", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, hallTitle) },
                enabled = name.isNotBlank() && !duplicate,
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Элемент списка мест: нажатие — выбрать, долгое нажатие — изменить. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceChip(text: String, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else BrandColors.DarkBlue,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .heightIn(min = 44.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun AddChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.heightIn(min = 44.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Добавить", style = MaterialTheme.typography.titleSmall)
        }
    }
}
