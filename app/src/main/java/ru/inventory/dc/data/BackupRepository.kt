package ru.inventory.dc.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Содержимое файла резервной копии. Фотографии в копию не входят (слишком большой объём). */
@Serializable
data class BackupData(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val records: List<EquipmentRecord> = emptyList(),
    val settings: SmtpSettings = SmtpSettings(),
    val placement: Placement = Placement(),
    val dictionary: List<DictEntry> = emptyList(),
) {
    companion object {
        const val FORMAT = "dc-inventory-backup"
    }
}

data class BackupState(
    val folderUri: String? = null,
    val folderName: String? = null,
    val includePassword: Boolean = false,
    val lastBackupAt: Long? = null,
    val lastError: String? = null,
)

/**
 * Резервная копия всех данных в JSON-файле в папке, выбранной пользователем (например, «Документы»).
 * Файл остаётся на телефоне после удаления приложения, и из него можно всё восстановить.
 */
class BackupRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val _state = MutableStateFlow(
        BackupState(
            folderUri = prefs.getString(K_FOLDER, null),
            folderName = prefs.getString(K_FOLDER_NAME, null),
            includePassword = prefs.getBoolean(K_PASSWORD, false),
            lastBackupAt = prefs.getLong(K_LAST, 0L).takeIf { it > 0 },
        )
    )
    val state: StateFlow<BackupState> = _state.asStateFlow()

    fun setFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        val name = DocumentFile.fromTreeUri(context, uri)?.name
        prefs.edit().putString(K_FOLDER, uri.toString()).putString(K_FOLDER_NAME, name).apply()
        _state.value = _state.value.copy(folderUri = uri.toString(), folderName = name, lastError = null)
    }

    fun setIncludePassword(include: Boolean) {
        prefs.edit().putBoolean(K_PASSWORD, include).apply()
        _state.value = _state.value.copy(includePassword = include)
    }

    /** Пишет автокопию в выбранную папку. Возвращает false, если папка не выбрана или недоступна. */
    suspend fun writeAuto(data: BackupData): Boolean = withContext(Dispatchers.IO) {
        val folder = _state.value.folderUri ?: return@withContext false
        try {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(folder))
                ?: error("Папка недоступна")
            val target = tree.findFile(FILE_NAME)
                ?: tree.createFile("application/json", FILE_NAME)
                ?: error("Не удалось создать файл в папке")
            write(target.uri, data)
            val now = System.currentTimeMillis()
            prefs.edit().putLong(K_LAST, now).apply()
            _state.value = _state.value.copy(lastBackupAt = now, lastError = null)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Автокопия не записана", e)
            _state.value = _state.value.copy(lastError = e.message ?: e.javaClass.simpleName)
            false
        }
    }

    suspend fun write(uri: Uri, data: BackupData) = withContext(Dispatchers.IO) {
        val text = json.encodeToString(BackupData.serializer(), data)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("Не удалось открыть файл для записи")
    }

    suspend fun read(uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Не удалось открыть файл")
        val data = json.decodeFromString(BackupData.serializer(), text)
        require(data.format == BackupData.FORMAT) { "Это не файл резервной копии приложения" }
        data
    }

    companion object {
        const val FILE_NAME = "dc-inventory-backup.json"
        private const val TAG = "BackupRepository"
        private const val K_FOLDER = "folder"
        private const val K_FOLDER_NAME = "folder_name"
        private const val K_PASSWORD = "include_password"
        private const val K_LAST = "last_backup"
    }
}
