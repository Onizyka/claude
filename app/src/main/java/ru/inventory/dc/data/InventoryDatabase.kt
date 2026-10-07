package ru.inventory.dc.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** Всё содержимое базы. На диске хранится только в зашифрованном виде. */
@Serializable
data class DatabaseContent(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    /** Случайный ключ (Base64) для фотографий и черновика. Не меняется при смене пароля. */
    val dataKey: String = "",
    val records: List<EquipmentRecord> = emptyList(),
    val settings: SmtpSettings = SmtpSettings(),
    val placement: Placement = Placement(),
    val dictionary: List<DictEntry> = emptyList(),
    /** Площадки, помещения и стойки, которые ведёт пользователь. */
    val sites: List<Site> = emptyList(),
)

enum class DbState { NO_DATABASE, LOCKED, UNLOCKED }

/** Где лежит файл базы и когда он последний раз записан. */
data class DbLocation(
    val fileUri: String? = null,
    val displayPath: String? = null,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
)

/**
 * Зашифрованная база приложения.
 *
 * Рабочая копия — `inventory.dcdb` во внутренней памяти приложения (всегда доступна).
 * Основной файл — в папке, выбранной пользователем (по умолчанию «Документы»): он переживает
 * переустановку приложения, и его можно открыть на другом телефоне. Оба файла зашифрованы
 * одним и тем же паролем; без пароля прочитать их невозможно.
 */
class InventoryDatabase(private val context: Context, scope: CoroutineScope) {

    private val internalFile = File(context.filesDir, "inventory.dcdb")
    private val draftFile = File(context.filesDir, "draft.enc")
    // Здесь только расположение файла — ничего конфиденциального.
    private val prefs = context.getSharedPreferences("database", Context.MODE_PRIVATE)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val _state = MutableStateFlow(if (internalFile.exists()) DbState.LOCKED else DbState.NO_DATABASE)
    val state: StateFlow<DbState> = _state.asStateFlow()

    private val _content = MutableStateFlow(DatabaseContent())
    val content: StateFlow<DatabaseContent> = _content.asStateFlow()

    private val _location = MutableStateFlow(
        DbLocation(
            fileUri = prefs.getString(K_URI, null),
            displayPath = prefs.getString(K_PATH, null),
            lastSyncAt = prefs.getLong(K_SYNC, 0L).takeIf { it > 0 },
        )
    )
    val location: StateFlow<DbLocation> = _location.asStateFlow()

    @Volatile private var passwordKey: VaultCrypto.PasswordKey? = null
    @Volatile private var dataKey: SecretKey? = null

    private val writeMutex = Mutex()
    private val internalSignal = Channel<Unit>(Channel.CONFLATED)
    private val externalSignal = Channel<Unit>(Channel.CONFLATED)

    init {
        // Рабочая копия пишется сразу после изменения.
        scope.launch(Dispatchers.IO) {
            for (signal in internalSignal) {
                writeMutex.withLock {
                    runCatching { writeInternalLocked() }.onFailure { Log.e(TAG, "База не записана", it) }
                }
            }
        }
        // Файл в папке пользователя — с небольшой задержкой, чтобы объединять частые изменения.
        scope.launch(Dispatchers.IO) {
            for (signal in externalSignal) {
                delay(EXTERNAL_DELAY_MS)
                externalSignal.tryReceive()
                writeMutex.withLock { writeExternalLocked() }
            }
        }
    }

    val isUnlocked: Boolean get() = _state.value == DbState.UNLOCKED

    // ---------- Создание, открытие, вход ----------

