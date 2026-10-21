package app.fridge.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What can hang on the door. Stored by name, so the order here never matters. */
enum class Kind {
    /** A line on the shopping pad. done = crossed out, doneBy = who crossed it, state 1 = a staple that comes back. */
    GROCERY,

    /** A chore on the "whose turn" card. doneBy = whose turn it is, state 1 = the one on turn asked to swap. */
    CHORE,

    /** One chore done by author on [Item.day]: what the month's balance is counted from. text = the chore. */
    CHORE_LOG,

    /** Someone's weather for [Item.day]; text = a [Weather] name. One per person per day (id is derived). */
    WEATHER,

    /** A wordless request: text = an [Ask] name. state 1 = the other one answered it. */
    ASK,

    /** A plain note. showAt > 0: a note for the morning, hidden from the other until then. state 1 = they put a heart on it. */
    NOTE,

    /** A worry hung face down. state 1 = the other one said they're ready, and it turned over. */
    WORRY,

    /** A confession in a sealed envelope. state 1 = opened, together. */
    ENVELOPE,

    /** A drawing on squared paper; extra = the strokes (see [app.fridge.domain.Strokes]). */
    DRAWING,

    /** A small thank-you, a round magnet on the freezer for the month it was given. */
    THANKS,

    /** A souvenir magnet from a trip together: text = the place, day = when, blob = a photo on the back. */
    TRIP,

    /** A wish in the hideout. done = the other one got it (never shown to the author). */
    WISH,
}

/**
 * Everything on the door is one of these. Both phones keep the same set and merge by [version]:
 * a Lamport clock, so the later edit wins wherever it was made, with [editor] breaking ties.
 * Nothing is ever really removed — [deleted] is a tombstone, so a removal travels to the other phone too.
 * [fresh] is the only field that never travels: it marks what came from the other phone and hasn't been looked at.
 */
@Entity(tableName = "items")
data class Item(
    @PrimaryKey val id: String,
    val kind: String,
    val author: String,
    val text: String = "",
    val extra: String = "",
    val done: Boolean = false,
    val doneBy: String = "",
    val state: Int = 0,
    /** Epoch day, for things that belong to a day (weather, chores done, trips). */
    val day: Long = 0,
    /** Epoch millis before which the other phone keeps this hidden. 0: shown at once. */
    val showAt: Long = 0,
    val createdAt: Long,
    val changedAt: Long,
    val version: Long = 0,
    val editor: String = "",
    val archived: Boolean = false,
    val deleted: Boolean = false,
    val blob: ByteArray? = null,
    @ColumnInfo(defaultValue = "0") val fresh: Boolean = false,
) {
    val type: Kind get() = runCatching { Kind.valueOf(kind) }.getOrDefault(Kind.NOTE)

    val date: LocalDate get() = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Is this edit newer than [other]? The merge rule, in one place. */
    fun beats(other: Item): Boolean = version > other.version || (version == other.version && editor > other.editor)

    // A ByteArray makes the generated equals compare by reference; compare what matters instead.
    override fun equals(other: Any?): Boolean =
        other is Item && id == other.id && version == other.version && editor == other.editor && fresh == other.fresh && deleted == other.deleted

    override fun hashCode(): Int = id.hashCode() * 31 + version.hashCode()
}

/** Which edit of an item a phone holds: enough to tell what the other one is missing. */
data class Stamp(val id: String, val version: Long, val editor: String)

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE deleted = 0 ORDER BY createdAt")
    fun live(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE deleted = 0 ORDER BY createdAt")
    suspend fun current(): List<Item>

    @Query("SELECT * FROM items")
    suspend fun everything(): List<Item>

    @Query("SELECT id, version, editor FROM items")
    suspend fun stamps(): List<Stamp>

    @Query("SELECT * FROM items WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<Item>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: String): Item?

    @Query("SELECT COALESCE(MAX(version), 0) FROM items")
    suspend fun clock(): Long

    @Upsert
    suspend fun put(items: List<Item>)

    @Query("UPDATE items SET fresh = 0 WHERE id = :id")
    suspend fun seen(id: String)

    @Query("UPDATE items SET fresh = 0 WHERE fresh = 1")
    suspend fun seenAll()
}

@Database(entities = [Item::class], version = 1, exportSchema = true)
abstract class FridgeDatabase : RoomDatabase() {
    abstract fun dao(): ItemDao

    companion object {
        /** [name] null: an in-memory door, for tests that play both phones. */
        fun create(context: Context, name: String? = "fridge.db"): FridgeDatabase =
            (if (name == null) Room.inMemoryDatabaseBuilder(context, FridgeDatabase::class.java) else Room.databaseBuilder(context, FridgeDatabase::class.java, name)).build()
    }
}
