package app.fridge

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.fridge.data.DemoSeed
import app.fridge.data.FridgeDatabase
import app.fridge.data.SettingsStore
import app.fridge.domain.Door
import app.fridge.sync.HomeNet
import app.fridge.sync.Hum
import app.fridge.sync.HumService
import app.fridge.sync.LanFinder
import app.fridge.sync.News
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppContainer(val context: Context, lan: Boolean = true) {
    val db = FridgeDatabase.create(context)
    val settings = SettingsStore(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val hum = Hum(settings, db.dao(), scope, if (lan) LanFinder(context) else null)
    val door = Door(db.dao(), settings).also { it.onChange = hum::push }
    val news = News(context)
    val homeNet = HomeNet(context)

    /** True while the app is on screen: news from the other phone is then on the door, not in a notification. */
    @Volatile var visible = false

    /** Fire-and-forget so a write survives the screen being closed right after tapping. */
    fun write(block: suspend AppContainer.() -> Unit) {
        scope.launch { block() }
    }
}

class FridgeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val c = AppContainer(this)
        container = c
        c.hum.onMet = { c.homeNet.here.value?.let { home -> c.scope.launch { c.settings.rememberHome(home) } } }
        c.hum.onNews = { items -> if (!c.visible) c.scope.launch { c.news.show(items, c.settings.now()) } }
        c.scope.launch {
            c.settings.deviceId()
            DemoSeed.seedIfEmpty(c, ru)
        }
        runCatching { ProcessLifecycleOwner.get() }.getOrNull()?.lifecycle?.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                c.visible = true
                c.news.clear()
                c.scope.launch { c.hum.start("app") }
            }

            override fun onStop(owner: LifecycleOwner) {
                c.visible = false
                c.scope.launch { c.hum.stop("app") }
            }
        })
        // Pairing, unpairing and the background switch all change what the hum should be doing.
        c.scope.launch {
            c.settings.settings.map { Triple(it.secret?.contentHashCode(), it.paired, it.hum) }.distinctUntilChanged().collect { (_, paired, hum) ->
                c.hum.refresh()
                if (paired && hum) {
                    runCatching { ContextCompat.startForegroundService(this@FridgeApp, HumService.intent(this@FridgeApp)) }
                } else {
                    stopService(HumService.intent(this@FridgeApp))
                }
            }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as FridgeApp).container

/** Resources in the in-app language, if the app speaks one on this Android. */
fun Context.localized(): Context {
    val tags = AppCompatDelegate.getApplicationLocales()
    if (tags.isEmpty) return this
    val config = Configuration(resources.configuration)
    config.setLocale(tags[0])
    return createConfigurationContext(config)
}

val Context.ru: Boolean get() = resources.configuration.locales[0].language == "ru"
