package com.offlineshare.app.security

import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    private const val KEY_LENGTH_BITS = 256
    private const val PBKDF2_ITERATIONS = 10000

    private val secureRandom = SecureRandom()

    fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun encryptChunk(data: ByteArray, offset: Int, length: Int, key: SecretKeySpec): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))

        val encrypted = cipher.doFinal(data, offset, length)
        val result = ByteArray(IV_LENGTH_BYTES + encrypted.size)
        System.arraycopy(iv, 0, result, 0, IV_LENGTH_BYTES)
        System.arraycopy(encrypted, 0, result, IV_LENGTH_BYTES, encrypted.size)
        return result
    }

    fun decryptChunk(data: ByteArray, offset: Int, length: Int, key: SecretKeySpec): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES)
        System.arraycopy(data, offset, iv, 0, IV_LENGTH_BYTES)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(data, offset + IV_LENGTH_BYTES, length - IV_LENGTH_BYTES)
    }
}