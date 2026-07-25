package qdvc.checklists.android.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import qdvc.checklists.android.app.Tab

@Composable
fun BottomBar(
    current: Tab,
    itemOpen: Boolean,
    onSelect: (Tab) -> Unit,
) {
    NavigationBar(tonalElevation = 0.dp) {
        NavItem(current, Tab.HOME, "Home", Icons.Filled.Home, true, onSelect)
        NavItem(current, Tab.VIEW, "Checklist", Icons.Filled.Checklist, itemOpen, onSelect)
        NavItem(current, Tab.INFO, "Info", Icons.Filled.Info, itemOpen, onSelect)
        NavItem(current, Tab.SWITCHER, "Open", Icons.AutoMirrored.Filled.List, true, onSelect)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    current: Tab,
    tab: Tab,
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onSelect: (Tab) -> Unit,
) {
    NavigationBarItem(
        selected = current == tab,
        enabled = enabled,
        onClick = { onSelect(tab) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}
