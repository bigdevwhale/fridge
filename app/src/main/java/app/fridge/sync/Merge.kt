package app.fridge.sync

import app.fridge.data.Item
import app.fridge.data.ItemDao
import app.fridge.data.Stamp

object Merge {
    /**
     * Takes in what came from the other phone, keeping whichever edit of each item is newer.
     * Returns the items that changed here, so the caller can tell what was new.
     */
    suspend fun take(dao: ItemDao, incoming: List<Item>, me: String): List<Item> {
        if (incoming.isEmpty()) return emptyList()
        val have = dao.byIds(incoming.map { it.id }).associateBy { it.id }
        val won = incoming.filter { inc -> have[inc.id]?.let { inc.beats(it) } ?: true }.map { inc ->
            val old = have[inc.id]
            // Fresh: newly arrived from the other one — or already fresh and still unread.
            inc.copy(fresh = (old == null && inc.author != me && !inc.deleted) || (old?.fresh == true && !inc.deleted))
        }
        if (won.isNotEmpty()) dao.put(won)
        return won
    }

    /** Of [mine], what the other phone (holding [theirs]) lacks or has older. */
    fun missing(mine: List<Item>, theirs: Map<String, Stamp>): List<Item> = mine.filter { i ->
        val t = theirs[i.id] ?: return@filter true
        t.version < i.version || (t.version == i.version && t.editor < i.editor)
    }
}
