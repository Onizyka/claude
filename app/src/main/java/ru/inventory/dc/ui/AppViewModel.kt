package ru.inventory.dc.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.inventory.dc.data.EquipmentRecord
import ru.inventory.dc.data.PortConnection
import ru.inventory.dc.data.RecordRepository
import ru.inventory.dc.data.SendStatus
import ru.inventory.dc.data.SettingsRepository
import ru.inventory.dc.data.SmtpSettings
import ru.inventory.dc.data.content
import ru.inventory.dc.data.forEditing
import ru.inventory.dc.data.formatDateTime
import ru.inventory.dc.data.hasData
import ru.inventory.dc.data.normalized
import ru.inventory.dc.mail.MailSender
import ru.inventory.dc.mail.ReportBuilder

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val recordRepo = RecordRepository(app)
    private val settingsRepo = SettingsRepository(app)

    val records: StateFlow<List<EquipmentRecord>> = recordRepo.records
    val settings: StateFlow<SmtpSettings> = settingsRepo.settings

    /** Запись, открытая в редакторе. */
    var draft by mutableStateOf(EquipmentRecord.empty())
        private set

    var sending by mutableStateOf(false)
        private set

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private fun post(message: String) {
        _messages.trySend(message)
    }

    // ---------- Редактор ----------

    fun updateDraft(transform: (EquipmentRecord) -> EquipmentRecord) {
        draft = transform(draft)
    }

    /** Новая запись; при [keepLocation] сохраняются площадка, помещение и стойка. */
    fun newRecord(keepLocation: Boolean) {
        val old = draft
        val fresh = EquipmentRecord.empty()
        draft = if (keepLocation) fresh.copy(site = old.site, room = old.room, rack = old.rack) else fresh
    }

    fun openRecord(record: EquipmentRecord) {
        draft = record.forEditing()
    }

    fun removeSerial(index: Int) = updateDraft { r ->
        val list = r.serials.toMutableList()
        if (index in list.indices) list.removeAt(index)
        r.copy(serials = list.ifEmpty { listOf("") })
    }

    fun addConnection() = updateDraft { r ->
        // Чаще всего подряд подключают порты к одному и тому же устройству — подставляем его.
        val lastDevice = r.connections.lastOrNull()?.remoteDevice.orEmpty()
        r.copy(connections = r.connections + PortConnection(remoteDevice = lastDevice))
    }

    fun updateConnection(connection: PortConnection) = updateDraft { r ->
        r.copy(connections = r.connections.map { if (it.id == connection.id) connection else it })
    }

    fun removeConnection(id: String) = updateDraft { r ->
        r.copy(connections = r.connections.filterNot { it.id == id })
    }

    fun applyScan(target: String, value: String) {
        when {
            target == SCAN_INVENTORY -> updateDraft { it.copy(inventoryNumber = value) }

            target == SCAN_SERIAL_NEW -> {
                if (draft.serials.any { it.trim().equals(value, ignoreCase = true) }) {
                    post("Серийный номер $value уже добавлен")
                    return
                }
                updateDraft { r ->
                    val list = r.serials.toMutableList()
                    val emptyIndex = list.indexOfFirst { it.isBlank() }
                    if (emptyIndex >= 0) list[emptyIndex] = value else list += value
                    r.copy(serials = list)
                }
                post("Добавлен S/N: $value")
            }

            target.startsWith(SCAN_SERIAL_PREFIX) -> {
                val index = target.removePrefix(SCAN_SERIAL_PREFIX).toIntOrNull() ?: return
                updateDraft { r ->
                    if (index in r.serials.indices) {
                        r.copy(serials = r.serials.toMutableList().also { it[index] = value })
                    } else {
                        r.copy(serials = r.serials + value)
                    }
                }
            }
        }
    }

    fun saveDraft() {
        val record = draft.normalized()
        if (!record.hasData()) {
            post("Форма пустая — нечего сохранять")
            return
        }
        viewModelScope.launch {
            val stored = records.value.firstOrNull { it.id == record.id }
            val saved = if (stored != null && stored.content() == record.content()) {
                stored
            } else {
                record.copy(updatedAt = System.currentTimeMillis(), status = SendStatus.DRAFT, lastError = null)
            }
            recordRepo.upsert(saved)
            draft = saved.forEditing()
            post("Запись сохранена в журнал")
        }
    }

    // ---------- Отправка ----------

    fun send() {
        val record = draft.normalized()
        if (!record.hasData()) {
            post("Заполните хотя бы одно поле")
            return
        }
        deliver(record) { saved, success ->
            if (success) {
                // Следующее устройство обычно ставят в ту же стойку — место оставляем.
                newRecord(keepLocation = true)
            } else if (draft.id == saved.id) {
                draft = saved.forEditing()
            }
        }
    }

    fun resend(record: EquipmentRecord) {
        deliver(record.normalized()) { saved, _ ->
            if (draft.id == saved.id) draft = saved.forEditing()
        }
    }

    private fun deliver(record: EquipmentRecord, onDone: (EquipmentRecord, Boolean) -> Unit) {
        if (sending) return
        val smtp = settings.value
        if (!smtp.isConfigured) {
            post("Укажите SMTP-сервер, отправителя и получателя в настройках")
            return
        }
        sending = true
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    MailSender.send(
                        settings = smtp,
                        subject = ReportBuilder.subject(record, smtp.subjectPrefix),
                        text = ReportBuilder.text(record, now),
                        html = ReportBuilder.html(record, now),
                    )
                }
            }
            val saved = result.fold(
                onSuccess = { record.copy(updatedAt = now, status = SendStatus.SENT, sentAt = now, lastError = null) },
                onFailure = { record.copy(updatedAt = now, status = SendStatus.FAILED, lastError = MailSender.describeError(it)) },
            )
            recordRepo.upsert(saved)
            sending = false
            onDone(saved, result.isSuccess)
            post(
                if (result.isSuccess) "Письмо отправлено: ${smtp.recipients}"
                else "Не удалось отправить: ${saved.lastError}"
            )
        }
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch {
            recordRepo.delete(id)
            if (draft.id == id) newRecord(keepLocation = true)
            post("Запись удалена")
        }
    }

    // ---------- Настройки ----------

    fun saveSettings(newSettings: SmtpSettings, silent: Boolean = false) {
        settingsRepo.save(newSettings)
        if (!silent) post("Настройки сохранены")
    }

    fun sendTest(newSettings: SmtpSettings) {
        settingsRepo.save(newSettings)
        if (sending) return
        if (!newSettings.isConfigured) {
            post("Заполните сервер, отправителя и получателя")
            return
        }
        sending = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    MailSender.send(
                        settings = newSettings,
                        subject = "${newSettings.subjectPrefix} Тестовое письмо".trim(),
                        text = "Тестовое письмо из приложения «Инвентаризация ЦОД».\n" +
                            "Отправлено: ${formatDateTime(System.currentTimeMillis())}\n" +
                            "Сервер: ${newSettings.host}:${newSettings.port} (${newSettings.security.title})",
                        html = null,
                    )
                }
            }
            sending = false
            post(
                result.fold(
                    onSuccess = { "Тестовое письмо отправлено" },
                    onFailure = { "Ошибка: ${MailSender.describeError(it)}" },
                )
            )
        }
    }

    companion object {
        const val SCAN_INVENTORY = "inventory"
        const val SCAN_SERIAL_NEW = "serial:new"
        const val SCAN_SERIAL_PREFIX = "serial:"
    }
}
