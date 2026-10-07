package ru.inventory.dc.data

import kotlinx.serialization.Serializable

/** Категории запоминаемых значений. */
object DictCategory {
    const val TYPE = "type"
    const val VENDOR = "vendor"
    const val MODEL = "model"
    const val DEVICE = "device"
    const val CABLE = "cable"
}

/**
 * Запомненное значение. [parent] — уточнение (для модели — производитель).
 * [count] — сколько раз использовалось: часто используемые предлагаются первыми.
 */
@Serializable
data class DictEntry(
    val category: String,
    val value: String,
    val parent: String = "",
    val count: Int = 1,
    val lastUsed: Long = 0,
) {
    fun sameKey(category: String, value: String, parent: String): Boolean =
        this.category == category &&
            this.value.equals(value.trim(), ignoreCase = true) &&
            this.parent.equals(parent.trim(), ignoreCase = true)
}

/** Запоминает значения из записи (справочник пополняется при сохранении и отправке). */
fun List<DictEntry>.learn(record: EquipmentRecord, now: Long = System.currentTimeMillis()): List<DictEntry> {
    val items = buildList {
        add(Triple(DictCategory.TYPE, record.type, ""))
        add(Triple(DictCategory.VENDOR, record.vendor, ""))
        add(Triple(DictCategory.MODEL, record.model, record.vendor))
        record.connections.forEach { c ->
            add(Triple(DictCategory.DEVICE, c.remoteDevice, ""))
            if (CABLE_TYPES.none { it.equals(c.cableType, ignoreCase = true) }) {
                add(Triple(DictCategory.CABLE, c.cableType, ""))
            }
        }
    }.filter { it.second.isNotBlank() }
        .distinctBy { Triple(it.first, it.second.trim().lowercase(), it.third.trim().lowercase()) }
    if (items.isEmpty()) return this
    val result = toMutableList()
    items.forEach { (category, value, parent) ->
        val index = result.indexOfFirst { it.sameKey(category, value, parent) }
        if (index >= 0) {
            val old = result[index]
            // Сохраняем последнее написание: «kraftway» → «Kraftway».
            result[index] = old.copy(value = value.trim(), count = old.count + 1, lastUsed = now)
        } else {
            result += DictEntry(category, value.trim(), parent.trim(), 1, now)
        }
    }
    return result
}

fun List<DictEntry>.forget(entry: DictEntry): List<DictEntry> =
    filterNot { it.sameKey(entry.category, entry.value, entry.parent) }

/**
 * Подсказки для поля: сначала совпадения с начала слова («kr» → Kraftway), затем по вхождению;
 * внутри группы — чаще используемые выше. Регистр не учитывается.
 */
fun rankSuggestions(options: List<SuggestionItem>, query: String, limit: Int = 8): List<SuggestionItem> {
    val q = query.trim()
    val unique = options.distinctBy { it.title.lowercase() }
    if (q.isEmpty()) return unique.sortedByDescending { it.weight }.take(limit)
    val prefix = unique.filter { it.title.startsWith(q, ignoreCase = true) }
    val wordPrefix = unique.filter { o ->
        o !in prefix && o.title.split(' ', '-', '_', '/', '.').any { it.startsWith(q, ignoreCase = true) }
    }
    val contains = unique.filter { o -> o !in prefix && o !in wordPrefix && o.title.contains(q, ignoreCase = true) }
    val ranked = prefix.sortedByDescending { it.weight } +
        wordPrefix.sortedByDescending { it.weight } +
        contains.sortedByDescending { it.weight }
    // Если введено ровно единственное подходящее значение — подсказывать нечего.
    if (ranked.size == 1 && ranked[0].title.equals(q, ignoreCase = true)) return emptyList()
    return ranked.take(limit)
}

/** Вариант подсказки. [entry] заполнен для запомненных значений — их можно удалить из справочника. */
data class SuggestionItem(
    val title: String,
    val subtitle: String? = null,
    val weight: Int = 0,
    val entry: DictEntry? = null,
)

/** Подсказки категории из справочника (+ статичный каталог). */
fun List<DictEntry>.suggestions(category: String, parent: String? = null): List<SuggestionItem> =
    filter { it.category == category && (parent == null || it.parent.equals(parent.trim(), ignoreCase = true)) }
        .map { SuggestionItem(it.value, weight = it.count, entry = it) }
