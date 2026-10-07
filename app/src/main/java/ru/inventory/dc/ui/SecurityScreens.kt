package ru.inventory.dc.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ru.inventory.dc.data.InventoryDatabase
import ru.inventory.dc.ui.theme.BrandColors

private const val MIN_PASSWORD_LENGTH = 8

/** Первый запуск: создать новую базу или открыть существующую. */
@Composable
fun DatabaseSetupScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pickExistingFile(uri)
    }
    val openFile = { pickFile.launch(arrayOf("application/octet-stream", "application/json", "*/*")) }

    when (vm.setupMode) {
        SetupMode.CHOOSE -> ChooseScreen(vm, snackbar, onOpen = openFile)
        SetupMode.CREATE -> CreateScreen(vm, snackbar)
        SetupMode.OPEN -> OpenScreen(vm, snackbar, onPickAnother = openFile)
    }
}

@Composable
private fun ChooseScreen(vm: AppViewModel, snackbar: SnackbarHostState, onOpen: () -> Unit) {
    AuthScaffold(snackbar = snackbar, title = "Инвентаризация ЦОД", subtitle = "Защищённая база оборудования") {
        Text(
            "Все данные хранятся в зашифрованной базе (AES-256). Открыть её можно только по паролю — " +
                "он же нужен для входа в приложение.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (vm.hasLegacyData) {
            InfoBanner("Найдены данные предыдущей версии — при создании базы они будут зашифрованы и перенесены.")
        }
        ChoiceTile(
            icon = Icons.Filled.Add,
            title = "Создать новую базу",
            text = "Выберите папку для файла базы и задайте пароль",
            onClick = { vm.setupMode = SetupMode.CREATE },
        )
        ChoiceTile(
            icon = Icons.Filled.FolderOpen,
            title = "Открыть существующую",
            text = "Файл ${InventoryDatabase.FILE_NAME} — например, после переустановки или с другого телефона",
            onClick = onOpen,
        )
        vm.authError?.let { InfoBanner(it, isError = true) }
    }
}

