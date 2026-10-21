package app.fridge.sync

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The two phones share one 32-byte secret, passed once through the QR. Everything between them —
 * over the home Wi-Fi or in a parcel file — is sealed with AES-GCM under a key derived from it, so
 * nothing in between can read it and nothing without the secret can talk to the fridge.
 */
object Seal {
    private val random = SecureRandom()

    fun newSecret(): ByteArray = ByteArray(32).also(random::nextBytes)

    fun nonce(size: Int = 16): ByteArray = ByteArray(size).also(random::nextBytes)

    fun key(secret: ByteArray): SecretKeySpec = SecretKeySpec(sha256("fridge-door/key".toByteArray() + secret), "AES")

    /**
     * What the phones call out on the network to find each other: derived from the secret, so it
     * says nothing about who they are, and other couples' fridges in the same building stay apart.
     */
    fun tag(secret: ByteArray): String = sha256("fridge-door/tag".toByteArray() + secret).take(5).joinToString("") { "%02x".format(it) }

    fun close(key: SecretKeySpec, plain: ByteArray, aad: ByteArray): ByteArray {
        val iv = nonce(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        c.updateAAD(aad)
        return iv + c.doFinal(plain)
    }

    /** Null when the data wasn't sealed with this key (or for this place in the conversation). */
    fun open(key: SecretKeySpec, sealed: ByteArray, aad: ByteArray): ByteArray? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12))
        c.updateAAD(aad)
        c.doFinal(sealed, 12, sealed.size - 12)
    }.getOrNull()

    private fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)
}
