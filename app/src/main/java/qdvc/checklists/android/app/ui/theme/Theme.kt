package qdvc.checklists.android.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import qdvc.checklists.android.app.data.ThemeMode
import qdvc.checklists.android.app.data.ThemeSpec

private fun String.toColor(): Color {
    val hex = removePrefix("#")
    val v = hex.toLong(16)
    return when (hex.length) {
        6 -> Color(0xFF000000 or v)
        8 -> Color(v)
        else -> Color.Magenta
    }
}

private fun ThemeSpec.colorScheme(): ColorScheme {
    fun c(key: String, fallback: Color) = colors[key]?.toColor() ?: fallback
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val onBackground = c("onBackground", base.onBackground)
    return base.copy(
        background = c("background", base.background),
        surface = c("surface", base.surface),
        surfaceVariant = c("surfaceVariant", base.surfaceVariant),
        onBackground = onBackground,
        onSurface = onBackground, // point onSurface at onBackground (B5)
        onSurfaceVariant = c("onSurfaceVariant", base.onSurfaceVariant),
        outline = c("outline", base.outline),
        primary = c("primary", base.primary),
        onPrimary = c("onPrimary", base.onPrimary),
        secondary = c("secondary", base.secondary),
        onSecondary = c("onSecondary", base.onSecondary),
        error = c("error", base.error),
    )
}

/** Resolve whether the app is dark, from the chosen mode. */
@Composable
fun resolveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.AUTOMATIC -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun QDVCTheme(
    spec: ThemeSpec?,
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val scheme = spec?.colorScheme()
        ?: if (darkTheme) darkColorScheme() else lightColorScheme()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val barColor = scheme.surface.toArgb()
            @Suppress("DEPRECATION")
            window.statusBarColor = barColor
            @Suppress("DEPRECATION")
            window.navigationBarColor = barColor
            val controller = WindowInsetsControllerCompat(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        content = content,
    )
}
