package ru.inventory.dc.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.inventory.dc.data.DatabaseContent
import ru.inventory.dc.data.DbLocation
import ru.inventory.dc.data.DbState
import ru.inventory.dc.data.DictEntry
import ru.inventory.dc.data.EquipmentRecord
import ru.inventory.dc.data.InventoryDatabase
import ru.inventory.dc.data.LegacyData
import ru.inventory.dc.data.ModelPreset
import ru.inventory.dc.data.NotADatabaseException
import ru.inventory.dc.data.PhotoStore
import ru.inventory.dc.data.Placement
import ru.inventory.dc.data.PortConnection
import ru.inventory.dc.data.SendStatus
import ru.inventory.dc.data.Site
import ru.inventory.dc.data.SmtpSettings
import ru.inventory.dc.data.VaultCrypto
import ru.inventory.dc.data.WrongPasswordException
import ru.inventory.dc.data.content
import ru.inventory.dc.data.forEditing
import ru.inventory.dc.data.forget
import ru.inventory.dc.data.formatDateTime
import ru.inventory.dc.data.hasOwnData
import ru.inventory.dc.data.learn
import ru.inventory.dc.data.learnPlace
import ru.inventory.dc.data.sitesFromHistory
import ru.inventory.dc.data.normalized
import ru.inventory.dc.mail.MailSender
import ru.inventory.dc.mail.ReportBuilder

/** Шаг первичной настройки: выбор, создание новой базы или открытие существующей. */
enum class SetupMode { CHOOSE, CREATE, OPEN }

