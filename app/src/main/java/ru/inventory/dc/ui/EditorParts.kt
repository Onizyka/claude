package ru.inventory.dc.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import ru.inventory.dc.data.DictEntry
import ru.inventory.dc.data.PhotoStore
import ru.inventory.dc.data.SuggestionItem
import ru.inventory.dc.data.rankSuggestions
import ru.inventory.dc.data.hallLabel
import ru.inventory.dc.ui.theme.BrandColors

/**
 * Поле ввода с выпадающим списком подсказок. Можно выбрать из списка или ввести своё значение.
 * Подсказки ищутся без учёта регистра: «kr» → «Kraftway». Увеличение [expandRequest] раскрывает список.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<SuggestionItem>,
    onSelect: (SuggestionItem) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    expandRequest: Int = 0,
    onRemove: ((DictEntry) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(expandRequest) { if (expandRequest > 0) expanded = true }

    val filtered = remember(options, value) { rankSuggestions(options, value) }
    val showMenu = expanded && filtered.isNotEmpty()

    ExposedDropdownMenuBox(expanded = showMenu, onExpandedChange = { expanded = it }, modifier = modifier) {
        Field(
            value,
            {
                onValueChange(it)
                expanded = true
            },
            label = label,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable),
            placeholder = placeholder,
            capitalization = capitalization,
            trailingIcon = if (options.isNotEmpty()) {
                {
                    IconButton(onClick = { expanded = !expanded }) {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMenu)
                    }
                }
            } else null,
        )
        ExposedDropdownMenu(expanded = showMenu, onDismissRequest = { expanded = false }) {
            filtered.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.title, style = MaterialTheme.typography.bodyLarge)
                            option.subtitle?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    trailingIcon = if (option.entry != null && onRemove != null) {
                        {
                            IconButton(onClick = { onRemove(option.entry) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Забыть значение",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else null,
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** Плашка с текущим местом установки (площадка и помещение). */
@Composable
fun PlacementSummary(site: String, hall: String, hallTitle: String, onChange: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Место установки", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val text = listOf(site, hallLabel(hallTitle, hall)).filter { it.isNotBlank() }.joinToString(" · ")
                Text(
                    text.ifBlank { "Не выбрано" },
                    style = MaterialTheme.typography.titleMedium,
                    color = BrandColors.DarkBlue,
                )
            }
            TextButton(onClick = onChange) { Text("Изменить") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotosSection(vm: AppViewModel) {
    val context = LocalContext.current
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCapture
        pendingCapture = null
        if (ok && uri != null) vm.addPhotos(listOf(Uri.parse(uri)))
    }
    val launchCamera: () -> Unit = {
        val uri = vm.newCaptureUri()
        pendingCapture = uri.toString()
        takePicture.launch(uri)
    }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else vm.post("Нет доступа к камере")
    }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        vm.addPhotos(uris)
    }

    SectionCard("Фотографии", Icons.Filled.PhotoCamera) {
        val photos = vm.draft.photos
        if (photos.isEmpty() && !vm.processingPhotos) {
            Text(
                "Общий вид в стойке, шильдик, подключения — фото уйдут вложениями в письме",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                photos.forEach { name ->
                    key(name) {
                        PhotoThumb(
                            store = vm.photoStore,
                            name = name,
                            onClick = { viewing = name },
                            onRemove = { vm.removePhoto(name) },
                        )
                    }
                }
                if (vm.processingPhotos) {
                    Box(Modifier.size(THUMB_DP.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) launchCamera() else requestCamera.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Снять")
            }
            OutlinedButton(
                onClick = {
                    pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Галерея")
            }
        }
    }

    viewing?.let { name -> PhotoViewer(vm.photoStore, name, onDismiss = { viewing = null }) }
}

@Composable
private fun PhotoThumb(store: PhotoStore, name: String, onClick: () -> Unit, onRemove: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, name) {
        value = store.thumbnail(name, 256)?.asImageBitmap()
    }
    Box(
        Modifier
            .size(THUMB_DP.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        bitmap?.let {
            Image(it, contentDescription = "Фото", contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Удалить фото", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PhotoViewer(store: PhotoStore, name: String, onDismiss: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, name) {
        value = store.thumbnail(name, 1600)?.asImageBitmap()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let {
                Image(it, contentDescription = "Фото", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } ?: CircularProgressIndicator(color = Color.White)
        }
    }
}

private const val THUMB_DP = 96
private const val MAX_PICK = 10
