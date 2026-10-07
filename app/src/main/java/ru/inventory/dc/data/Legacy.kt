package ru.inventory.dc.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Незашифрованная резервная копия версии 1.3 — поддерживается только для импорта. */
@Serializable
data class LegacyBackup(
    val format: String = "",
    val records: List<EquipmentRecord> = emptyList(),
    val settings: SmtpSettings = SmtpSettings(),
    val placement: Placement = Placement(),
    val dictionary: List<DictEntry> = emptyList(),
)

/**
 * Данные предыдущих версий (до шифрования): незашифрованные JSON-файлы и настройки.
 * Переносятся в новую базу, после чего удаляются.
 */
class LegacyData(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val recordsFile = File(context.filesDir, "records.json")
    private val dictionaryFile = File(context.filesDir, "dictionary.json")
    private val draftFile = File(context.filesDir, "draft.json")
    private val smtpPrefs = context.getSharedPreferences("smtp_settings", Context.MODE_PRIVATE)
    private val placementPrefs = context.getSharedPreferences("placement", Context.MODE_PRIVATE)
    private val backupPrefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    fun exists(): Boolean =
        recordsFile.exists() || dictionaryFile.exists() || smtpPrefs.contains("host") || placementPrefs.contains("site")

    /** Папка автокопии из версии 1.3 — предлагаем её же для новой базы. */
    val backupFolder: Uri? get() = backupPrefs.getString("folder", null)?.let(Uri::parse)

    fun load(): DatabaseContent {
        val records = readList(recordsFile, EquipmentRecord.serializer())
        val dictionary = readList(dictionaryFile, DictEntry.serializer())
        val security = runCatching {
            SmtpSecurity.valueOf(smtpPrefs.getString("security", null) ?: SmtpSecurity.STARTTLS.name)
        }.getOrDefault(SmtpSecurity.STARTTLS)
        val settings = SmtpSettings(
            host = smtpPrefs.getString("host", null).orEmpty(),
            port = smtpPrefs.getString("port", null) ?: security.defaultPort.toString(),
            security = security,
            username = smtpPrefs.getString("username", null).orEmpty(),
            password = decryptLegacyPassword(smtpPrefs.getString("password", null).orEmpty()),
            fromAddress = smtpPrefs.getString("from", null).orEmpty(),
            recipients = smtpPrefs.getString("recipients", null).orEmpty(),
            cc = smtpPrefs.getString("cc", null).orEmpty(),
            subjectPrefix = smtpPrefs.getString("subject_prefix", null) ?: SmtpSettings.DEFAULT_SUBJECT_PREFIX,
            trustAllCerts = smtpPrefs.getBoolean("trust_all_certs", false),
        )
        val placement = Placement(
            site = placementPrefs.getString("site", null).orEmpty(),
            hall = placementPrefs.getString("hall", null).orEmpty(),
            rack = placementPrefs.getString("rack", null).orEmpty(),
            chosen = placementPrefs.getBoolean("chosen", false),
        )
        return DatabaseContent(records = records, settings = settings, placement = placement, dictionary = dictionary)
    }

    /** Удаляет все незашифрованные данные старой версии, включая открытую копию в папке автокопии. */
    fun wipe() {
        listOf(recordsFile, dictionaryFile, draftFile).forEach { it.delete() }
        context.filesDir.listFiles { f -> f.name.startsWith("records.corrupt-") }?.forEach { it.delete() }
        backupFolder?.let { folder ->
            runCatching { DocumentFile.fromTreeUri(context, folder)?.findFile(LEGACY_BACKUP_NAME)?.delete() }
                .onFailure { Log.w(TAG, "Не удалось удалить старую копию", it) }
        }
        smtpPrefs.edit().clear().apply()
        placementPrefs.edit().clear().apply()
        backupPrefs.edit().clear().apply()
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(LEGACY_KEY_ALIAS)
        }
    }

    /** Разбор незашифрованной копии версии 1.3; null — если файл не в этом формате. */
    fun parseLegacyBackup(bytes: ByteArray): DatabaseContent? = runCatching {
        val backup = json.decodeFromString(LegacyBackup.serializer(), bytes.toString(Charsets.UTF_8))
        if (backup.format != "dc-inventory-backup") return null
        DatabaseContent(
            records = backup.records,
            settings = backup.settings,
            placement = backup.placement,
            dictionary = backup.dictionary,
        )
    }.getOrNull()

    private fun <T> readList(file: File, serializer: kotlinx.serialization.KSerializer<T>): List<T> = try {
        if (file.exists()) json.decodeFromString(ListSerializer(serializer), file.readText()) else emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "Не удалось прочитать ${file.name}", e)
        emptyList()
    }

    private fun decryptLegacyPassword(stored: String): String {
        if (stored.isEmpty()) return ""
        if (stored.startsWith("plain:")) return stored.removePrefix("plain:")
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = keyStore.getKey(LEGACY_KEY_ALIAS, null) as? SecretKey ?: return ""
            val data = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12))
            String(cipher.doFinal(data, 12, data.size - 12), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось расшифровать старый пароль", e)
            ""
        }
    }

    private companion object {
        const val TAG = "LegacyData"
        const val LEGACY_KEY_ALIAS = "smtp_password_key"
        const val LEGACY_BACKUP_NAME = "dc-inventory-backup.json"
    }
}
