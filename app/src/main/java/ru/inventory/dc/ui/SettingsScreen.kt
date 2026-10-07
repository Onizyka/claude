package ru.inventory.dc.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.inventory.dc.CHANGELOG
import ru.inventory.dc.data.InventoryDatabase
import ru.inventory.dc.data.formatDateTime
import ru.inventory.dc.data.splitAddresses
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ru.inventory.dc.data.SmtpSecurity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    var s by remember { mutableStateOf(vm.currentSettings()) }
    var showPassword by remember { mutableStateOf(false) }

    // Сохраняем при уходе с экрана, чтобы изменения не потерялись.
    val latest by rememberUpdatedState(s)
    DisposableEffect(Unit) {
        onDispose { vm.saveSettings(latest, silent = true) }
    }

    Scaffold(
        topBar = {
            BrandTopBar(
                title = "Настройки",
                subtitle = "Отправка отчётов по e-mail",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { vm.sendTest(s) },
                        enabled = !vm.sending,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (vm.sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = LocalContentColor.current,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Тест")
                    }
                    Button(
                        onClick = { vm.saveSettings(s) },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Сохранить") }
                }
            }
        },
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
            SectionCard("SMTP-сервер", Icons.Filled.Dns) {
                Field(
                    s.host, { s = s.copy(host = it.trim()) },
                    label = "Адрес сервера",
                    placeholder = "smtp.company.ru",
                    keyboardType = KeyboardType.Uri,
                )
                Text("Шифрование", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = SmtpSecurity.entries
                    options.forEachIndexed { index, security ->
                        SegmentedButton(
                            selected = s.security == security,
                            onClick = {
                                // Если порт стандартный — подставляем стандартный порт нового режима.
                                val defaultPorts = SmtpSecurity.entries.map { it.defaultPort.toString() }
                                val port = if (s.port.isBlank() || s.port in defaultPorts) security.defaultPort.toString() else s.port
                                s = s.copy(security = security, port = port)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(security.title) }
                    }
                }
                Field(
                    s.port, { s = s.copy(port = it.filter(Char::isDigit).take(5)) },
                    label = "Порт",
                    keyboardType = KeyboardType.Number,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Не проверять сертификат", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Для внутренних серверов с самоподписанным сертификатом",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = s.trustAllCerts, onCheckedChange = { s = s.copy(trustAllCerts = it) })
                }
            }

            SectionCard("Авторизация", Icons.Filled.Key) {
                Field(
                    s.username, { s = s.copy(username = it) },
                    label = "Логин",
                    supportingText = "Оставьте пустым, если сервер не требует авторизации",
                    keyboardType = KeyboardType.Email,
                )
                Field(
                    s.password, { s = s.copy(password = it) },
                    label = "Пароль",
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Скрыть пароль" else "Показать пароль",
                            )
                        }
                    },
                )
            }

            SectionCard("Письмо", Icons.Filled.Email) {
                Field(
                    s.fromAddress, { s = s.copy(fromAddress = it.trim()) },
                    label = "Адрес отправителя",
                    placeholder = "inventory@company.ru",
                    supportingText = "Если пусто — используется логин",
                    keyboardType = KeyboardType.Email,
                )
                AddressListEditor(
                    title = "Получатель",
                    value = s.recipients,
                    onChange = { s = s.copy(recipients = it) },
                    addLabel = "Добавить получателя",
                )
                AddressListEditor(
                    title = "Копия (CC)",
                    value = s.cc,
                    onChange = { s = s.copy(cc = it) },
                    addLabel = "Добавить копию",
                    startEmpty = true,
                )
                Field(
                    s.subjectPrefix, { s = s.copy(subjectPrefix = it) },
                    label = "Префикс темы письма",
                )
            }

            DatabaseSection(vm, onLock = {
                vm.saveSettings(s, silent = true)
                vm.lockNow()
            })

            AboutSection()

            Spacer(Modifier.height(8.dp))
        }
    }
}

