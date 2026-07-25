package qdvc.checklists.android.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import qdvc.checklists.android.app.data.ThemeMode
import qdvc.checklists.android.app.data.ThemeSpec
import qdvc.checklists.android.app.ui.components.ListRow
import qdvc.checklists.android.app.ui.components.SlideNavHost

private enum class SettingsPage(val depth: Int) {
    ROOT(0), APPEARANCE(1), LIGHT_THEME(1), DARK_THEME(1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    lightThemeId: String?,
    darkThemeId: String?,
    lightThemes: List<ThemeSpec>,
    darkThemes: List<ThemeSpec>,
    onSetMode: (ThemeMode) -> Unit,
    onSetLight: (String) -> Unit,
    onSetDark: (String) -> Unit,
    onClose: () -> Unit,
) {
    var page by remember { mutableStateOf(SettingsPage.ROOT) }
    val goBack: () -> Unit = {
        if (page == SettingsPage.ROOT) onClose() else page = SettingsPage.ROOT
    }
    BackHandler(enabled = true) { goBack() }

    val title = when (page) {
        SettingsPage.ROOT -> "Settings"
        SettingsPage.APPEARANCE -> "Appearance"
        SettingsPage.LIGHT_THEME -> "Light mode style"
        SettingsPage.DARK_THEME -> "Dark mode style"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            )
        },
    ) { padding ->
        SlideNavHost(key = page.depth, modifier = Modifier.padding(padding).fillMaxSize()) {
            when (page) {
                SettingsPage.ROOT -> RootPage(
                    themeMode = themeMode,
                    lightName = lightThemes.firstOrNull { it.id == lightThemeId }?.name
                        ?: lightThemes.firstOrNull()?.name ?: "Default",
                    darkName = darkThemes.firstOrNull { it.id == darkThemeId }?.name
                        ?: darkThemes.firstOrNull()?.name ?: "Default",
                    onOpen = { page = it },
                )
                SettingsPage.APPEARANCE -> AppearancePage(themeMode, onSetMode)
                SettingsPage.LIGHT_THEME -> ThemeChoicePage(lightThemes, lightThemeId, onSetLight)
                SettingsPage.DARK_THEME -> ThemeChoicePage(darkThemes, darkThemeId, onSetDark)
            }
        }
    }
}

@Composable
private fun RootPage(
    themeMode: ThemeMode,
    lightName: String,
    darkName: String,
    onOpen: (SettingsPage) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListRow(
                title = "Appearance",
                subtitle = when (themeMode) {
                    ThemeMode.AUTOMATIC -> "Automatic"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                },
                leadingIcon = Icons.Filled.Palette,
                onClick = { onOpen(SettingsPage.APPEARANCE) },
            )
        }
        item {
            ListRow(
                title = "Light mode style",
                subtitle = lightName,
                leadingIcon = Icons.Filled.LightMode,
                onClick = { onOpen(SettingsPage.LIGHT_THEME) },
            )
        }
        item {
            ListRow(
                title = "Dark mode style",
                subtitle = darkName,
                leadingIcon = Icons.Filled.DarkMode,
                onClick = { onOpen(SettingsPage.DARK_THEME) },
            )
        }
    }
}

@Composable
private fun AppearancePage(mode: ThemeMode, onSet: (ThemeMode) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(ThemeMode.entries) { m ->
            ChoiceRow(
                label = when (m) {
                    ThemeMode.AUTOMATIC -> "Automatic"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                },
                selected = m == mode,
                onClick = { onSet(m) },
            )
        }
    }
}

@Composable
private fun ThemeChoicePage(
    themes: List<ThemeSpec>,
    selectedId: String?,
    onSet: (String) -> Unit,
) {
    val effectiveSelected = selectedId ?: themes.firstOrNull()?.id
    LazyColumn(Modifier.fillMaxSize()) {
        items(themes) { t ->
            ChoiceRow(
                label = t.name,
                selected = t.id == effectiveSelected,
                onClick = { onSet(t.id) },
            )
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