@Composable
private fun CreateScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
    var folder by rememberSaveable { mutableStateOf(vm.legacyFolder?.toString()) }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) folder = uri.toString()
    }

    val tooShort = password.isNotEmpty() && password.length < MIN_PASSWORD_LENGTH
    val mismatch = confirm.isNotEmpty() && confirm != password
    val canCreate = folder != null && password.length >= MIN_PASSWORD_LENGTH && password == confirm && !vm.busy

    AuthScaffold(
        snackbar = snackbar,
        title = "Новая база",
        subtitle = "Место хранения и пароль",
        onBack = vm::cancelSetup,
    ) {
        vm.pendingImport?.let {
            InfoBanner("Будут перенесены данные из копии: записей ${it.records.size}")
        }

        SectionCard("Где хранить базу", Icons.Filled.Folder) {
            Text(
                "Файл ${InventoryDatabase.FILE_NAME} останется в этой папке после удаления приложения — " +
                    "из него всё можно восстановить. Рекомендуется папка «Documents».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val folderName = folder?.let { folderLabel(Uri.parse(it)) }
            if (folderName != null) {
                InfoBanner("Папка: $folderName")
            }
            Button(
                onClick = { pickFolder.launch(InventoryDatabase.DEFAULT_FOLDER) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) { Text(if (folder == null) "Documents (по умолчанию)" else "Выбрать Documents") }
            OutlinedButton(
                onClick = { pickFolder.launch(null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Другая папка…") }
        }

        SectionCard("Пароль", Icons.Filled.Lock) {
            PasswordField(
                password, { password = it },
                label = "Пароль (не меньше $MIN_PASSWORD_LENGTH символов)",
                isError = tooShort,
                supportingText = if (tooShort) "Слишком короткий пароль" else null,
            )
            PasswordField(
                confirm, { confirm = it },
                label = "Повторите пароль",
                isError = mismatch,
                supportingText = if (mismatch) "Пароли не совпадают" else null,
                onDone = { if (canCreate) vm.createDatabase(password, folder?.let(Uri::parse)) },
            )
            InfoBanner(
                "Пароль невозможно восстановить. Если его забыть, данные базы прочитать не получится.",
                isError = true,
            )
        }

        vm.authError?.let { InfoBanner(it, isError = true) }

        Button(
            onClick = { vm.createDatabase(password, folder?.let(Uri::parse)) },
            enabled = canCreate,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (vm.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Шифрование…")
            } else {
                Text("Создать базу")
            }
        }
    }
}

@Composable
private fun OpenScreen(vm: AppViewModel, snackbar: SnackbarHostState, onPickAnother: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    val fileName = vm.openCandidate?.let(::folderLabel).orEmpty()

    AuthScaffold(
        snackbar = snackbar,
        title = "Открыть базу",
        subtitle = fileName,
        onBack = vm::cancelSetup,
    ) {
        Text("Введите пароль базы.", style = MaterialTheme.typography.bodyMedium)
        PasswordField(
            password, { password = it },
            label = "Пароль",
            onDone = { if (password.isNotEmpty()) vm.openDatabase(password) },
        )
        vm.authError?.let { InfoBanner(it, isError = true) }
        Button(
            onClick = { vm.openDatabase(password) },
            enabled = password.isNotEmpty() && !vm.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (vm.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Проверка…")
            } else {
                Text("Открыть")
            }
        }
        TextButton(onClick = onPickAnother) { Text("Выбрать другой файл") }
    }
}

/** Вход в приложение по паролю. */
@Composable
fun LockScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
    var password by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    val submit = { if (password.isNotEmpty()) vm.unlock(password) }

    AuthScaffold(snackbar = snackbar, title = "Инвентаризация ЦОД", subtitle = "Вход") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            }
        }
        Text(
            "База зашифрована. Введите пароль.",
            style = MaterialTheme.typography.titleMedium,
            color = BrandColors.DarkBlue,
        )
        PasswordField(password, { password = it }, label = "Пароль", onDone = submit)
        vm.authError?.let { InfoBanner(it, isError = true) }
        Button(
            onClick = submit,
            enabled = password.isNotEmpty() && !vm.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (vm.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Проверка…")
            } else {
                Text("Войти")
            }
        }
        TextButton(onClick = { confirmReset = true }) { Text("Забыли пароль?") }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Забыли пароль?") },
            text = {
                Text(
                    "Восстановить пароль невозможно — данные зашифрованы. Можно удалить базу с этого телефона " +
                        "и создать новую или открыть другой файл базы. Файл в папке хранения не удаляется."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetDatabase()
                }) { Text("Удалить с телефона", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } },
        )
    }
}

// ---------- Общие элементы ----------

@Composable
private fun AuthScaffold(
    snackbar: SnackbarHostState,
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            BrandTopBar(
                title = title,
                subtitle = subtitle,
                navigationIcon = if (onBack != null) {
                    {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                } else null,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
            Text(
                "Версия $APP_VERSION",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceTile(icon: ImageVector, title: String, text: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(BrandColors.DarkBlue, BrandColors.Blue))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = BrandColors.DarkBlue)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
    supportingText: String? = null,
    onDone: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = if (supportingText != null) {
            { Text(supportingText) }
        } else null,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Скрыть пароль" else "Показать пароль",
                )
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
        ),
    )
}

/** Человекочитаемый путь: «Documents» или «Documents/dc-inventory.dcdb». */
fun folderLabel(uri: Uri): String {
    val segments = uri.pathSegments
    val id = when {
        segments.size >= 4 && segments[2] == "document" -> segments[3]
        segments.size >= 2 && (segments[0] == "tree" || segments[0] == "document") -> segments[1]
        else -> uri.lastPathSegment ?: uri.toString()
    }
    return id.substringAfter(':').ifBlank { "Внутренняя память" }
}
