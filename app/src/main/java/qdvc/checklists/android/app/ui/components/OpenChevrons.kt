package qdvc.checklists.android.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

/**
 * The "currently open" indicator: two chevrons in the accent colour, stretched
 * to (slightly beyond) the full height of the row's text block. When [visible]
 * is false the same footprint is reserved as blank space so text stays aligned
 * across rows that do and don't show the indicator.
 *
 * [pointLeft] flips the direction: tab 4 (Jump) points left toward the checklist
 * tab that sits to its left; tabs 1 and 2 point right.
 *
 * The parent Row must use `Modifier.height(IntrinsicSize.Min)` so that
 * `fillMaxHeight` here resolves to the height of the tallest sibling (the text
 * column), making the chevrons as tall as all the lines of text combined.
 */
@Composable
fun OpenChevrons(
    visible: Boolean,
    modifier: Modifier = Modifier,
    pointLeft: Boolean = false,
) {
    Box(
        modifier
            .width(28.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            Icon(
                imageVector = if (pointLeft) Icons.Filled.KeyboardDoubleArrowLeft
                else Icons.Filled.KeyboardDoubleArrowRight,
                contentDescription = "Currently open",
                tint = MaterialTheme.colorScheme.primary,
                // Fill the row height, then stretch taller vertically only
                // (scaleY) so the chevrons grow taller without getting wider.
                modifier = Modifier
                    .fillMaxHeight()
                    .scale(scaleX = 1f, scaleY = 1.3f),
            )
        }
    }
}