@OptIn(kotlinx.coroutines.FlowPreview::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = InventoryDatabase(app, viewModelScope)
    private val legacy = LegacyData(app)
    val photoStore = PhotoStore(app, db)

    val dbState: StateFlow<DbState> = db.state
    val dbLocation: StateFlow<DbLocation> = db.location

    val records: StateFlow<List<EquipmentRecord>> = db.content.map { it.records }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<SmtpSettings> = db.content.map { it.settings }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SmtpSettings())
    val placement: StateFlow<Placement> = db.content.map { it.placement }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Placement())
    val dictionary: StateFlow<List<DictEntry>> = db.content.map { it.dictionary }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sites: StateFlow<List<Site>> = db.content.map { it.sites }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Актуальные значения напрямую из базы (без задержки производных потоков). */
    fun currentPlacement(): Placement = db.content.value.placement
    fun currentSettings(): SmtpSettings = db.content.value.settings

    // ---------- Состояние экранов входа ----------

    var setupMode by mutableStateOf(SetupMode.CHOOSE)
    var busy by mutableStateOf(false)
        private set
    var authError by mutableStateOf<String?>(null)

    /** Выбранный файл базы, для которого ждём пароль. */
    var openCandidate by mutableStateOf<Uri?>(null)
        private set

    /** Данные незашифрованной копии старой версии, которые перенесём в новую базу. */
    var pendingImport by mutableStateOf<DatabaseContent?>(null)
        private set

    val hasLegacyData: Boolean get() = legacy.exists()
    val legacyFolder: Uri? get() = legacy.backupFolder

    // ---------- Редактор ----------

    var draft by mutableStateOf(EquipmentRecord.empty())
        private set

    var sending by mutableStateOf(false)
        private set

    var processingPhotos by mutableStateOf(false)
        private set

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var backgroundSince = 0L

    init {
        // Черновик сохраняется (в зашифрованном виде), даже если система выгрузит приложение.
        viewModelScope.launch {
            snapshotFlow { draft }.drop(1).debounce(400).collect {
                if (db.isUnlocked) db.saveDraft(it)
            }
        }
    }

    fun post(message: String) {
        _messages.trySend(message)
    }

    // ---------- Создание / открытие / вход ----------

    fun createDatabase(password: String, folder: Uri?) {
        if (busy) return
        busy = true
        authError = null
        viewModelScope.launch {
            val migrateLocal = legacy.exists()
            val initial = pendingImport ?: if (migrateLocal) withContext(Dispatchers.IO) { legacy.load() } else DatabaseContent()
            runCatching { db.create(password.toCharArray(), folder, initial) }
                .onSuccess {
                    if (migrateLocal) migrateLegacyPhotos()
                    withContext(Dispatchers.IO) { legacy.wipe() }
                    val imported = pendingImport != null || migrateLocal
                    pendingImport = null
                    onUnlocked()
                    if (imported) post("Данные перенесены в зашифрованную базу: записей ${db.content.value.records.size}")
                }
                .onFailure { authError = "Не удалось создать базу: ${it.message}" }
            busy = false
        }
    }

    /** Пользователь выбрал файл: база — спросим пароль; старая незашифрованная копия — перенесём. */
    fun pickExistingFile(uri: Uri) {
        authError = null
        viewModelScope.launch {
            val bytes = runCatching { db.readUri(uri) }.getOrElse {
                authError = "Не удалось открыть файл: ${it.message}"
                return@launch
            }
            if (VaultCrypto.isDatabase(bytes)) {
                openCandidate = uri
                setupMode = SetupMode.OPEN
                return@launch
            }
            val old = legacy.parseLegacyBackup(bytes)
            if (old != null) {
                pendingImport = old
                setupMode = SetupMode.CREATE
                post("Найдена незашифрованная копия (записей: ${old.records.size}). Задайте пароль — данные перейдут в новую базу.")
            } else {
                authError = NotADatabaseException().message
            }
        }
    }

    fun openDatabase(password: String) {
        val uri = openCandidate ?: return
        if (busy) return
        busy = true
        authError = null
        viewModelScope.launch {
            runCatching {
                val bytes = db.readUri(uri)
                db.open(uri, bytes, password.toCharArray())
            }.onSuccess {
                if (legacy.exists()) {
                    // На телефоне остались данные старой версии — добавляем их в открытую базу.
                    val old = withContext(Dispatchers.IO) { legacy.load() }
                    db.update { c ->
                        c.copy(
                            records = mergeRecords(c.records, old.records),
                            dictionary = old.records.fold(c.dictionary) { d, r -> d.learn(r) },
                        )
                    }
                    migrateLegacyPhotos()
                    withContext(Dispatchers.IO) { legacy.wipe() }
                }
                openCandidate = null
                onUnlocked()
            }.onFailure {
                authError = when (it) {
                    is WrongPasswordException -> "Неверный пароль"
                    else -> "Не удалось открыть базу: ${it.message}"
                }
            }
            busy = false
        }
    }

    fun unlock(password: String) {
        if (busy) return
        busy = true
        authError = null
        viewModelScope.launch {
            val ok = runCatching { db.unlock(password.toCharArray()) }.getOrElse {
                authError = "Ошибка чтения базы: ${it.message}"
                busy = false
                return@launch
            }
            if (ok) {
                onUnlocked()
            } else {
                authError = "Неверный пароль"
            }
            busy = false
        }
    }

    fun lockNow() {
        viewModelScope.launch {
            if (db.isUnlocked) db.saveDraft(draft)
            db.lock()
            draft = EquipmentRecord.empty()
            authError = null
        }
    }

    /** «Забыли пароль»: локальная база удаляется, можно создать новую или открыть файл. */
    fun resetDatabase() {
        viewModelScope.launch {
            db.resetLocal()
            photoStore.deleteAll()
            draft = EquipmentRecord.empty()
            setupMode = SetupMode.CHOOSE
            authError = null
        }
    }

    fun cancelSetup() {
        setupMode = SetupMode.CHOOSE
        openCandidate = null
        pendingImport = null
        authError = null
    }

    fun onAppBackground() {
        backgroundSince = SystemClock.elapsedRealtime()
    }

    /** Автоблокировка, если приложение было свёрнуто дольше [AUTO_LOCK_MS]. */
    fun onAppForeground() {
        val since = backgroundSince
        backgroundSince = 0L
        if (since > 0 && db.isUnlocked && SystemClock.elapsedRealtime() - since > AUTO_LOCK_MS) lockNow()
    }

    private suspend fun onUnlocked() {
        val c = db.content.value
        draft = db.loadDraft()?.forEditing() ?: blankDraft(keepRack = false)
        if (c.sites.isEmpty() && (c.placement.site.isNotBlank() || c.records.any { it.site.isNotBlank() })) {
            db.update { it.copy(sites = sitesFromHistory(it.placement, it.records)) }
        }
        if (c.dictionary.isEmpty() && c.records.isNotEmpty()) {
            db.update { it.copy(dictionary = it.records.fold(it.dictionary) { d, r -> d.learn(r) }) }
        }
        val used = db.content.value.records.flatMap { it.photos }.toSet() + draft.photos
        photoStore.cleanup(keep = used)
    }

    private suspend fun migrateLegacyPhotos() {
        val renamed = photoStore.encryptLegacyPhotos()
        if (renamed.isEmpty()) return
        db.update { c ->
            c.copy(records = c.records.map { r -> r.copy(photos = r.photos.map { renamed[it] ?: it }) })
        }
    }

    private fun mergeRecords(current: List<EquipmentRecord>, incoming: List<EquipmentRecord>): List<EquipmentRecord> {
        val byId = current.associateBy { it.id }.toMutableMap()
        incoming.forEach { r ->
            val existing = byId[r.id]
            if (existing == null || r.updatedAt > existing.updatedAt) byId[r.id] = r
        }
        return byId.values.sortedByDescending { it.updatedAt }
    }

    private fun List<EquipmentRecord>.upsert(record: EquipmentRecord): List<EquipmentRecord> =
        (filterNot { it.id == record.id } + record).sortedByDescending { it.updatedAt }

    /** Запись в журнал + пополнение справочников оборудования и мест. */
    private fun DatabaseContent.withRecord(record: EquipmentRecord): DatabaseContent = copy(
        records = records.upsert(record),
        dictionary = dictionary.learn(record),
        sites = sites.learnPlace(record.site, record.room, record.rack, record.roomTitle),
    )

    /** Изменение справочника мест (экран «Место установки»). */
    fun editSites(transform: (List<Site>) -> List<Site>) {
        db.update { it.copy(sites = transform(it.sites)) }
    }

    // ---------- Управление базой (настройки) ----------

    fun changePassword(old: String, new: String, onDone: (Boolean) -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val ok = runCatching { db.changePassword(old.toCharArray(), new.toCharArray()) }.getOrDefault(false)
            busy = false
            onDone(ok)
            post(if (ok) "Пароль изменён" else "Текущий пароль указан неверно")
        }
    }

    fun moveDatabase(folder: Uri) {
        viewModelScope.launch {
            runCatching { db.moveTo(folder) }
                .onSuccess { post("База сохранена в новую папку") }
                .onFailure { post("Не удалось сохранить базу в папку: ${it.message}") }
        }
    }

    fun exportCopy(uri: Uri) {
        viewModelScope.launch {
            runCatching { db.exportCopy(uri) }
                .onSuccess { post("Копия базы сохранена (тот же пароль)") }
                .onFailure { post("Ошибка: ${it.message}") }
        }
    }

    // ---------- Редактор ----------

    private fun blankDraft(keepRack: Boolean, previous: EquipmentRecord? = null): EquipmentRecord {
        val p = currentPlacement()
        val rack = if (keepRack && previous != null && previous.rack.isNotBlank()) previous.rack else p.rack
        return EquipmentRecord.empty().copy(site = p.site, room = p.hall, roomTitle = p.hallTitle, rack = rack)
    }

    fun updateDraft(transform: (EquipmentRecord) -> EquipmentRecord) {
        draft = transform(draft)
    }

    /** Новая запись: площадка и помещение берутся из места установки; [keepRack] — оставить стойку. */
    fun newRecord(keepRack: Boolean) {
        draft = blankDraft(keepRack, previous = draft)
    }

    fun setPlacement(p: Placement) {
        val saved = p.trimmed().copy(chosen = true)
        db.update {
            it.copy(placement = saved, sites = it.sites.learnPlace(saved.site, saved.hall, saved.rack, saved.hallTitle))
        }
        updateDraft {
            it.copy(site = saved.site, room = saved.hall, roomTitle = saved.hallTitle, rack = saved.rack.ifBlank { it.rack })
        }
    }

    fun applyModel(preset: ModelPreset) = updateDraft { r ->
        r.copy(
            model = preset.name,
            type = r.type.ifBlank { preset.type },
            heightU = preset.heightU?.toString() ?: r.heightU,
        )
    }

    fun newCaptureUri(): Uri = photoStore.newCaptureUri()

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            processingPhotos = true
            val names = uris.mapNotNull { photoStore.import(it) }
            photoStore.clearCaptures()
            processingPhotos = false
            if (names.isNotEmpty()) updateDraft { it.copy(photos = it.photos + names) }
            if (names.size < uris.size) post("Не удалось добавить фото: ${uris.size - names.size} шт.")
        }
    }

    fun removePhoto(name: String) = updateDraft { r -> r.copy(photos = r.photos - name) }

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
        val last = r.connections.lastOrNull()
        r.copy(
            connections = r.connections + PortConnection(
                remoteDevice = last?.remoteDevice.orEmpty(),
                cableType = last?.cableType.orEmpty(),
            )
        )
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
        if (!record.hasOwnData()) {
            post("Форма пустая — нечего сохранять")
            return
        }
        val stored = db.content.value.records.firstOrNull { it.id == record.id }
        val saved = if (stored != null && stored.content() == record.content()) {
            stored
        } else {
            record.copy(updatedAt = System.currentTimeMillis(), status = SendStatus.DRAFT, lastError = null)
        }
        db.update { it.withRecord(saved) }
        draft = saved.forEditing()
        post("Запись сохранена в базу")
    }

    // ---------- Отправка ----------

    fun send() {
        val record = draft.normalized()
        if (!record.hasOwnData()) {
            post("Заполните хотя бы одно поле")
            return
        }
        deliver(record) { saved, success ->
            if (success) {
                // Следующее устройство обычно ставят в ту же стойку — место оставляем.
                newRecord(keepRack = true)
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
        val smtp = currentSettings()
        if (!smtp.isConfigured) {
            post("Укажите SMTP-сервер, отправителя и получателя в настройках")
            return
        }
        sending = true
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val attachments = photoStore.prepareMailFiles(record.photos)
            val result = try {
                withContext(Dispatchers.IO) {
                    runCatching {
                        MailSender.send(
                            settings = smtp,
                            subject = ReportBuilder.subject(record, smtp.subjectPrefix, now),
                            text = ReportBuilder.text(record, now),
                            html = ReportBuilder.html(record, now),
                            attachments = attachments,
                        )
                    }
                }
            } finally {
                // Расшифрованные копии фото не оставляем на диске.
                photoStore.clearMailFiles()
            }
            val saved = result.fold(
                onSuccess = { record.copy(updatedAt = now, status = SendStatus.SENT, sentAt = now, lastError = null) },
                onFailure = { record.copy(updatedAt = now, status = SendStatus.FAILED, lastError = MailSender.describeError(it)) },
            )
            db.update { it.withRecord(saved) }
            sending = false
            onDone(saved, result.isSuccess)
            post(
                if (result.isSuccess) "Письмо отправлено (получателей: ${smtp.recipientCount})"
                else "Не удалось отправить: ${saved.lastError}"
            )
        }
    }

    fun deleteRecord(id: String) {
        db.update { c -> c.copy(records = c.records.filterNot { it.id == id }) }
        if (draft.id == id) newRecord(keepRack = true)
        post("Запись удалена")
    }

    // ---------- Настройки ----------

    fun saveSettings(newSettings: SmtpSettings, silent: Boolean = false) {
        val value = newSettings.trimmed()
        if (value != currentSettings()) db.update { it.copy(settings = value) }
        if (!silent) post("Настройки сохранены")
    }

    fun sendTest(newSettings: SmtpSettings) {
        saveSettings(newSettings, silent = true)
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

    fun forgetSuggestion(entry: DictEntry) {
        db.update { it.copy(dictionary = it.dictionary.forget(entry)) }
    }

    companion object {
        const val SCAN_INVENTORY = "inventory"
        const val SCAN_SERIAL_NEW = "serial:new"
        const val SCAN_SERIAL_PREFIX = "serial:"

        /** Через сколько свёрнутое приложение блокируется. */
        const val AUTO_LOCK_MS = 5 * 60 * 1000L
    }
}
