package ru.inventory.dc.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Фотографии оборудования: хранятся во внутренней памяти приложения в зашифрованном виде
 * (ключ данных базы), уменьшенными до [MAX_SIDE] px, чтобы письмо не было слишком тяжёлым.
 * Расшифрованные копии создаются только на время отправки письма и сразу удаляются.
 */
class PhotoStore(private val context: Context, private val db: InventoryDatabase) {

    private val dir = File(context.filesDir, "photos").apply { mkdirs() }
    private val captureDir = File(context.cacheDir, "camera").apply { mkdirs() }
    private val mailDir = File(context.cacheDir, "mail")

    fun file(name: String): File = File(dir, name)

    /** URI для камеры: снимок сначала пишется во временный файл. */
    fun newCaptureUri(): Uri {
        val target = File(captureDir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }

    /** Копирует снимок в хранилище (с уменьшением и учётом поворота). Возвращает имя файла. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null

            val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
            val result = scaleAndRotate(decoded, rotation)

            val jpeg = ByteArrayOutputStream().use {
                result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
                it.toByteArray()
            }
            val name = "${UUID.randomUUID()}.enc"
            File(dir, name).writeBytes(db.encryptData(jpeg))
            name
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось сохранить фото $uri", e)
            null
        }
    }

    /** Миниатюра для показа в интерфейсе (расшифровывается в памяти). */
    suspend fun thumbnail(name: String, size: Int): Bitmap? = withContext(Dispatchers.IO) {
        val bytes = readPlain(name) ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun readPlain(name: String): ByteArray? {
        val f = file(name)
        if (!f.exists()) return null
        return runCatching {
            // Фото старых версий (.jpg) ещё не зашифрованы — читаем как есть.
            if (name.endsWith(".enc")) db.decryptData(f.readBytes()) else f.readBytes()
        }.onFailure { Log.w(TAG, "Не удалось прочитать фото $name", it) }.getOrNull()
    }

    /** Временные расшифрованные копии для вложений письма. После отправки вызвать [clearMailFiles]. */
    suspend fun prepareMailFiles(names: List<String>): List<File> = withContext(Dispatchers.IO) {
        mailDir.mkdirs()
        names.mapIndexedNotNull { i, name ->
            val bytes = readPlain(name) ?: return@mapIndexedNotNull null
            File(mailDir, "photo_${i + 1}.jpg").apply { writeBytes(bytes) }
        }
    }

    suspend fun clearMailFiles() = withContext(Dispatchers.IO) {
        mailDir.listFiles()?.forEach { it.delete() }
    }

    /** Снимок с камеры уже скопирован в зашифрованное хранилище — исходник удаляем сразу. */
    suspend fun clearCaptures() = withContext(Dispatchers.IO) {
        captureDir.listFiles()?.forEach { it.delete() }
    }

    /** Шифрует фото, оставшиеся от версий без шифрования. Возвращает соответствие старых и новых имён. */
    suspend fun encryptLegacyPhotos(): Map<String, String> = withContext(Dispatchers.IO) {
        val renamed = mutableMapOf<String, String>()
        dir.listFiles { f -> f.name.endsWith(".jpg") }?.forEach { f ->
            runCatching {
                val newName = f.nameWithoutExtension + ".enc"
                File(dir, newName).writeBytes(db.encryptData(f.readBytes()))
                f.delete()
                renamed[f.name] = newName
            }.onFailure { Log.w(TAG, "Не удалось зашифровать ${f.name}", it) }
        }
        renamed
    }

    /** Полная очистка (сброс базы). */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        captureDir.listFiles()?.forEach { it.delete() }
        mailDir.listFiles()?.forEach { it.delete() }
    }

    /** Удаляет снимки, на которые больше не ссылается ни одна запись. */
    suspend fun cleanup(keep: Set<String>) = withContext(Dispatchers.IO) {
        val threshold = System.currentTimeMillis() - CLEANUP_AGE_MS
        dir.listFiles()?.forEach { f ->
            if (f.name !in keep && f.lastModified() < threshold) f.delete()
        }
        captureDir.listFiles()?.forEach { f -> if (f.lastModified() < threshold) f.delete() }
        mailDir.listFiles()?.forEach { it.delete() }
    }

    private fun scaleAndRotate(bitmap: Bitmap, rotation: Int): Bitmap {
        val scale = min(1f, MAX_SIDE.toFloat() / max(bitmap.width, bitmap.height))
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val TAG = "PhotoStore"
        const val MAX_SIDE = 1920
        const val JPEG_QUALITY = 85
        const val CLEANUP_AGE_MS = 60 * 60 * 1000L
    }
}