    /** Создаёт новую базу; файл появляется в [folder] (папка, выбранная пользователем). */
    suspend fun create(password: CharArray, folder: Uri?, initial: DatabaseContent) = withContext(Dispatchers.Default) {
        val key = VaultCrypto.deriveKey(password)
        val content = initial.copy(
            createdAt = System.currentTimeMillis(),
            dataKey = initial.dataKey.ifEmpty { Base64.encodeToString(VaultCrypto.randomKeyBytes(), Base64.NO_WRAP) },
        )
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                // Сначала файл в папке: если папка недоступна, база не создаётся вовсе.
                val file = folder?.let { createFileIn(it) }
                try {
                    activate(key, content)
                    writeInternalLocked()
                } catch (e: Exception) {
                    passwordKey = null
                    dataKey = null
                    internalFile.delete()
                    throw e
                }
                setLocation(file)
                writeExternalLocked()
            }
        }
        draftFile.delete()
        _state.value = DbState.UNLOCKED
    }

    suspend fun readUri(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Не удалось открыть файл")
    }

    /** Открывает существующую базу из файла и дальше работает с этим файлом. */
    suspend fun open(uri: Uri, bytes: ByteArray, password: CharArray) = withContext(Dispatchers.Default) {
        val (salt, iterations) = VaultCrypto.readHeader(bytes)
        val key = VaultCrypto.deriveKey(password, salt, iterations)
        val content = decode(VaultCrypto.decryptFile(key.key, bytes))
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                activate(key, content)
                writeInternalLocked()
                val writable = runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }.isSuccess
                setLocation(uri)
                if (!writable) {
                    _location.update { it.copy(lastError = "Нет доступа на запись к файлу — сохраните базу в другую папку") }
                }
            }
        }
        draftFile.delete()
        _state.value = DbState.UNLOCKED
    }

    /** Вход в приложение. false — неверный пароль. */
    suspend fun unlock(password: CharArray): Boolean = withContext(Dispatchers.Default) {
        val bytes = withContext(Dispatchers.IO) { internalFile.readBytes() }
        val (salt, iterations) = VaultCrypto.readHeader(bytes)
        val key = VaultCrypto.deriveKey(password, salt, iterations)
        val plain = try {
            VaultCrypto.decryptFile(key.key, bytes)
        } catch (e: WrongPasswordException) {
            return@withContext false
        }
        writeMutex.withLock { activate(key, decode(plain)) }
        _state.value = DbState.UNLOCKED
        true
    }

    /** Блокировка: всё записывается на диск, ключи и данные удаляются из памяти. */
    suspend fun lock() = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            if (passwordKey != null) {
                writeInternalLocked()
                writeExternalLocked()
            }
            passwordKey = null
            dataKey = null
            _content.value = DatabaseContent()
        }
        if (internalFile.exists()) _state.value = DbState.LOCKED
    }

    /** «Забыли пароль»: удаляет локальную копию. Файл в папке пользователя не трогаем. */
    suspend fun resetLocal() = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            passwordKey = null
            dataKey = null
            _content.value = DatabaseContent()
            internalFile.delete()
            draftFile.delete()
            prefs.edit().clear().apply()
            _location.value = DbLocation()
        }
        _state.value = DbState.NO_DATABASE
    }

    // ---------- Изменение данных ----------

    fun update(transform: (DatabaseContent) -> DatabaseContent) {
        if (!isUnlocked) return
        _content.update(transform)
        internalSignal.trySend(Unit)
        externalSignal.trySend(Unit)
    }

    suspend fun changePassword(old: CharArray, new: CharArray): Boolean = withContext(Dispatchers.Default) {
        val current = passwordKey ?: return@withContext false
        val check = VaultCrypto.deriveKey(old, current.salt, current.iterations)
        if (!MessageDigest.isEqual(check.key.encoded, current.key.encoded)) return@withContext false
        val newKey = VaultCrypto.deriveKey(new)
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                passwordKey = newKey
                writeInternalLocked()
                writeExternalLocked()
            }
        }
        true
    }

    /** Переносит файл базы в другую папку (старый файл остаётся — его можно удалить вручную). */
    suspend fun moveTo(folder: Uri) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val file = createFileIn(folder)
            setLocation(file)
            writeExternalLocked()
        }
    }

    /** Копия базы (тот же пароль) в произвольный файл. */
    suspend fun exportCopy(uri: Uri) = withContext(Dispatchers.IO) {
        val key = passwordKey ?: error("База заблокирована")
        val bytes = VaultCrypto.encryptFile(key, encode(_content.value))
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Не удалось записать файл")
    }

    // ---------- Черновик и фото (ключ данных) ----------

    suspend fun saveDraft(record: EquipmentRecord) = withContext(Dispatchers.IO) {
        val key = dataKey ?: return@withContext
        runCatching {
            val plain = json.encodeToString(EquipmentRecord.serializer(), record).toByteArray(Charsets.UTF_8)
            draftFile.writeBytes(VaultCrypto.encryptBlob(key, plain))
        }.onFailure { Log.w(TAG, "Черновик не сохранён", it) }
    }

    suspend fun loadDraft(): EquipmentRecord? = withContext(Dispatchers.IO) {
        val key = dataKey ?: return@withContext null
        if (!draftFile.exists()) return@withContext null
        runCatching {
            val plain = VaultCrypto.decryptBlob(key, draftFile.readBytes())
            json.decodeFromString(EquipmentRecord.serializer(), plain.toString(Charsets.UTF_8))
        }.getOrNull()
    }

    fun encryptData(plain: ByteArray): ByteArray =
        VaultCrypto.encryptBlob(dataKey ?: error("База заблокирована"), plain)

    fun decryptData(data: ByteArray): ByteArray =
        VaultCrypto.decryptBlob(dataKey ?: error("База заблокирована"), data)

    // ---------- Внутреннее ----------

    private fun activate(key: VaultCrypto.PasswordKey, content: DatabaseContent) {
        passwordKey = key
        dataKey = SecretKeySpec(Base64.decode(content.dataKey, Base64.NO_WRAP), "AES")
        _content.value = content
    }

    private fun encode(content: DatabaseContent): ByteArray =
        json.encodeToString(DatabaseContent.serializer(), content).toByteArray(Charsets.UTF_8)

    private fun decode(plain: ByteArray): DatabaseContent {
        val content = json.decodeFromString(DatabaseContent.serializer(), plain.toString(Charsets.UTF_8))
        require(content.dataKey.isNotEmpty()) { "Повреждённая база: нет ключа данных" }
        return content
    }

    private fun writeInternalLocked() {
        val key = passwordKey ?: return
        val bytes = VaultCrypto.encryptFile(key, encode(_content.value))
        val tmp = File(internalFile.parentFile, "${internalFile.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(internalFile)) {
            tmp.copyTo(internalFile, overwrite = true)
            tmp.delete()
        }
    }

    private fun writeExternalLocked() {
        val key = passwordKey ?: return
        val uri = _location.value.fileUri?.let(Uri::parse) ?: return
        try {
            val bytes = VaultCrypto.encryptFile(key, encode(_content.value))
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: error("Файл базы недоступен")
            val now = System.currentTimeMillis()
            prefs.edit().putLong(K_SYNC, now).apply()
            _location.update { it.copy(lastSyncAt = now, lastError = null) }
        } catch (e: Exception) {
            Log.w(TAG, "Файл базы не записан", e)
            _location.update { it.copy(lastError = e.message ?: e.javaClass.simpleName) }
        }
    }

    private fun createFileIn(folder: Uri): Uri {
        context.contentResolver.takePersistableUriPermission(
            folder, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val tree = DocumentFile.fromTreeUri(context, folder) ?: error("Папка недоступна")
        // Существующую базу не перезаписываем: система добавит к имени номер.
        val file = tree.createFile("application/octet-stream", FILE_NAME) ?: error("Не удалось создать файл в папке")
        return file.uri
    }

    private fun setLocation(uri: Uri?) {
        val path = uri?.let { u ->
            runCatching { DocumentsContract.getDocumentId(u).substringAfter(':') }.getOrNull()
                ?: DocumentFile.fromSingleUri(context, u)?.name
        }
        prefs.edit().putString(K_URI, uri?.toString()).putString(K_PATH, path).remove(K_SYNC).apply()
        _location.value = DbLocation(fileUri = uri?.toString(), displayPath = path)
    }

    companion object {
        const val FILE_NAME = "dc-inventory.dcdb"
        private const val TAG = "InventoryDatabase"
        private const val EXTERNAL_DELAY_MS = 1500L
        private const val K_URI = "file_uri"
        private const val K_PATH = "file_path"
        private const val K_SYNC = "last_sync"

        /** Папка «Документы» во внутренней памяти — предлагается по умолчанию. */
        val DEFAULT_FOLDER: Uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents", "primary:Documents",
        )
    }
}
