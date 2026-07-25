package qdvc.checklists.android.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The single horizontal-slide vocabulary reused for every hierarchical
 * transition (B7). The animation is keyed by navigation depth so direction is
 * derived (target > initial => going deeper). The `SizeTransform { snap() }`
 * prevents the diagonal-drift failure mode caused by an animated height.
 *
 * [key] must be an Int encoding the current depth. [content] renders the body
 * for a given depth-key.
 */
@Composable
fun SlideNavHost(
    key: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    AnimatedContent(
        targetState = key,
        modifier = modifier,
        transitionSpec = {
            val deeper = targetState > initialState
            val transform: ContentTransform = if (deeper) {
                (slideInHorizontally(tween(280)) { it } + fadeIn()) togetherWith
                    (slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut())
            } else {
                (slideInHorizontally(tween(280)) { -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally(tween(280)) { it } + fadeOut())
            }
            transform.using(SizeTransform(clip = false) { _, _ -> snap() })
        },
        label = "slide-nav",
        content = { content(it) },
    )
}
