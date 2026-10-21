package app.fridge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.fridge.data.FridgeSettings
import app.fridge.ui.door.DoorScreen
import app.fridge.ui.inside.InsideScreen
import app.fridge.ui.onboarding.Welcome
import app.fridge.ui.pair.PairScreen
import app.fridge.ui.settings.SettingsScreen

/** The door is home; everything else is a step away from it and straight back. */
private enum class Place { DOOR, INSIDE, SETTINGS, PAIR }

@Composable
fun Root(settings: FridgeSettings) {
    if (!settings.onboarded) {
        Welcome()
        return
    }
    var place by rememberSaveable { mutableStateOf(Place.DOOR) }
    BackHandler(enabled = place != Place.DOOR) { place = Place.DOOR }
    Crossfade(targetState = place, label = "place") { here ->
        when (here) {
            Place.DOOR -> DoorScreen(
                settings,
                onInside = { place = Place.INSIDE },
                onSettings = { place = Place.SETTINGS },
                onPair = { place = Place.PAIR },
            )
            Place.INSIDE -> InsideScreen(settings, onBack = { place = Place.DOOR }, onPair = { place = Place.PAIR })
            Place.SETTINGS -> SettingsScreen(settings, onBack = { place = Place.DOOR }, onPair = { place = Place.PAIR })
            Place.PAIR -> PairScreen(settings, onBack = { place = Place.DOOR })
        }
    }
}
