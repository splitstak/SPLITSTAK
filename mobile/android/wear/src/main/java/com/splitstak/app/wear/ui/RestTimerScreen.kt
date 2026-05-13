package com.splitstak.app.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.splitstak.app.wear.Haptic
import com.splitstak.app.wear.data.Snapshot
import kotlinx.coroutines.delay

/**
 * Rest-timer screen. The orange bezel is a CircularProgressIndicator
 * whose progress shrinks from 1f → 0f as the timer ticks down. When it
 * hits 0, we show "Rest complete" briefly, fire the finish haptic, then
 * auto-dismiss back to the workout screen so the user can start their
 * next set without tapping anything.
 *
 * Haptics:
 *   - start:    short tick when the timer first appears
 *   - 10 sec:   short tick warning
 *   - finish:   long buzz on hit-zero
 */
@Composable
fun RestTimerScreen(snapshot: Snapshot, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val endsAt = snapshot.restEndsAt ?: return
    val durationMs = (snapshot.timerDuration.coerceAtLeast(1) * 1000L)

    // Watch the wall-clock; recompute every 100 ms while the timer runs.
    var nowMs by remember(endsAt) { mutableLongStateOf(System.currentTimeMillis()) }
    var startFired by remember(endsAt) { mutableStateOf(false) }
    var warningFired by remember(endsAt) { mutableStateOf(false) }
    var finishFired by remember(endsAt) { mutableStateOf(false) }

    LaunchedEffect(endsAt) {
        if (!startFired) {
            startFired = true
            Haptic.short(context)
        }
        while (true) {
            nowMs = System.currentTimeMillis()
            val remaining = endsAt - nowMs
            if (!warningFired && remaining in 1L..10_000L) {
                warningFired = true
                Haptic.short(context)
            }
            if (!finishFired && remaining <= 0L) {
                finishFired = true
                Haptic.long(context)
                // Hold "Rest complete" on screen briefly, then auto-
                // return to the workout. The phone's AlarmManager will
                // also fire applyExpireRest around the same time; either
                // path clears restEndsAt and gets us back to the active
                // exercise screen.
                delay(700L)
                onDismiss()
                break
            }
            delay(100L)
        }
    }

    val remainingMs = (endsAt - nowMs).coerceAtLeast(0L)
    val progress = (remainingMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        // Shrinking bezel ring. startAngle = 270° puts the seam at 12 o'clock;
        // endAngle = 270° makes it a complete circle that shrinks counter-
        // clockwise as `progress` falls toward 0.
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            startAngle = 270f,
            endAngle = 270f,
            indicatorColor = SplitstakColors.Accent,
            trackColor = Color.Transparent,
            strokeWidth = 8.dp
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val warning = remainingMs in 1L..10_000L
            Text(
                text = if (remainingMs > 0) "REST" else "DONE",
                style = MaterialTheme.typography.title3,
                color = if (remainingMs > 0) SplitstakColors.Accent else SplitstakColors.TextDim
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (remainingMs > 0) formatMmSs(remainingMs) else "Rest complete",
                style = if (remainingMs > 0) MaterialTheme.typography.display1
                        else MaterialTheme.typography.display3,
                color = if (warning) SplitstakColors.Accent else SplitstakColors.Text
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.size(width = 90.dp, height = 32.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color.Transparent,
                    contentColor = SplitstakColors.Accent
                )
            ) {
                Text(
                    text = "DISMISS",
                    style = MaterialTheme.typography.button,
                    color = SplitstakColors.Accent
                )
            }
        }
    }
}

private fun formatMmSs(ms: Long): String {
    val totalSec = ms / 1000L
    val m = totalSec / 60L
    val s = totalSec % 60L
    return "%02d:%02d".format(m, s)
}
