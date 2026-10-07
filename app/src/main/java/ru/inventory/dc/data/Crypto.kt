package ru.inventory.dc.data

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Неверный пароль или файл повреждён. */
class WrongPasswordException : Exception("Неверный пароль")

/** Файл не является базой приложения. */
class NotADatabaseException : Exception("Файл не является базой «Инвентаризация ЦОД»")

/**
 * Шифрование базы.
 *
 * Формат файла:
 * `DCINV` | версия (1 байт) | итерации PBKDF2 (4 байта) | соль (16) | IV (12) | шифротекст AES-256-GCM.
 * Ключ получается из пароля через PBKDF2-HMAC-SHA256. Заголовок защищён от подмены (AAD).
 * Неверный пароль или изменённый файл обнаруживаются проверкой GCM-тега.
 */
object VaultCrypto {

    private val MAGIC = "DCINV".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION: Byte = 1
    const val DEFAULT_ITERATIONS = 310_000
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val HEADER_SIZE = 5 + 1 + 4 + SALT_SIZE

    private val random = SecureRandom()

    /** Ключ, полученный из пароля, вместе с параметрами для заголовка. */
    class PasswordKey(val key: SecretKey, val salt: ByteArray, val iterations: Int)

    fun newSalt(): ByteArray = ByteArray(SALT_SIZE).also(random::nextBytes)

    fun randomKeyBytes(): ByteArray = ByteArray(32).also(random::nextBytes)

    fun deriveKey(password: CharArray, salt: ByteArray = newSalt(), iterations: Int = DEFAULT_ITERATIONS): PasswordKey {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return PasswordKey(SecretKeySpec(bytes, "AES"), salt, iterations)
        } finally {
            spec.clearPassword()
        }
    }

    fun isDatabase(bytes: ByteArray): Boolean =
        bytes.size > HEADER_SIZE + IV_SIZE && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    /** Параметры из заголовка — чтобы получить ключ из пароля при открытии. */
    fun readHeader(bytes: ByteArray): Pair<ByteArray, Int> {
        if (!isDatabase(bytes)) throw NotADatabaseException()
        val input = DataInputStream(bytes.inputStream())
        input.skipBytes(MAGIC.size)
        val version = input.readByte()
        if (version != FORMAT_VERSION) throw NotADatabaseException()
        val iterations = input.readInt()
        val salt = ByteArray(SALT_SIZE).also { input.readFully(it) }
        return salt to iterations
    }

    fun encryptFile(key: PasswordKey, plain: ByteArray): ByteArray {
        val header = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).apply {
                write(MAGIC)
                writeByte(FORMAT_VERSION.toInt())
                writeInt(key.iterations)
                write(key.salt)
            }
        }.toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key.key)
        cipher.updateAAD(header)
        return header + cipher.iv + cipher.doFinal(plain)
    }

    fun decryptFile(key: SecretKey, bytes: ByteArray): ByteArray {
        if (!isDatabase(bytes)) throw NotADatabaseException()
        val header = bytes.copyOfRange(0, HEADER_SIZE)
        val iv = bytes.copyOfRange(HEADER_SIZE, HEADER_SIZE + IV_SIZE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        try {
            return cipher.doFinal(bytes, HEADER_SIZE + IV_SIZE, bytes.size - HEADER_SIZE - IV_SIZE)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        }
    }

    /** Шифрование небольших данных (фото, черновик) ключом данных базы: IV | шифротекст. */
    fun encryptBlob(key: SecretKey, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(plain)
    }

    fun decryptBlob(key: SecretKey, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, data, 0, IV_SIZE))
        return cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE)
    }
}
