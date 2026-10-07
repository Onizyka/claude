package ru.inventory.dc.data

/**
 * Справочник площадок. Чтобы добавить машзал или ЦОД — достаточно поправить этот список.
 * [hallTitle] — как называется помещение на площадке: машзал в ЦОД, помещение в офисе.
 */
data class DataCenter(val name: String, val halls: List<String>, val hallTitle: String = "Машзал")

val DATA_CENTERS = listOf(
    DataCenter("оЦОД", listOf("2", "4", "5", "9")),
    DataCenter("рЦОД", emptyList()),
    DataCenter("Офис", listOf("949", "1416"), hallTitle = "Помещение"),
)

/** Название помещения для площадки: «Машзал» или «Помещение». */
fun hallTitle(site: String): String =
    DATA_CENTERS.firstOrNull { it.name.equals(site.trim(), ignoreCase = true) }?.hallTitle ?: "Машзал"

/** «5» → «Машзал 5», в офисе «949» → «Помещение 949»; произвольный текст оставляем как есть. */
fun hallLabel(site: String, hall: String): String {
    val h = hall.trim()
    return if (h.isNotEmpty() && h.all { it.isDigit() }) "${hallTitle(site)} $h" else h
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
