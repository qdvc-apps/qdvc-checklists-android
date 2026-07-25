package qdvc.checklists.android.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The "currently open" indicator: two right-pointing chevrons in the accent
 * colour, stretched to the full height of the row's text block. When [visible]
 * is false the same footprint is reserved as blank space so text stays aligned
 * across rows that do and don't show the indicator.
 *
 * The parent Row must use `Modifier.height(IntrinsicSize.Min)` so that
 * `fillMaxHeight` here resolves to the height of the tallest sibling (the text
 * column), making the chevrons as tall as all the lines of text combined.
 */
@Composable
fun OpenChevrons(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .width(28.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            Icon(
                imageVector = Icons.Filled.KeyboardDoubleArrowRight,
                contentDescription = "Currently open",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxHeight(),
            )
        }
    }
}
