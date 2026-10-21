package app.fridge.sync

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.fridge.R
import app.fridge.container
import app.fridge.localized
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The fridge humming with the app closed: a quiet foreground service that keeps listening for the
 * other phone, so whatever they hung on the door is already here when you look. Only when the
 * person turned it on, and only while paired.
 */
class HumService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val c = container
        c.news.channels()
        val l = localized()
        val n = NotificationCompat.Builder(this, News.HUM)
            .setSmallIcon(R.drawable.ic_stat_magnet)
            .setContentTitle(l.getString(R.string.hum_title))
            .setContentText(l.getString(R.string.hum_text))
            .setContentIntent(c.news.open())
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val started = runCatching {
            ServiceCompat.startForeground(this, News.HUM_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        c.hum.start("service")
        return START_STICKY
    }

    override fun onDestroy() {
        container.hum.stop("service")
        super.onDestroy()
    }

    companion object {
        fun intent(context: Context) = Intent(context, HumService::class.java)
    }
}

/** After a restart the fridge goes back to humming, if it was. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val c = context.container
        val done = goAsync()
        c.scope.launch {
            val s = c.settings.settings.first()
            if (s.paired && s.hum) runCatching { ContextCompat.startForegroundService(context, HumService.intent(context)) }
            done.finish()
        }
    }
}
