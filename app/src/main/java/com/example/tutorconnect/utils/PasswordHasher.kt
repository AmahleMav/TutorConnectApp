package com.example.tutorconnect.utils

import android.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object PasswordHasher {

    fun verifyHashedPassword(hashedPassword: String, password: String): Boolean {
        try {
            val decoded = Base64.decode(hashedPassword, Base64.DEFAULT)
            if (decoded.size < 16) return false

            val subkeyLength = 32
            val salt = decoded.copyOfRange(1, 17)
            val storedSubkey = decoded.copyOfRange(17, 17 + subkeyLength)

            val derivedKey = pbkdf2(password.toByteArray(Charsets.UTF_8), salt, 1000, subkeyLength)
            return storedSubkey.contentEquals(derivedKey)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        val keySpec = SecretKeySpec(password, "HmacSHA1")
        mac.init(keySpec)

        var blockIndex = 1
        val hashLength = mac.macLength
        val output = ByteArray(keyLength)
        var offset = 0

        while (offset < keyLength) {
            val u = F(mac, salt, iterations, blockIndex)
            val remaining = keyLength - offset
            val length = if (remaining > hashLength) hashLength else remaining
            System.arraycopy(u, 0, output, offset, length)
            offset += length
            blockIndex++
        }
        return output
    }

    private fun F(mac: Mac, salt: ByteArray, iterations: Int, blockIndex: Int): ByteArray {
        val blockIndexBytes = byteArrayOf(
            ((blockIndex shr 24) and 0xff).toByte(),
            ((blockIndex shr 16) and 0xff).toByte(),
            ((blockIndex shr 8) and 0xff).toByte(),
            (blockIndex and 0xff).toByte()
        )

        var u = mac.doFinal(salt + blockIndexBytes)
        val result = u.clone()
        for (i in 1 until iterations) {
            u = mac.doFinal(u)
            for (j in result.indices) {
                result[j] = (result[j].toInt() xor u[j].toInt()).toByte()
            }
        }
        return result
    }
}
