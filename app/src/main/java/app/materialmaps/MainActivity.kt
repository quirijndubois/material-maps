package app.materialmaps

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
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

    /** Whether the app is shrunk to a picture-in-picture window. */
    private val inPip = mutableStateOf(false)

    /** Going to another app while navigating keeps the map going in a small window. */
    private var pipWanted = false

    private fun pipParams() = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(3, 4))
        .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(pipWanted) }
        .build()

    private fun setPipWanted(wanted: Boolean) {
        if (wanted == pipWanted) return
        pipWanted = wanted
        setPictureInPictureParams(pipParams())
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // From Android 12 the system does this itself, more smoothly.
        if (pipWanted && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) enterPictureInPictureMode(pipParams())
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

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
                    pip = inPip.value,
                    onNavigatingChange = ::setPipWanted,
                )
            }
        }
    }
}
