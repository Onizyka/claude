package ru.inventory.dc.data

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Serializable
data class PortConnection(
    val id: String = UUID.randomUUID().toString(),
    /** Порт этого устройства (NIC1, eth0, iLO...). */
    val localPort: String = "",
    /** Устройство, к которому подключён порт. */
    val remoteDevice: String = "",
    /** Порт на удалённом устройстве. */
    val remotePort: String = "",
    /** Тип кабеля: «Медь», «Оптика» или своё значение. */
    val cableType: String = "",
    /** Маркировка, длина, примечание. */
    val note: String = "",
) {
    fun isBlank(): Boolean =
        localPort.isBlank() && remoteDevice.isBlank() && remotePort.isBlank() && cableType.isBlank() && note.isBlank()

    fun trimmed(): PortConnection = copy(
        localPort = localPort.trim(),
        remoteDevice = remoteDevice.trim(),
        remotePort = remotePort.trim(),
        cableType = cableType.trim(),
        note = note.trim(),
    )
}

@Serializable
enum class SendStatus { DRAFT, SENT, FAILED }

/** Запись об установленном оборудовании. Все поля необязательные. */
@Serializable
data class EquipmentRecord(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,

    // Размещение: site — площадка, room — машзал/помещение, roomTitle — как оно называется
    val site: String = "",
    val room: String = "",
    val roomTitle: String = "",
    val rack: String = "",
    val unit: String = "",
    val heightU: String = "",

    // Оборудование
    val type: String = "",
    val vendor: String = "",
    val model: String = "",
    val hostname: String = "",
    val inventoryNumber: String = "",

    // MGMT
    val mgmtIp: String = "",
    val mgmtMask: String = "",
    val mgmtGateway: String = "",

    val serials: List<String> = emptyList(),
    val connections: List<PortConnection> = emptyList(),
    val comment: String = "",
    /** Имена файлов фотографий в [PhotoStore]. */
    val photos: List<String> = emptyList(),

    val status: SendStatus = SendStatus.DRAFT,
    val sentAt: Long? = null,
    val lastError: String? = null,
) {
    companion object {
        /** Новая пустая запись с одним пустым полем для серийного номера. */
        fun empty() = EquipmentRecord(serials = listOf(""))
    }
}

private fun EquipmentRecord.textFields(): List<String> = listOf(
    site, room, rack, unit, heightU, type, vendor, model, hostname,
    inventoryNumber, mgmtIp, mgmtMask, mgmtGateway, comment,
)

/** Убирает лишние пробелы, пустые серийные номера и пустые подключения. */
fun EquipmentRecord.normalized(): EquipmentRecord = copy(
    site = site.trim(),
    room = room.trim(),
    rack = rack.trim(),
    unit = unit.trim(),
    heightU = heightU.trim(),
    type = type.trim(),
    vendor = vendor.trim(),
    model = model.trim(),
    hostname = hostname.trim(),
    inventoryNumber = inventoryNumber.trim(),
    mgmtIp = mgmtIp.trim(),
    mgmtMask = mgmtMask.trim(),
    mgmtGateway = mgmtGateway.trim(),
    serials = serials.map { it.trim() }.filter { it.isNotEmpty() },
    connections = connections.map { it.trimmed() }.filterNot { it.isBlank() },
    comment = comment.trim(),
)

/** Для редактирования: всегда показываем хотя бы одно поле серийного номера. */
fun EquipmentRecord.forEditing(): EquipmentRecord = copy(serials = serials.ifEmpty { listOf("") })

fun EquipmentRecord.hasData(): Boolean =
    textFields().any { it.isNotBlank() } ||
        serials.any { it.isNotBlank() } ||
        connections.any { !it.isBlank() } ||
        photos.isNotEmpty()

/** Содержимое записи без служебных полей — для сравнения «изменилось ли». */
fun EquipmentRecord.content(): EquipmentRecord =
    normalized().copy(createdAt = 0, updatedAt = 0, status = SendStatus.DRAFT, sentAt = null, lastError = null)

fun EquipmentRecord.displayTitle(): String =
    listOf(vendor, model).filter { it.isNotBlank() }.joinToString(" ")
        .ifBlank { hostname }
        .ifBlank { type }
        .ifBlank { "Без названия" }

fun EquipmentRecord.locationLine(): String = listOfNotNull(
    site.takeIf { it.isNotBlank() },
    room.takeIf { it.isNotBlank() }?.let { hallLabel(roomTitle, it) },
    rack.takeIf { it.isNotBlank() }?.let { "Стойка $it" },
    unit.takeIf { it.isNotBlank() }?.let { u ->
        val height = heightU.takeIf { it.isNotBlank() }?.let { " (${it}U)" }.orEmpty()
        "U$u$height"
    },
).joinToString(" / ")

fun EquipmentRecord.matches(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val haystack = textFields() + serials + connections.flatMap {
        listOf(it.localPort, it.remoteDevice, it.remotePort, it.cableType, it.note)
    }
    return haystack.any { it.contains(q, ignoreCase = true) }
}

private val ipv4Regex = Regex(
    "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(/([0-9]|[12]\\d|3[0-2]))?$"
)
private val ipv6Regex = Regex("^[0-9a-fA-F:]+(/\\d{1,3})?$")

/** Пустое значение считается корректным: поля необязательные. */
fun isValidIp(value: String): Boolean {
    val v = value.trim()
    if (v.isEmpty()) return true
    return ipv4Regex.matches(v) || (v.contains(':') && ipv6Regex.matches(v))
}

fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("ru")).format(Date(millis))

val EQUIPMENT_TYPES = listOf("Сервер", "Коммутатор", "СХД", "Маршрутизатор", "Межсетевой экран", "ИБП (UPS)", "PDU", "Другое")

/** Есть ли данные помимо места установки (площадка/помещение/стойка подставляются автоматически). */
fun EquipmentRecord.hasOwnData(): Boolean = copy(site = "", room = "", roomTitle = "", rack = "").hasData()

// ---------- Маскировка IP для письма ----------

/** IPv4 внутри произвольного текста: захватываем первый октет. */
private val ipv4InText = Regex("(?<![\\d.])\\d{1,3}(?=\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(?!\\d|\\.\\d))")

/** «192.168.1.50» → «*.168.1.50». Работает и внутри текста (комментарии, примечания). */
fun maskIpv4(text: String): String = ipv4InText.replace(text, "*")

/** Значение поля с IP: IPv4 — скрываем первый октет, IPv6 — первую группу. */
fun maskIpField(value: String): String {
    val masked = maskIpv4(value)
    if (masked != value) return masked
    val v = value.trim()
    return if (v.contains(':') && v.first() != ':') "*" + v.substring(v.indexOf(':')) else value
}

/** Копия записи для письма: все IP-адреса с замазанным первым октетом. Маска сети не трогается. */
fun EquipmentRecord.maskedForEmail(): EquipmentRecord = copy(
    mgmtIp = maskIpField(mgmtIp),
    mgmtGateway = maskIpField(mgmtGateway),
    hostname = maskIpv4(hostname),
    comment = maskIpv4(comment),
    connections = connections.map {
        it.copy(
            localPort = maskIpv4(it.localPort),
            remoteDevice = maskIpv4(it.remoteDevice),
            remotePort = maskIpv4(it.remotePort),
            note = maskIpv4(it.note),
        )
    },
)
