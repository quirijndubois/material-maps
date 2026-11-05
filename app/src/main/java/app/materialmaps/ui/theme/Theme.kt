package app.materialmaps.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette for Android 8–11; Android 12+ uses the wallpaper (Material You) colors.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6B50),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6F2D0),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4C6358),
    secondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFF3F6375),
    tertiaryContainer = Color(0xFFC3E8FD),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AD6B4),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFA6F2D0),
    secondary = Color(0xFFB3CCBF),
    secondaryContainer = Color(0xFF354B40),
    tertiary = Color(0xFFA7CCE0),
    tertiaryContainer = Color(0xFF264B5C),
)

@Composable
fun MaterialMapsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
