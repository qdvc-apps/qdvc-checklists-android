package qdvc.checklists.android.app.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shown while the app reads the workspaces from disk into its local projection.
 *
 * This is the one moment the app deliberately blocks: until the scan finishes
 * there is genuinely nothing to show. So rather than an opaque spinner, the scan
 * narrates itself — the transcript is the point, and it doubles as the place a
 * problem (an unreadable folder, a missing `checklists/`) becomes visible.
 */
@Composable
fun SplashScreen(lines: List<String>) {
    val listState = rememberLazyListState()

    // Follow the tail as new lines arrive.
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Loading",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 14.dp),
            )
        }

        Text(
            text = "Reading your workspace files",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(12.dp)
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(lines) { line ->
                    TranscriptLine(line = line, isLast = line === lines.lastOrNull())
                }
            }
        }
    }
}

/**
 * One transcript line. Indentation in the source line marks a nested step (a
 * folder within a workspace, a file within the logs), so it is rendered dimmer
 * and without a marker — the hierarchy carries real information about what the
 * scan is doing, rather than being decoration.
 */
@Composable
private fun TranscriptLine(line: String, isLast: Boolean) {
    val nested = line.startsWith("  ")
    val text = line.trimStart()
    val colors = MaterialTheme.colorScheme
    val color = when {
        isLast -> colors.primary
        nested -> colors.onSurfaceVariant.copy(alpha = 0.65f)
        else -> colors.onSurfaceVariant
    }
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = if (nested) "  " else "> ",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = colors.onSurfaceVariant.copy(alpha = 0.45f),
        )
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = color,
            fontWeight = if (isLast) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
