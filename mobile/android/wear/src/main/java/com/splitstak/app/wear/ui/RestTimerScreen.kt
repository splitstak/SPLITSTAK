package com.splitstak.app.wear.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import com.splitstak.app.wear.data.Snapshot
import kotlinx.coroutines.delay

/**
 * Rest-timer screen. The orange bezel is a CircularProgressIndicator whose
 * progress shrinks from 1f → 0f as the timer ticks down. When it hits 0,
 * the indicator is invisible and we show the "Rest complete" finish state
 * until the snapshot's restEndsAt clears (which happens automatically via
 * the phone's AlarmManager-driven applyExpireRest after ~250 ms).
 *
 * Haptics: a single short tick at 10 sec remaining and a longer buzz at
 * 0 sec. The phone separately posts lock-screen notifications via its
 * own AlarmManager; both surfaces stay synchronised because they share
 * the same restEndsAt timestamp in the snapshot.
 */
@Composable
fun RestTimerScreen(snapshot: Snapshot, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val endsAt = snapshot.restEndsAt ?: return
    val durationMs = (snapshot.timerDuration.coerceAtLeast(1) * 1000L)

    // Watch the wall-clock; recompute every 100 ms while the timer runs.
    var nowMs by remember(endsAt) { mutableLongStateOf(System.currentTimeMillis()) }
    var warningFired by remember(endsAt) { mutableStateOf(false) }
    var finishFired by remember(endsAt) { mutableStateOf(false) }

    LaunchedEffect(endsAt) {
        while (true) {
            nowMs = System.currentTimeMillis()
            val remaining = endsAt - nowMs
            if (!warningFired && remaining in 1L..10_000L) {
                warningFired = true
                hapticTick(context, longBuzz = false)
            }
            if (!finishFired && remaining <= 0L) {
                finishFired = true
                hapticTick(context, longBuzz = true)
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

private fun hapticTick(context: Context, longBuzz: Boolean) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        val durationMs = if (longBuzz) 220L else 80L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    } catch (_: Exception) {
        // Vibrator can be null on some emulators; no-op fallback.
    }
}

