package app.fridge.sync

import app.fridge.data.FridgeSettings
import app.fridge.data.Item
import app.fridge.data.ItemDao
import app.fridge.data.SettingsStore
import org.json.JSONObject
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * For when the two of you are apart for a while: the whole door, sealed with the shared key, as one
 * file to send through any messenger or Quick Share. Only the other phone can open it, and opening it
 * merges it like a meeting at home would.
 */
object Parcel {
    private val MAGIC = "FRIDGEPARCEL1".toByteArray()
    private val AAD = "fridge-door/parcel".toByteArray()

    sealed interface Opened {
        data class Took(val news: List<Item>, val from: String) : Opened
        data object NotOurs : Opened
        data object OwnParcel : Opened
    }

    suspend fun pack(dao: ItemDao, s: FridgeSettings, me: String): ByteArray {
        val secret = requireNotNull(s.secret) { "not paired" }
        val body = JSONObject().put("dev", me).put("name", s.myName).put("items", Wire.items(dao.everything()))
        val zipped = java.io.ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(body.toString().toByteArray()) } }.toByteArray()
        return MAGIC + Seal.close(Seal.key(secret), zipped, AAD)
    }

    suspend fun unpack(bytes: ByteArray, dao: ItemDao, settings: SettingsStore): Opened {
        val s = settings.now()
        val secret = s.secret ?: return Opened.NotOurs
        if (bytes.size <= MAGIC.size || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return Opened.NotOurs
        val zipped = Seal.open(Seal.key(secret), bytes.copyOfRange(MAGIC.size, bytes.size), AAD) ?: return Opened.NotOurs
        val body = JSONObject(String(GZIPInputStream(zipped.inputStream()).use { it.readBytes() }))
        val me = settings.deviceId()
        val from = body.optString("dev")
        if (from == me) return Opened.OwnParcel
        if (s.partnerId.isNotEmpty() && from != s.partnerId) return Opened.NotOurs
        if (s.partnerId.isEmpty()) settings.pair(secret, from, body.optString("name"))
        return Opened.Took(Merge.take(dao, Wire.items(body.getJSONArray("items")), me), body.optString("name"))
    }
}
