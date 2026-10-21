package app.fridge.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.fridge.MainActivity
import app.fridge.R
import app.fridge.data.FridgeSettings
import app.fridge.data.Item
import app.fridge.data.Kind
import app.fridge.localized

/**
 * The quiet notification that the other one has put something on the door while the app was
 * closed — "a note · an envelope · milk, bread". Only ever from the background hum, and one at a time.
 */
class News(private val context: Context) {
    private val nm = NotificationManagerCompat.from(context)
    private val pending = mutableListOf<Item>()

    fun channels() {
        val c = context.localized()
        val m = context.getSystemService(NotificationManager::class.java)
        m.createNotificationChannel(NotificationChannel(HUM, c.getString(R.string.channel_hum), NotificationManager.IMPORTANCE_MIN))
        m.createNotificationChannel(NotificationChannel(NEWS, c.getString(R.string.channel_news), NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun open(): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @Synchronized
    fun show(items: List<Item>, s: FridgeSettings) {
        val now = System.currentTimeMillis()
        pending += items.filter { it.fresh && it.showAt <= now }
        val line = summary(pending) ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        channels()
        val c = context.localized()
        val n = NotificationCompat.Builder(context, NEWS)
            .setSmallIcon(R.drawable.ic_stat_magnet)
            .setContentTitle(c.getString(R.string.news_title, s.partnerName.ifBlank { c.getString(R.string.them) }))
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line))
            .setContentIntent(open())
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NEWS_ID, n) }
    }

    @Synchronized
    fun clear() {
        pending.clear()
        runCatching { nm.cancel(NEWS_ID) }
    }

    /** What arrived, in a line: kinds by name, groceries by their words. */
    fun summary(items: List<Item>): String? {
        val c = context.localized()
        val groceries = items.filter { it.type == Kind.GROCERY && !it.done }.map { it.text }
        val parts = items.filter { it.type != Kind.GROCERY }.mapNotNull { i ->
            when (i.type) {
                Kind.NOTE -> R.string.news_note
                Kind.WORRY -> R.string.news_worry
                Kind.ENVELOPE -> R.string.news_envelope
                Kind.DRAWING -> R.string.news_drawing
                Kind.THANKS -> R.string.news_thanks
                Kind.TRIP -> R.string.news_trip
                Kind.ASK -> R.string.news_ask
                Kind.CHORE -> R.string.news_chore
                else -> null
            }
        }.distinct().map { c.getString(it) }.toMutableList()
        if (groceries.isNotEmpty()) parts += c.getString(R.string.news_groceries, groceries.distinct().take(5).joinToString(", "))
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    companion object {
        const val HUM = "hum"
        const val NEWS = "news"
        const val HUM_ID = 1
        private const val NEWS_ID = 2
    }
}