private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** Список адресов: по одному полю на адрес, количество не ограничено. */
@Composable
private fun AddressListEditor(
    title: String,
    value: String,
    onChange: (String) -> Unit,
    addLabel: String,
    startEmpty: Boolean = false,
) {
    var rows by remember {
        mutableStateOf(splitAddresses(value).ifEmpty { if (startEmpty) emptyList() else listOf("") })
    }
    fun update(newRows: List<String>) {
        rows = newRows
        onChange(newRows.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", "))
    }
    rows.forEachIndexed { index, address ->
        val invalid = address.isNotBlank() && !emailRegex.matches(address.trim())
        Field(
            address,
            { v -> update(rows.toMutableList().also { it[index] = v.trim() }) },
            label = if (rows.size > 1) "$title ${index + 1}" else title,
            placeholder = "name@company.ru",
            keyboardType = KeyboardType.Email,
            isError = invalid,
            supportingText = if (invalid) "Проверьте адрес" else null,
            trailingIcon = if (rows.size > 1 || address.isNotEmpty() || startEmpty) {
                {
                    IconButton(onClick = { update(rows.toMutableList().also { it.removeAt(index) }) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Удалить адрес")
                    }
                }
            } else null,
        )
    }
    TextButton(onClick = { rows = rows + "" }) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(addLabel)
    }
}

@Composable
private fun DatabaseSection(vm: AppViewModel, onLock: () -> Unit) {
    val location by vm.dbLocation.collectAsStateWithLifecycle()
    var changePassword by remember { mutableStateOf(false) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.moveDatabase(uri)
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.exportCopy(uri)
    }

    SectionCard("База данных", Icons.Filled.Lock) {
        Text(
            "Журнал, настройки почты (включая пароль SMTP), место установки и справочник хранятся " +
                "в зашифрованной базе (AES-256). Фото и черновик тоже зашифрованы. Резервирование Android отключено.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (location.fileUri == null) {
            InfoBanner("Файл базы хранится только внутри приложения. Выберите папку, чтобы база пережила переустановку.", isError = true)
        } else {
            val synced = location.lastSyncAt?.let { "сохранено ${formatDateTime(it)}" } ?: "ещё не сохранялась"
            InfoBanner(
                text = "Файл: ${location.displayPath ?: InventoryDatabase.FILE_NAME}\n$synced" +
                    (location.lastError?.let { "\nОшибка: $it" } ?: ""),
                isError = location.lastError != null,
            )
        }
        OutlinedButton(
            onClick = { pickFolder.launch(InventoryDatabase.DEFAULT_FOLDER) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Сохранить базу в другую папку")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { changePassword = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Сменить пароль") }
            OutlinedButton(
                onClick = { exportFile.launch(InventoryDatabase.FILE_NAME) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Копия базы") }
        }
        Button(
            onClick = onLock,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Заблокировать")
        }
        Text(
            "Приложение блокируется само, если было свёрнуто дольше 5 минут.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (changePassword) {
        ChangePasswordDialog(vm, onDismiss = { changePassword = false })
    }
}

@Composable
private fun ChangePasswordDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val tooShort = new.isNotEmpty() && new.length < 8
    val mismatch = confirm.isNotEmpty() && confirm != new
    val canSave = old.isNotEmpty() && new.length >= 8 && new == confirm && !vm.busy

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Смена пароля") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(old, { old = it }, label = "Текущий пароль")
                PasswordField(
                    new, { new = it },
                    label = "Новый пароль (от 8 символов)",
                    isError = tooShort,
                    supportingText = if (tooShort) "Слишком короткий" else null,
                )
                PasswordField(
                    confirm, { confirm = it },
                    label = "Повторите новый пароль",
                    isError = mismatch,
                    supportingText = if (mismatch) "Пароли не совпадают" else null,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.changePassword(old, new) { ok -> if (ok) onDismiss() } },
                enabled = canSave,
            ) { Text(if (vm.busy) "Шифрование…" else "Сменить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun AboutSection() {
    var showChangelog by remember { mutableStateOf(false) }
    SectionCard("О приложении", Icons.Filled.Info) {
        Text("Инвентаризация ЦОД, версия $APP_VERSION", style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(
            onClick = { showChangelog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) { Text("История версий") }
    }
    if (showChangelog) {
        WhatsNewDialog("История версий", CHANGELOG) { showChangelog = false }
    }
}
