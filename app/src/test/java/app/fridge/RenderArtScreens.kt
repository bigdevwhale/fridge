package app.fridge

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.fridge.data.DemoSeed
import app.fridge.data.FridgeSettings
import app.fridge.data.Kind
import app.fridge.domain.Board
import app.fridge.sync.Near
import app.fridge.ui.art.Art
import app.fridge.ui.door.DoorContent
import app.fridge.ui.door.DoorState
import app.fridge.ui.door.Sheet
import app.fridge.ui.inside.InsideContent
import app.fridge.ui.onboarding.Welcome
import app.fridge.ui.pair.PairScreen
import app.fridge.ui.settings.SettingsScreen
import app.fridge.ui.theme.FridgeTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Every main screen over the demo door, drawn to PNGs (run with `-Prender`), English and Russian: a look without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
abstract class ScreensBase {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    abstract val lang: String
    private val out by lazy { File(System.getProperty("render.out") ?: "build/art", "screens-$lang").apply { mkdirs() } }
    private val me = "a1a1a1a1a1a1a1a1"
    private val now = System.currentTimeMillis()

    @Before fun still() {
        Art.still = true
    }

    private fun settings(waiting: Boolean = false) = FridgeSettings(
        onboarded = true, deviceId = me, myName = if (lang == "ru") "Аня" else "Anna",
        partnerId = if (waiting) "" else DemoSeed.PARTNER, partnerName = if (waiting) "" else if (lang == "ru") "Боря" else "Ben",
        secret = ByteArray(32) { it.toByte() }, hum = true, lastMet = now - 3 * 3_600_000L,
    )

    private fun door(near: Near = Near(humming = true)): DoorState {
        val s = settings()
        val items = DemoSeed.items(me, DemoSeed.PARTNER, lang == "ru", now).map {
            // The other one's worry and drawing arrived since you last looked.
            if (it.author != me && (it.type == Kind.WORRY || it.type == Kind.DRAWING) && !it.archived) it.copy(fresh = true) else it
        }
        return DoorState(Board.of(items, s, now), near, s, now)
    }

    private fun id(kind: Kind) = door().board.papers.first { it.type == kind && it.state == 0 }.id

    private fun shot(name: String, content: @Composable () -> Unit) {
        ApplicationProvider.getApplicationContext<FridgeApp>()
        rule.mainClock.autoAdvance = false
        rule.setContent { FridgeTheme { content() } }
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            File(out, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun welcome() = shot("0-welcome") { Welcome() }
    @Test fun doorTop() = door().let { d -> shot("1-door") { DoorContent(d, {}, {}, {}) } }
    @Test fun doorNotes() = door().let { d -> shot("2-notes") { DoorContent(d, {}, {}, {}, startScroll = 2_350) } }
    @Test fun add() = door().let { d -> shot("3-add") { DoorContent(d, {}, {}, {}, startSheet = Sheet.Add, startScroll = 2_350) } }
    @Test fun worry() = door().let { d -> shot("4-worry") { DoorContent(d, {}, {}, {}, startSheet = Sheet.Open(id(Kind.WORRY)), startScroll = 2_350) } }
    @Test fun envelope() {
        val env = id(Kind.ENVELOPE)
        val d = door(Near(humming = true, together = true, theirHolds = setOf(env)))
        shot("5-envelope") { DoorContent(d, {}, {}, {}, startSheet = Sheet.Open(env), startScroll = 2_350) }
    }
    @Test fun hideout() = door().let { d -> shot("6-hideout") { DoorContent(d, {}, {}, {}, startSheet = Sheet.Wishes, startScroll = 2_350) } }
    @Test fun inside() = door().let { d -> shot("7-inside") { InsideContent(d, {}, {}) } }
    @Test fun pair() = shot("8-pair") { PairScreen(settings(waiting = true)) {} }
    @Test fun settingsScreen() = shot("9-settings") { SettingsScreen(settings(), {}, {}) }
}

class RenderArtScreensEn : ScreensBase() {
    override val lang = "en"
}

@Config(qualifiers = "ru-w411dp-h891dp-xxhdpi")
class RenderArtScreensRu : ScreensBase() {
    override val lang = "ru"
}
