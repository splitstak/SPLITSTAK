package com.splitstak.app.wear

import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.splitstak.app.wear.data.Snapshot
import com.splitstak.app.wear.data.WatchState
import com.splitstak.app.wear.ui.ActiveExerciseScreen
import com.splitstak.app.wear.ui.DayCompleteScreen
import com.splitstak.app.wear.ui.RestDayScreen
import com.splitstak.app.wear.ui.RestTimerScreen
import com.splitstak.app.wear.ui.SplitstakColors
import com.splitstak.app.wear.ui.SplitstakTheme
import com.splitstak.app.wear.ui.SyncingScreen

/**
 * Single Activity, single Composable tree. State-based routing decides
 * which screen to render — no NavHost needed because every transition
 * is driven by the snapshot (rest started, day complete, etc.) rather
 * than by user navigation.
 *
 * Also intercepts rotary-encoder (crown) MotionEvents and republishes
 * them through [RotaryDispatcher]. Compose's focus-based rotary handling
 * was unreliable here because nested tap targets kept poaching focus;
 * routing through the Activity sidesteps that entirely.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchState.hydrate(applicationContext)
        setContent {
            SplitstakTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SplitstakColors.Bg)
                ) {
                    Router()
                }
            }
        }
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent?): Boolean {
        if (ev != null
            && ev.action == MotionEvent.ACTION_SCROLL
            && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            // Wear OS reports rotary motion on AXIS_SCROLL as floating-point
            // detent fractions; multiply by the system's scaled scroll
            // factor to get pixels. Negate so "crown up / away from user"
            // maps to "value increases" — the natural mental model.
            val scaledScroll = ev.getAxisValue(MotionEvent.AXIS_SCROLL) *
                ViewConfiguration.get(this).scaledVerticalScrollFactor
            RotaryDispatcher.emit(-scaledScroll)
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }
}

@Composable
private fun Router() {
    val snapshot by WatchState.snapshotFlow.collectAsState()
    val context = LocalContext.current

    when {
        snapshot == null -> SyncingScreen()
        isRestActive(snapshot) -> RestTimerScreen(
            snapshot = snapshot!!,
            onDismiss = {
                com.splitstak.app.wear.data.ActionSender.dismissRest(context)
            }
        )
        snapshot!!.dayAllComplete -> DayCompleteScreen(
            snapshot = snapshot!!,
            onSaveDay = {
                com.splitstak.app.wear.data.ActionSender.finishDay(context)
            }
        )
        snapshot!!.isRestDay -> RestDayScreen(snapshot = snapshot!!)
        else -> ActiveExerciseScreen(snapshot = snapshot!!)
    }
}

private fun isRestActive(s: Snapshot?): Boolean {
    val endsAt = s?.restEndsAt ?: return false
    return endsAt > System.currentTimeMillis()
}
