package app.fridge

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fridge.data.FridgeSettings
import app.fridge.ui.Root
import app.fridge.ui.theme.FridgeTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A light kitchen: dark icons on clear bars.
        val style = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        setContent {
            val settings: FridgeSettings? by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            FridgeTheme {
                // Wait for settings so the welcome doesn't flash for someone already here.
                settings?.let { Root(it) }
            }
        }
    }
}
