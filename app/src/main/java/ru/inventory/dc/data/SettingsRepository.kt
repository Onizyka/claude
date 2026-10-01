package ru.inventory.dc.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class SmtpSecurity(val title: String, val defaultPort: Int) {
    NONE("Нет", 25),
    STARTTLS("STARTTLS", 587),
    SSL("SSL/TLS", 465),
}

data class SmtpSettings(
    val host: String = "",
    val port: String = SmtpSecurity.STARTTLS.defaultPort.toString(),
    val security: SmtpSecurity = SmtpSecurity.STARTTLS,
    val username: String = "",
    val password: String = "",
    val fromAddress: String = "",
    val recipients: String = "",
    val subjectPrefix: String = DEFAULT_SUBJECT_PREFIX,
    val trustAllCerts: Boolean = false,
) {
    val sender: String get() = fromAddress.trim().ifBlank { username.trim() }

    val isConfigured: Boolean
        get() = host.isNotBlank() && recipients.isNotBlank() && sender.isNotBlank()

    companion object {
        const val DEFAULT_SUBJECT_PREFIX = "[Инвентаризация]"
    }
}

/** Настройки SMTP. Пароль шифруется ключом из Android Keystore. */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("smtp_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<SmtpSettings> = _settings.asStateFlow()

    private fun load(): SmtpSettings {
        val security = runCatching {
            SmtpSecurity.valueOf(prefs.getString(K_SECURITY, null) ?: SmtpSecurity.STARTTLS.name)
        }.getOrDefault(SmtpSecurity.STARTTLS)
        return SmtpSettings(
            host = prefs.getString(K_HOST, null).orEmpty(),
            port = prefs.getString(K_PORT, null) ?: security.defaultPort.toString(),
            security = security,
            username = prefs.getString(K_USER, null).orEmpty(),
            password = SecretCipher.decrypt(prefs.getString(K_PASSWORD, null).orEmpty()),
            fromAddress = prefs.getString(K_FROM, null).orEmpty(),
            recipients = prefs.getString(K_TO, null).orEmpty(),
            subjectPrefix = prefs.getString(K_PREFIX, null) ?: SmtpSettings.DEFAULT_SUBJECT_PREFIX,
            trustAllCerts = prefs.getBoolean(K_TRUST_ALL, false),
        )
    }

    fun save(settings: SmtpSettings) {
        if (settings == _settings.value) return
        prefs.edit()
            .putString(K_HOST, settings.host.trim())
            .putString(K_PORT, settings.port.trim())
            .putString(K_SECURITY, settings.security.name)
            .putString(K_USER, settings.username.trim())
            .putString(K_PASSWORD, SecretCipher.encrypt(settings.password))
            .putString(K_FROM, settings.fromAddress.trim())
            .putString(K_TO, settings.recipients.trim())
            .putString(K_PREFIX, settings.subjectPrefix)
            .putBoolean(K_TRUST_ALL, settings.trustAllCerts)
            .apply()
        _settings.value = settings
    }

    private companion object {
        const val K_HOST = "host"
        const val K_PORT = "port"
        const val K_SECURITY = "security"
        const val K_USER = "username"
        const val K_PASSWORD = "password"
        const val K_FROM = "from"
        const val K_TO = "recipients"
        const val K_PREFIX = "subject_prefix"
        const val K_TRUST_ALL = "trust_all_certs"
    }
}

private object SecretCipher {
    private const val TAG = "SecretCipher"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "smtp_password_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PLAIN_PREFIX = "plain:"
    private const val IV_SIZE = 12

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val data = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(data, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Keystore недоступен на устройстве — сохраняем без шифрования, чтобы не потерять пароль.
            Log.w(TAG, "Keystore недоступен, пароль сохранён без шифрования", e)
            PLAIN_PREFIX + plain
        }
    }

    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        if (stored.startsWith(PLAIN_PREFIX)) return stored.removePrefix(PLAIN_PREFIX)
        return try {
            val data = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_SIZE))
            String(cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось расшифровать пароль", e)
            ""
        }
    }
}
