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
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Фотографии оборудования: хранятся в памяти приложения уменьшенными до [MAX_SIDE] px,
 * чтобы письмо с несколькими снимками не было слишком тяжёлым.
 */
class PhotoStore(private val context: Context) {

    private val dir = File(context.filesDir, "photos").apply { mkdirs() }
    private val captureDir = File(context.cacheDir, "camera").apply { mkdirs() }

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

            val name = "${UUID.randomUUID()}.jpg"
            FileOutputStream(File(dir, name)).use { result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            name
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось сохранить фото $uri", e)
            null
        }
    }

    /** Миниатюра для показа в интерфейсе. */
    suspend fun thumbnail(name: String, size: Int): Bitmap? = withContext(Dispatchers.IO) {
        val f = file(name)
        if (!f.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Удаляет снимки, на которые больше не ссылается ни одна запись. */
    suspend fun cleanup(keep: Set<String>) = withContext(Dispatchers.IO) {
        val threshold = System.currentTimeMillis() - CLEANUP_AGE_MS
        dir.listFiles()?.forEach { f ->
            if (f.name !in keep && f.lastModified() < threshold) f.delete()
        }
        captureDir.listFiles()?.forEach { f -> if (f.lastModified() < threshold) f.delete() }
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
