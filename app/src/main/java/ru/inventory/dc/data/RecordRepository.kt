package ru.inventory.dc.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Журнал записей: хранится локально в JSON-файле внутри приложения. */
class RecordRepository(context: Context) {

    private val file = File(context.filesDir, "records.json")
    private val serializer = ListSerializer(EquipmentRecord.serializer())
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val mutex = Mutex()

    private val _records = MutableStateFlow(load())
    val records: StateFlow<List<EquipmentRecord>> = _records.asStateFlow()

    private fun load(): List<EquipmentRecord> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось прочитать журнал, файл сохранён как копия", e)
            file.copyTo(File(file.parentFile, "records.corrupt-${System.currentTimeMillis()}.json"), overwrite = true)
            emptyList()
        }
    }

    // ---------- Черновик: сохраняется автоматически, чтобы не потерять данные ----------

    private val draftFile = File(context.filesDir, "draft.json")

    fun loadDraft(): EquipmentRecord? = try {
        if (draftFile.exists()) json.decodeFromString(EquipmentRecord.serializer(), draftFile.readText()) else null
    } catch (e: Exception) {
        Log.w(TAG, "Не удалось прочитать черновик", e)
        null
    }

    suspend fun saveDraftFile(record: EquipmentRecord) = withContext(Dispatchers.IO) {
        try {
            draftFile.writeText(json.encodeToString(EquipmentRecord.serializer(), record))
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось сохранить черновик", e)
        }
    }

    suspend fun upsert(record: EquipmentRecord) = mutate { list ->
        val index = list.indexOfFirst { it.id == record.id }
        if (index >= 0) list.toMutableList().also { it[index] = record } else list + record
    }

    /** Слияние с записями из резервной копии: при совпадении берём более свежую версию. */
    suspend fun mergeAll(incoming: List<EquipmentRecord>) = mutate { list ->
        val byId = list.associateBy { it.id }.toMutableMap()
        incoming.forEach { r ->
            val existing = byId[r.id]
            if (existing == null || r.updatedAt > existing.updatedAt) byId[r.id] = r
        }
        byId.values.toList()
    }

    suspend fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    private suspend fun mutate(block: (List<EquipmentRecord>) -> List<EquipmentRecord>) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val updated = block(_records.value).sortedByDescending { it.updatedAt }
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeText(json.encodeToString(serializer, updated))
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
                _records.value = updated
            }
        }

    private companion object {
        const val TAG = "RecordRepository"
    }
}
