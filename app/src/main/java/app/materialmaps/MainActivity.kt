package app.materialmaps

import android.graphics.Color
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.materialmaps.ui.theme.MaterialMapsTheme

class MainActivity : ComponentActivity() {
    /** A location link another app opened us with, until the map has shown it. */
    private val incoming = mutableStateOf<Uri?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { incoming.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) incoming.value = intent?.data
        setContent {
            var settings by remember { mutableStateOf(Settings.load(this)) }
            distanceUnits = settings.units
            val dark = when (settings.appearance) {
                Appearance.System -> isSystemInDarkTheme()
                Appearance.Light -> false
                Appearance.Dark -> true
            }
            // Status and navigation bar icons follow the app's appearance, not just the system's.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            MaterialMapsTheme(darkTheme = dark) {
                MapScreen(
                    settings = settings,
                    onSettingsChange = {
                        settings = it
                        it.save(this)
                    },
                    darkMap = when (settings.mapColors) {
                        MapColors.MatchApp -> dark
                        MapColors.Light -> false
                        MapColors.Dark -> true
                    },
                    incoming = incoming.value,
                    onIncomingHandled = { incoming.value = null },
                )
            }
        }
    }
}
