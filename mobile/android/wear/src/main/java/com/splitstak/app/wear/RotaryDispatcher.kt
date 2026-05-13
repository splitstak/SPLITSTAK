package com.splitstak.app.wear

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Global pipe for rotary-encoder (crown) scroll events.
 *
 * Wear OS delivers rotary events through MotionEvent (SOURCE_ROTARY_ENCODER,
 * ACTION_SCROLL, AXIS_SCROLL) routed to the focused View. Compose's
 * Modifier.onRotaryScrollEvent in turn requires a properly-focused
 * Composable to receive them — which proved fragile here because child
 * tap targets keep stealing Compose focus.
 *
 * The Activity intercepts MotionEvents in [MainActivity.dispatchGenericMotionEvent]
 * and pushes the scaled delta through this SharedFlow. Composables collect
 * from [events] inside a LaunchedEffect, which means rotary input keeps
 * working regardless of which Compose node currently owns focus.
 */
object RotaryDispatcher {
    /**
     * Buffered so a flick of the crown that fires several events in the
     * same frame doesn't drop deltas before the collector wakes up.
     */
    private val _events = MutableSharedFlow<Float>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<Float> = _events.asSharedFlow()

    fun emit(deltaPx: Float) {
        _events.tryEmit(deltaPx)
    }
}
