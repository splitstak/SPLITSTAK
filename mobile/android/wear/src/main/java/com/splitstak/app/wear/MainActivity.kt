package com.splitstak.app.wear

import android.os.Bundle
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
