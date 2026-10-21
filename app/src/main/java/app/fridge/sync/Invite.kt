package app.fridge.sync

import android.util.Base64

/**
 * What the QR says: the shared secret, the phone that shows it, and its owner's name. Scanning it
 * pairs the scanning phone at once; the showing phone learns who scanned when they first meet.
 */
data class Invite(val secret: ByteArray, val deviceId: String, val name: String) {
    fun encode(): String = listOf(PREFIX, b64(secret), deviceId, b64(name.toByteArray())).joinToString(":")

    override fun equals(other: Any?) = other is Invite && secret.contentEquals(other.secret) && deviceId == other.deviceId && name == other.name
    override fun hashCode() = deviceId.hashCode()

    companion object {
        private const val PREFIX = "fridgedoor1"
        private const val FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

        fun decode(text: String): Invite? = runCatching {
            val p = text.trim().split(":")
            if (p.size != 4 || p[0] != PREFIX) return null
            val secret = Base64.decode(p[1], FLAGS)
            if (secret.size != 32 || p[2].isBlank()) return null
            Invite(secret, p[2], String(Base64.decode(p[3], FLAGS)))
        }.getOrNull()

        private fun b64(b: ByteArray) = Base64.encodeToString(b, FLAGS)
    }
}
