package ru.inventory.dc.ui

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
    var s by remember { mutableStateOf(vm.settings.value) }
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
                Field(
                    s.recipients, { s = s.copy(recipients = it) },
                    label = "Получатели",
                    placeholder = "admin@company.ru",
                    supportingText = "Несколько адресов — через запятую",
                    keyboardType = KeyboardType.Email,
                    singleLine = false,
                )
                Field(
                    s.subjectPrefix, { s = s.copy(subjectPrefix = it) },
                    label = "Префикс темы письма",
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
