package ru.inventory.dc.mail

import ru.inventory.dc.data.EquipmentRecord
import ru.inventory.dc.data.displayTitle
import ru.inventory.dc.data.formatDateTime
import ru.inventory.dc.data.hallTitle
import ru.inventory.dc.data.locationLine
import ru.inventory.dc.data.maskedForEmail

/** Формирует тему и текст письма (plain text + HTML) по записи. Пустые поля в письмо не попадают. */
object ReportBuilder {

    private data class Section(val title: String, val rows: List<Pair<String, String>>)

    private fun sections(r: EquipmentRecord): List<Section> = listOf(
        Section(
            "Размещение",
            listOf(
                "ЦОД" to r.site,
                hallTitle(r.site) to r.room,
                "Стойка" to r.rack,
                "Юнит" to r.unit,
                "Высота, U" to r.heightU,
            ),
        ),
        Section(
            "Оборудование",
            listOf(
                "Тип" to r.type,
                "Производитель" to r.vendor,
                "Модель" to r.model,
                "Hostname" to r.hostname,
            ),
        ),
        Section(
            "Серийный и инвентарный номер",
            r.serials.mapIndexed { i, sn ->
                (if (r.serials.size == 1) "Серийный номер" else "S/N ${i + 1}") to sn
            } + ("Инвентарный номер" to r.inventoryNumber),
        ),
        Section(
            "MGMT-интерфейс",
            listOf(
                "IP-адрес" to r.mgmtIp,
                "Маска / префикс" to r.mgmtMask,
                "Шлюз" to r.mgmtGateway,
            ),
        ),
    )
        .map { section -> section.copy(rows = section.rows.filter { it.second.isNotBlank() }) }
        .filter { it.rows.isNotEmpty() }

    /** Тема: префикс, оборудование и дата/время. Место установки — только в теле письма. */
    fun subject(record: EquipmentRecord, prefix: String, timestamp: Long): String {
        val r = record.maskedForEmail()
        val equipment = listOf(r.type, listOf(r.vendor, r.model).filter { it.isNotBlank() }.joinToString(" "))
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { r.hostname }
            .ifBlank { "Новое оборудование" }
        return listOf(prefix.trim(), equipment, "·", formatDateTime(timestamp))
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    fun text(record: EquipmentRecord, timestamp: Long): String = buildString {
        val r = record.maskedForEmail()
        appendLine("ИНВЕНТАРИЗАЦИЯ ОБОРУДОВАНИЯ")
        appendLine("${r.displayTitle()} — ${formatDateTime(timestamp)}")
        r.locationLine().takeIf { it.isNotBlank() }?.let { appendLine("Место: $it") }
        sections(r).forEach { section ->
            appendLine()
            appendLine("== ${section.title} ==")
            section.rows.forEach { (k, v) -> appendLine("$k: $v") }
        }
        if (r.connections.isNotEmpty()) {
            appendLine()
            appendLine("== Подключения ==")
            r.connections.forEachIndexed { i, c ->
                val local = c.localPort.ifBlank { "—" }
                val remote = listOf(c.remoteDevice, c.remotePort).filter { it.isNotBlank() }.joinToString(" : ").ifBlank { "—" }
                val note = listOf(c.cableType, c.note).filter { it.isNotBlank() }.joinToString(", ")
                    .takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                appendLine("${i + 1}. $local → $remote$note")
            }
        }
        if (r.photos.isNotEmpty()) {
            appendLine()
            appendLine("== Фотографии ==")
            appendLine("Во вложении: ${r.photos.size} шт.")
        }
        if (r.comment.isNotBlank()) {
            appendLine()
            appendLine("== Комментарий ==")
            appendLine(r.comment)
        }
    }

    fun html(record: EquipmentRecord, timestamp: Long): String = buildString {
        val r = record.maskedForEmail()
        append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>")
        append("<body style=\"margin:0;padding:16px 0;background:#F2F5F9;font-family:Arial,Helvetica,sans-serif;color:#1B2B3F\">")
        append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:720px;margin:0 auto;background:#FFFFFF;border-radius:12px;overflow:hidden\">")

        // Шапка в фирменных цветах
        append("<tr><td style=\"background:#003274;background:linear-gradient(90deg,#003274,#025EA1);padding:20px 24px;color:#FFFFFF\">")
        append("<div style=\"font-size:12px;letter-spacing:2px;text-transform:uppercase;opacity:.8\">Инвентаризация оборудования</div>")
        append("<div style=\"font-size:20px;font-weight:bold;margin-top:6px\">").append(esc(r.displayTitle())).append("</div>")
        append("<div style=\"font-size:13px;opacity:.85;margin-top:4px\">").append(esc(formatDateTime(timestamp))).append("</div>")
        r.locationLine().takeIf { it.isNotBlank() }?.let {
            append("<div style=\"font-size:14px;margin-top:8px;font-weight:bold\">").append(esc(it)).append("</div>")
        }
        append("</td></tr>")

        sections(r).forEach { section ->
            appendSectionTitle(section.title)
            append("<table width=\"100%\" cellpadding=\"6\" cellspacing=\"0\" style=\"font-size:14px;margin-top:6px\">")
            section.rows.forEach { (k, v) ->
                append("<tr><td style=\"width:40%;color:#6E7782;border-bottom:1px solid #E8EDF3\">").append(esc(k))
                append("</td><td style=\"border-bottom:1px solid #E8EDF3;font-weight:bold\">").append(esc(v)).append("</td></tr>")
            }
            append("</table></td></tr>")
        }

        if (r.connections.isNotEmpty()) {
            appendSectionTitle("Подключения")
            append("<table width=\"100%\" cellpadding=\"6\" cellspacing=\"0\" style=\"font-size:13px;margin-top:6px;border-collapse:collapse\">")
            append("<tr style=\"background:#E6F1FA;color:#003274\">")
            listOf("#", "Порт", "Устройство", "Порт устройства", "Кабель", "Примечание").forEach {
                append("<th align=\"left\" style=\"border:1px solid #D8DDE3\">").append(esc(it)).append("</th>")
            }
            append("</tr>")
            r.connections.forEachIndexed { i, c ->
                append("<tr>")
                listOf((i + 1).toString(), c.localPort, c.remoteDevice, c.remotePort, c.cableType, c.note).forEach {
                    append("<td style=\"border:1px solid #D8DDE3\">").append(esc(it.ifBlank { "—" })).append("</td>")
                }
                append("</tr>")
            }
            append("</table></td></tr>")
        }

        if (r.comment.isNotBlank()) {
            appendSectionTitle("Комментарий")
            append("<div style=\"font-size:14px;margin-top:8px;white-space:pre-wrap\">").append(esc(r.comment)).append("</div></td></tr>")
        }

        if (r.photos.isNotEmpty()) {
            appendSectionTitle("Фотографии")
            append("<div style=\"font-size:14px;margin-top:8px\">Во вложении: ").append(r.photos.size).append(" шт.</div></td></tr>")
        }

        append("<tr><td style=\"padding:20px 24px;font-size:11px;color:#6E7782\">Сформировано приложением «Инвентаризация ЦОД»</td></tr>")
        append("</table></body></html>")
    }

    /** Открывает строку секции; закрывающий `</td></tr>` добавляет вызывающий код. */
    private fun StringBuilder.appendSectionTitle(title: String) {
        append("<tr><td style=\"padding:16px 24px 0\">")
        append("<div style=\"font-size:15px;font-weight:bold;color:#003274;border-bottom:2px solid #6CACE4;padding-bottom:6px\">")
        append(esc(title)).append("</div>")
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
