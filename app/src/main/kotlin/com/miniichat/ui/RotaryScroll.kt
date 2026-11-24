package com.miniichat.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import kotlinx.coroutines.launch

/** Lets the watch crown scroll the same content as a finger swipe. */
@Composable
internal fun Modifier.watchRotaryScroll(state: ScrollableState, active: Boolean = true): Modifier {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(active) { if (active) focusRequester.requestFocus() }
    return this
        .onRotaryScrollEvent { event ->
            if (!active) false else {
                scope.launch { state.scrollBy(event.verticalScrollPixels) }
                true
            }
        }
        .focusRequester(focusRequester)
        .focusable()
}
