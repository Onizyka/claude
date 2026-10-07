package ru.inventory.dc.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Справочник мест, который ведёт сам пользователь: площадка → машзалы (помещения) → стойки.
 * Хранится в зашифрованной базе; в коде приложения конкретных площадок нет.
 */
@Serializable
data class Site(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Как называются помещения на этой площадке: «Машзал» или «Помещение». */
    val hallTitle: String = DEFAULT_HALL_TITLE,
    val halls: List<Hall> = emptyList(),
)

@Serializable
data class Hall(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val racks: List<String> = emptyList(),
)

private fun String.same(other: String) = trim().equals(other.trim(), ignoreCase = true)

fun List<Site>.findSite(name: String): Site? = firstOrNull { it.name.same(name) }

fun Site.findHall(name: String): Hall? = halls.firstOrNull { it.name.same(name) }

/** Стойки выбранного помещения — для подсказок в редакторе. */
fun List<Site>.racksOf(site: String, hall: String): List<String> =
    findSite(site)?.findHall(hall)?.racks.orEmpty()

// ---------- Площадки ----------

fun List<Site>.addSite(name: String, hallTitle: String): List<Site> =
    if (name.isBlank() || findSite(name) != null) this
    else this + Site(name = name.trim(), hallTitle = hallTitle)

fun List<Site>.editSite(id: String, name: String, hallTitle: String): List<Site> =
    map { if (it.id == id) it.copy(name = name.trim(), hallTitle = hallTitle) else it }

fun List<Site>.removeSite(id: String): List<Site> = filterNot { it.id == id }

// ---------- Помещения ----------

private fun List<Site>.updateSite(siteId: String, transform: (Site) -> Site): List<Site> =
    map { if (it.id == siteId) transform(it) else it }

fun List<Site>.addHall(siteId: String, name: String): List<Site> = updateSite(siteId) { s ->
    if (name.isBlank() || s.findHall(name) != null) s else s.copy(halls = s.halls + Hall(name = name.trim()))
}

fun List<Site>.renameHall(siteId: String, hallId: String, name: String): List<Site> = updateSite(siteId) { s ->
    s.copy(halls = s.halls.map { if (it.id == hallId) it.copy(name = name.trim()) else it })
}

fun List<Site>.removeHall(siteId: String, hallId: String): List<Site> = updateSite(siteId) { s ->
    s.copy(halls = s.halls.filterNot { it.id == hallId })
}

// ---------- Стойки ----------

private fun List<Site>.updateHall(siteId: String, hallId: String, transform: (Hall) -> Hall): List<Site> =
    updateSite(siteId) { s -> s.copy(halls = s.halls.map { if (it.id == hallId) transform(it) else it }) }

fun List<Site>.addRack(siteId: String, hallId: String, rack: String): List<Site> = updateHall(siteId, hallId) { h ->
    if (rack.isBlank() || h.racks.any { it.same(rack) }) h else h.copy(racks = h.racks + rack.trim())
}

fun List<Site>.renameRack(siteId: String, hallId: String, old: String, new: String): List<Site> =
    updateHall(siteId, hallId) { h -> h.copy(racks = h.racks.map { if (it.same(old)) new.trim() else it }) }

fun List<Site>.removeRack(siteId: String, hallId: String, rack: String): List<Site> =
    updateHall(siteId, hallId) { h -> h.copy(racks = h.racks.filterNot { it.same(rack) }) }

/**
 * Запоминает место из записи или выбора: недостающие площадка, помещение и стойка
 * добавляются в справочник автоматически.
 */
fun List<Site>.learnPlace(site: String, hall: String, rack: String, hallTitle: String = DEFAULT_HALL_TITLE): List<Site> {
    if (site.isBlank()) return this
    var result = addSite(site, hallTitle.ifBlank { DEFAULT_HALL_TITLE })
    val s = result.findSite(site) ?: return result
    if (hall.isBlank()) return result
    result = result.addHall(s.id, hall)
    val h = result.findSite(site)?.findHall(hall) ?: return result
    return result.addRack(s.id, h.id, rack)
}

/** Первый запуск версии со справочником мест: собираем его из уже введённых данных. */
fun sitesFromHistory(placement: Placement, records: List<EquipmentRecord>): List<Site> {
    var sites = emptyList<Site>().learnPlace(placement.site, placement.hall, placement.rack, placement.hallTitle)
    records.forEach { r -> sites = sites.learnPlace(r.site, r.room, r.rack, r.roomTitle) }
    return sites
}
