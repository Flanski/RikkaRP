package me.rerere.rikkahub.ui.hooks

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import me.rerere.rikkahub.ui.context.LocalSharedTransitionScope
import me.rerere.rikkahub.ui.theme.sharp.SharpMotion

@Composable
fun Modifier.heroAnimation(
    key: Any,
): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current
    return with(sharedTransitionScope) {
        this@heroAnimation.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedVisibilityScope,
            // RikkaRP: 共享元素变形也统一 400ms。
            // 库默认是 spring(StiffnessMediumLow)，停止时刻不可精确指定。
            boundsTransform = BoundsTransform { _, _ -> SharpMotion.spec() },
        )
    }
}

/**
 * 容器变换：同一个 key 的两个容器（比如列表项和它打开的整页）在页面切换时互相展开、收回，内容交叉淡入淡出。
 * 两边都按宽度缩放、顶部对齐，不会在动画的每一帧重新测量整页。
 */
@Composable
fun Modifier.heroContainer(
    key: Any,
    shape: Shape,
): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current
    return with(sharedTransitionScope) {
        this@heroContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedVisibilityScope,
            // RikkaRP: 同上，容器变换与内容淡入淡出统一 400ms。
            enter = fadeIn(SharpMotion.spec()),
            exit = fadeOut(SharpMotion.spec()),
            boundsTransform = BoundsTransform { _, _ -> SharpMotion.spec() },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}
