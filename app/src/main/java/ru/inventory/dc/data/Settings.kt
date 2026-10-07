package ru.inventory.dc.data

import kotlinx.serialization.Serializable

@Serializable
enum class SmtpSecurity(val title: String, val defaultPort: Int) {
    NONE("Нет", 25),
    STARTTLS("STARTTLS", 587),
    SSL("SSL/TLS", 465),
}

/** Настройки почты. Хранятся внутри зашифрованной базы вместе с паролем SMTP. */
@Serializable
data class SmtpSettings(
    val host: String = "",
    val port: String = SmtpSecurity.STARTTLS.defaultPort.toString(),
    val security: SmtpSecurity = SmtpSecurity.STARTTLS,
    val username: String = "",
    val password: String = "",
    val fromAddress: String = "",
    /** Получатели через запятую — количество не ограничено. */
    val recipients: String = "",
    /** Копия (CC) через запятую. */
    val cc: String = "",
    val subjectPrefix: String = DEFAULT_SUBJECT_PREFIX,
    val trustAllCerts: Boolean = false,
) {
    val sender: String get() = fromAddress.trim().ifBlank { username.trim() }

    val recipientCount: Int get() = splitAddresses(recipients).size + splitAddresses(cc).size

    val isConfigured: Boolean
        get() = host.isNotBlank() && recipients.isNotBlank() && sender.isNotBlank()

    fun trimmed(): SmtpSettings = copy(
        host = host.trim(),
        port = port.trim(),
        username = username.trim(),
        fromAddress = fromAddress.trim(),
        recipients = recipients.trim(),
        cc = cc.trim(),
    )

    companion object {
        const val DEFAULT_SUBJECT_PREFIX = "[Инвентаризация]"
    }
}

/** Текущее место установки: задаётся один раз для серии устройств. */
@Serializable
data class Placement(
    val site: String = "",
    val hall: String = "",
    val rack: String = "",
    /** Пользователь уже проходил экран выбора места. */
    val chosen: Boolean = false,
) {
    fun summary(): String = listOf(site, hallLabel(site, hall), rack.takeIf { it.isNotBlank() }?.let { "Стойка $it" }.orEmpty())
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    fun trimmed(): Placement = copy(site = site.trim(), hall = hall.trim(), rack = rack.trim())
}

/** Разбор списка адресов: разделители — запятая, точка с запятой, пробел, перевод строки. */
fun splitAddresses(text: String): List<String> =
    text.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
