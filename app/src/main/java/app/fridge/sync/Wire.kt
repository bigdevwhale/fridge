package app.fridge.sync

import android.util.Base64
import app.fridge.data.Item
import org.json.JSONArray
import org.json.JSONObject

/** Items as JSON, the way they travel. [Item.fresh] stays home: it belongs to each phone's own eyes. */
object Wire {
    fun item(i: Item): JSONObject = JSONObject().apply {
        put("id", i.id)
        put("k", i.kind)
        put("a", i.author)
        if (i.text.isNotEmpty()) put("t", i.text)
        if (i.extra.isNotEmpty()) put("x", i.extra)
        if (i.done) put("d", true)
        if (i.doneBy.isNotEmpty()) put("db", i.doneBy)
        if (i.state != 0) put("s", i.state)
        if (i.day != 0L) put("day", i.day)
        if (i.showAt != 0L) put("at", i.showAt)
        put("c", i.createdAt)
        put("ch", i.changedAt)
        put("v", i.version)
        put("e", i.editor)
        if (i.archived) put("ar", true)
        if (i.deleted) put("del", true)
        i.blob?.let { put("b", Base64.encodeToString(it, Base64.NO_WRAP)) }
    }

    fun item(o: JSONObject): Item = Item(
        id = o.getString("id"),
        kind = o.getString("k"),
        author = o.getString("a"),
        text = o.optString("t"),
        extra = o.optString("x"),
        done = o.optBoolean("d"),
        doneBy = o.optString("db"),
        state = o.optInt("s"),
        day = o.optLong("day"),
        showAt = o.optLong("at"),
        createdAt = o.getLong("c"),
        changedAt = o.getLong("ch"),
        version = o.getLong("v"),
        editor = o.getString("e"),
        archived = o.optBoolean("ar"),
        deleted = o.optBoolean("del"),
        blob = o.optString("b").takeIf { it.isNotEmpty() }?.let { Base64.decode(it, Base64.NO_WRAP) },
    )

    fun items(list: List<Item>): JSONArray = JSONArray().apply { list.forEach { put(item(it)) } }

    fun items(a: JSONArray): List<Item> = (0 until a.length()).mapNotNull { runCatching { item(a.getJSONObject(it)) }.getOrNull() }
}
