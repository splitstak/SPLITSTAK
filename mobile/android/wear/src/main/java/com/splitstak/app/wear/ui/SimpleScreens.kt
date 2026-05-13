package com.splitstak.app.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.splitstak.app.wear.data.Snapshot

/** Cold-start state — phone hasn't published a snapshot yet. */
@Composable
fun SyncingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SPLITSTAK",
            style = MaterialTheme.typography.title2,
            color = SplitstakColors.Accent
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Open SPLITSTAK on your phone to sync today's workout.",
            style = MaterialTheme.typography.body2,
            color = SplitstakColors.TextDim,
            textAlign = TextAlign.Center
        )
    }
}

/** Rest day — no exercises to log; nothing to do here but acknowledge. */
@Composable
fun RestDayScreen(snapshot: Snapshot) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SPLITSTAK",
            style = MaterialTheme.typography.title3,
            color = SplitstakColors.TextDim
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "REST",
            style = MaterialTheme.typography.display1,
            color = SplitstakColors.Accent
        )
        snapshot.currentDayLabel?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.caption1,
                color = SplitstakColors.TextDim
            )
        }
    }
}

/** Day complete — all exercises done, prompt user to save. */
@Composable
fun DayCompleteScreen(snapshot: Snapshot, onSaveDay: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Big dot row to celebrate
        val dots = buildString {
            for ((i, _) in snapshot.exercises.withIndex()) {
                if (i > 0) append(' ')
                append('●')
            }
        }
        Text(
            text = dots,
            style = MaterialTheme.typography.title1,
            color = SplitstakColors.Accent
        )
        Spacer(Modifier.height(6.dp))
        snapshot.currentDayLabel?.let {
            Text(
                text = it.uppercase(),
                style = MaterialTheme.typography.title2,
                color = SplitstakColors.Text
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${snapshot.exercises.size} of ${snapshot.exercises.size} done",
            style = MaterialTheme.typography.caption1,
            color = SplitstakColors.TextDim
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onSaveDay,
            modifier = Modifier.size(width = 120.dp, height = 38.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = SplitstakColors.Accent,
                contentColor = SplitstakColors.Bg
            )
        ) {
            Text(
                text = "SAVE DAY",
                style = MaterialTheme.typography.button,
                color = SplitstakColors.Bg
            )
        }
    }
}
