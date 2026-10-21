package app.fridge.sync

import android.app.Notification
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The fridge humming with the app closed: a quiet foreground service that keeps listening for the
 * other phone, so whatever they hung on the door is already here when you look. Only when the
 * person turned it on, only while paired — and only at home: on a network where the two phones
 * have met before. Anywhere else it sleeps (until the two have met somewhere, any Wi-Fi will do).
 */
class HumService : Service() {
    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val c = container
        c.news.channels()
        val started = runCatching {
            ServiceCompat.startForeground(this, News.HUM_ID, note(home = true), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (scope == null) {
            val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            scope = s
            s.launch {
                c.homeNet.here.combine(c.settings.settings.map { it.homes }) { here, homes ->
                    here != null && (homes.isEmpty() || here in homes)
                }.distinctUntilChanged().collect { home ->
                    if (home) c.hum.start("service") else c.hum.stop("service")
                    // Starting foreground again with a new notification just updates the one shown.
                    runCatching { ServiceCompat.startForeground(this@HumService, News.HUM_ID, note(home), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) }
                }
            }
        }
        return START_STICKY
    }

    private fun note(home: Boolean): Notification {
        val l = localized()
        return NotificationCompat.Builder(this, News.HUM)
            .setSmallIcon(R.drawable.ic_stat_magnet)
            .setContentTitle(l.getString(if (home) R.string.hum_title else R.string.hum_title_away))
            .setContentText(l.getString(if (home) R.string.hum_text else R.string.hum_text_away))
            .setContentIntent(container.news.open())
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
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
