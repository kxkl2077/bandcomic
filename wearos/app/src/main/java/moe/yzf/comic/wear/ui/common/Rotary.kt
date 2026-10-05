package moe.yzf.comic.wear.ui.common

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import kotlinx.coroutines.launch

/**
 * 把旋转表冠的刻度事件接到任意可滚动容器上。
 *
 * Wear Compose 的 Pager 自带旋转吸附，但普通的 scroll / LazyColumn 不会响应表冠，
 * 必须显式接 onRotaryScrollEvent，并先拿到焦点。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.rotaryScroll(state: ScrollableState): Modifier {
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    return this
        .focusRequester(focus)
        .focusable()
        .onRotaryScrollEvent { event ->
            scope.launch { state.scrollBy(event.verticalScrollPixels) }
            true
        }
}
