package app.fridge.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Who this phone is, and who the other phone at the fridge is. [secret] comes from the QR the two
 * phones shared once: it is the key every word between them is sealed with. With a secret but no
 * [partnerId] yet, this phone has shown its QR and waits for the other one to come by.
 */
data class FridgeSettings(
    val onboarded: Boolean = false,
    val deviceId: String = "",
    val myName: String = "",
    val partnerId: String = "",
    val partnerName: String = "",
    val secret: ByteArray? = null,
    /** Keep listening for the other phone at home with the app closed. */
    val hum: Boolean = false,
    val lastMet: Long = 0,
) {
    val paired: Boolean get() = secret != null && partnerId.isNotEmpty()
    val waiting: Boolean get() = secret != null && partnerId.isEmpty()

    // The secret is a ByteArray: compare it by content, and keep it out of toString.
    override fun equals(other: Any?): Boolean = other is FridgeSettings && onboarded == other.onboarded && deviceId == other.deviceId &&
        myName == other.myName && partnerId == other.partnerId && partnerName == other.partnerName &&
        secret.contentEquals(other.secret) && hum == other.hum && lastMet == other.lastMet

    override fun hashCode(): Int = listOf(deviceId, myName, partnerId, partnerName, hum, lastMet).hashCode() * 31 + secret.contentHashCode()

    override fun toString(): String = "FridgeSettings(me=$myName/$deviceId, partner=$partnerName/$partnerId, paired=$paired, hum=$hum)"
}

/** One per file per process, as DataStore requires; tests make a second one to play the other phone. */
class SettingsStore(context: Context, file: String = "settings") {
    private val store = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(file) }
    private val onboardedKey = booleanPreferencesKey("onboarded")
    private val deviceKey = stringPreferencesKey("deviceId")
    private val myNameKey = stringPreferencesKey("myName")
    private val partnerIdKey = stringPreferencesKey("partnerId")
    private val partnerNameKey = stringPreferencesKey("partnerName")
    private val secretKey = stringPreferencesKey("secret")
    private val humKey = booleanPreferencesKey("hum")
    private val lastMetKey = longPreferencesKey("lastMet")

    val settings: Flow<FridgeSettings> = store.data.map { p ->
        FridgeSettings(
            onboarded = p[onboardedKey] ?: false,
            deviceId = p[deviceKey] ?: "",
            myName = p[myNameKey] ?: "",
            partnerId = p[partnerIdKey] ?: "",
            partnerName = p[partnerNameKey] ?: "",
            secret = p[secretKey]?.let { Base64.decode(it, Base64.NO_WRAP) },
            hum = p[humKey] ?: false,
            lastMet = p[lastMetKey] ?: 0,
        )
    }

    suspend fun now(): FridgeSettings = settings.first()

    /** This phone's id, made once on first use. Short enough to read in a log, long enough never to clash. */
    suspend fun deviceId(): String {
        now().deviceId.takeIf { it.isNotEmpty() }?.let { return it }
        val id = UUID.randomUUID().toString().replace("-", "").take(16)
        store.edit { if (it[deviceKey].isNullOrEmpty()) it[deviceKey] = id }
        return now().deviceId
    }

    suspend fun finishWelcome(name: String) = store.edit {
        it[onboardedKey] = true
        it[myNameKey] = name.trim()
    }

    suspend fun setMyName(name: String) = store.edit { it[myNameKey] = name.trim() }

    /** This phone made the QR: it holds the secret and waits for whoever scans it. */
    suspend fun offer(secret: ByteArray) = store.edit {
        it[secretKey] = Base64.encodeToString(secret, Base64.NO_WRAP)
        it.remove(partnerIdKey)
        it.remove(partnerNameKey)
    }

    /** Paired: from scanning the other's QR, or from their phone turning up with the right secret. */
    suspend fun pair(secret: ByteArray, partnerId: String, partnerName: String) = store.edit {
        it[secretKey] = Base64.encodeToString(secret, Base64.NO_WRAP)
        it[partnerIdKey] = partnerId
        it[partnerNameKey] = partnerName.trim()
    }

    suspend fun setPartnerName(name: String) = store.edit { if (name.isNotBlank()) it[partnerNameKey] = name.trim() }

    /** Forget the other phone. The door stays as it is. */
    suspend fun unpair() = store.edit {
        it.remove(secretKey)
        it.remove(partnerIdKey)
        it.remove(partnerNameKey)
        it[humKey] = false
    }

    suspend fun setHum(on: Boolean) = store.edit { it[humKey] = on }

    suspend fun met(at: Long) = store.edit { it[lastMetKey] = at }
}
