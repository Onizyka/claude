package ru.inventory.dc.mail

import ru.inventory.dc.data.SmtpSecurity
import ru.inventory.dc.data.SmtpSettings
import java.io.File
import java.util.Date
import java.util.Properties
import javax.mail.AuthenticationFailedException
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/** Отправка писем по SMTP. Вызывать только из фонового потока. */
object MailSender {

    private const val TIMEOUT_MS = "20000"

    fun send(
        settings: SmtpSettings,
        subject: String,
        text: String,
        html: String?,
        attachments: List<File> = emptyList(),
    ) {
        require(settings.host.isNotBlank()) { "Не указан SMTP-сервер" }
        require(settings.sender.isNotBlank()) { "Не указан адрес отправителя" }
        val recipients = settings.recipients
            .split(',', ';', ' ', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        require(recipients.isNotEmpty()) { "Не указан получатель" }

        val port = settings.port.trim().toIntOrNull() ?: settings.security.defaultPort
        val useAuth = settings.username.isNotBlank()

        val props = Properties().apply {
            put("mail.transport.protocol", "smtp")
            put("mail.smtp.host", settings.host.trim())
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", useAuth.toString())
            put("mail.smtp.connectiontimeout", TIMEOUT_MS)
            put("mail.smtp.timeout", TIMEOUT_MS)
            put("mail.smtp.writetimeout", TIMEOUT_MS)
            when (settings.security) {
                SmtpSecurity.SSL -> put("mail.smtp.ssl.enable", "true")
                SmtpSecurity.STARTTLS -> {
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                }
                SmtpSecurity.NONE -> Unit
            }
            if (settings.trustAllCerts) {
                put("mail.smtp.ssl.trust", "*")
                put("mail.smtp.ssl.checkserveridentity", "false")
            } else {
                put("mail.smtp.ssl.checkserveridentity", "true")
            }
        }

        val session = if (useAuth) {
            Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(settings.username.trim(), settings.password)
            })
        } else {
            Session.getInstance(props)
        }

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(settings.sender))
            setRecipients(Message.RecipientType.TO, recipients.map { InternetAddress(it, true) }.toTypedArray())
            setSubject(subject, "UTF-8")
            sentDate = Date()
            val textPart = MimeBodyPart().apply { setText(text, "UTF-8") }
            val body = if (html == null) {
                MimeMultipart().apply { addBodyPart(textPart) }
            } else {
                val htmlPart = MimeBodyPart().apply { setContent(html, "text/html; charset=UTF-8") }
                MimeMultipart("alternative").apply {
                    addBodyPart(textPart)
                    addBodyPart(htmlPart)
                }
            }
            val files = attachments.filter { it.exists() }
            if (files.isEmpty()) {
                setContent(body)
            } else {
                // Текст письма + фотографии вложениями
                val mixed = MimeMultipart("mixed")
                mixed.addBodyPart(MimeBodyPart().apply { setContent(body) })
                files.forEachIndexed { i, file ->
                    mixed.addBodyPart(MimeBodyPart().apply {
                        attachFile(file, "image/jpeg", null)
                        fileName = "photo_${i + 1}.jpg"
                    })
                }
                setContent(mixed)
            }
        }

        Transport.send(message)
    }

    /** Человекочитаемое описание ошибки отправки. */
    fun describeError(error: Throwable): String {
        if (error is AuthenticationFailedException) return "Ошибка авторизации: проверьте логин и пароль"
        val messages = generateSequence(error) { it.cause }
            .mapNotNull { e -> e.message?.trim()?.takeIf { it.isNotEmpty() } ?: e.javaClass.simpleName }
            .distinct()
            .toList()
        return messages.joinToString(": ").ifBlank { error.javaClass.simpleName }
    }
}
