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

/**
 * Generic variant: animates between arbitrary [T] states, deriving slide
 * direction from [depthOf]. The body is rendered for the *animating* state,
 * so outgoing and incoming frames show the correct (different) content.
 */
@Composable
fun <T> SlideNavHostFor(
    target: T,
    depthOf: (T) -> Int,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            val deeper = depthOf(targetState) > depthOf(initialState)
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
