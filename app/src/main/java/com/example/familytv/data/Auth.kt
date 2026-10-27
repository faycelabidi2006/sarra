package com.example.familytv.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** حسابات الدخول إلى التطبيق. تُخزَّن بصمة (PBKDF2) لكلمة المرور، لا النص نفسه. */
object Auth {
    const val DEFAULT_USER = "faycel"
    private const val DEFAULT_SALT = "3f5a16119698592f02558788501b975c"
    private const val DEFAULT_HASH = "5a8c8ec34f85931f07e8300f18008a6646677a87390a2030623946ea035c5922"
    private const val ITERATIONS = 50_000

    fun defaultOwner() = AppUser(DEFAULT_USER, DEFAULT_SALT, DEFAULT_HASH, owner = true)

    fun newSalt(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    fun hash(pass: String, saltHex: String): String {
        val salt = ByteArray(saltHex.length / 2) { saltHex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val spec = PBEKeySpec(pass.toCharArray(), salt, ITERATIONS, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return key.joinToString("") { "%02x".format(it) }
    }

    fun verify(pass: String, user: AppUser): Boolean =
        MessageDigest.isEqual(hash(pass, user.salt).toByteArray(), user.hash.toByteArray())
}
