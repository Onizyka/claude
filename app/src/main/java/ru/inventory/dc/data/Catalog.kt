package ru.inventory.dc.data

/** Как называются помещения на площадке — выбирается для каждой площадки. */
const val DEFAULT_HALL_TITLE = "Машзал"
val HALL_TITLES = listOf("Машзал", "Помещение")

/** «5» → «Машзал 5»; произвольный текст («Серверная») оставляем как есть. */
fun hallLabel(title: String, hall: String): String {
    val h = hall.trim()
    return if (h.isNotEmpty() && h.all { it.isDigit() }) "${title.ifBlank { DEFAULT_HALL_TITLE }} $h" else h
}

/** Варианты кабеля в подключениях; третий вариант — ввести своё значение. */
val CABLE_TYPES = listOf("Медь", "Оптика")

/** Модель оборудования из справочника. */
data class ModelPreset(
    val name: String,
    val type: String,
    val heightU: Int?,
    val description: String,
)

data class Vendor(val name: String, val models: List<ModelPreset> = emptyList())

/** Производители: для YADRO список моделей выпадает при выборе. */
val VENDORS = listOf(
    Vendor(
        "YADRO",
        listOf(
            ModelPreset("VEGMAN S220", "Сервер", 2, "2U · 2× Xeon Scalable v2 · 16 LFF + 4 SFF"),
            ModelPreset("VEGMAN S320", "Сервер", 3, "3U · 2× Xeon Scalable v2 · 32 LFF"),
            ModelPreset("X2-205", "Сервер", 2, "2U · 2× Xeon Scalable 2 Gen · до 12 LFF / 24 SFF"),
        ),
    ),
    Vendor("Aquarius"),
    Vendor("Dell"),
    Vendor("HPE"),
    Vendor("Huawei"),
    Vendor("xFusion"),
    Vendor("Lenovo"),
    Vendor("Supermicro"),
)

fun findVendor(name: String): Vendor? = VENDORS.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
