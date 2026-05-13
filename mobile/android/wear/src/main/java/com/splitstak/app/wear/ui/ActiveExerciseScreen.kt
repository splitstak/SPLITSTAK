package com.splitstak.app.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.splitstak.app.wear.data.ActionSender
import com.splitstak.app.wear.data.Exercise
import com.splitstak.app.wear.data.SetEntry
import com.splitstak.app.wear.data.Snapshot
import com.splitstak.app.wear.data.WatchState

/**
 * The main interaction surface — one exercise, one set focused, ± weight,
 * ± reps, and a done toggle. Layout adapts to the exercise's mode:
 *   - reps         (default strength): weight + reps + done
 *   - bodyweight   reps + done (weight column hidden)
 *   - time         seconds + done (single stepper)
 *   - cardio       minutes + miles + done (no set number)
 *
 * Each tap fires an ActionSender call — those are sent to the phone via the
 * Wearable Data Layer's MessageClient, the phone applies the mutation, the
 * phone re-publishes the snapshot, and the watch picks up the change.
 */
@Composable
fun ActiveExerciseScreen(snapshot: Snapshot) {
    val context = LocalContext.current
    val selectedId by WatchState.widgetSelectedFlow.collectAsState()
    val exercise = remember(snapshot, selectedId) {
        snapshot.exercises.firstOrNull { it.id == selectedId }
            ?: snapshot.currentExercise()
    } ?: return

    val setIdx = remember(exercise) {
        // Default to the first incomplete set; falls back to last set.
        val idx = exercise.sets.indexOfFirst { !it.d }
        if (idx >= 0) idx else (exercise.sets.size - 1).coerceAtLeast(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header: name + target
        Text(
            text = exercise.name.uppercase(),
            style = MaterialTheme.typography.title2,
            color = SplitstakColors.Text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (exercise.target.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = exercise.target,
                style = MaterialTheme.typography.caption1,
                color = SplitstakColors.Accent
            )
        }

        Spacer(Modifier.height(6.dp))

        // Set indicator with ◀ ▶ nav
        if (exercise.mode != "cardio") {
            ExerciseNavRow(
                setLabel = "SET ${setIdx + 1} / ${exercise.sets.size}",
                onPrev = { ActionSender.nav(context, -1) },
                onNext = { ActionSender.nav(context, 1) }
            )
        } else {
            ExerciseNavRow(
                setLabel = "CARDIO",
                onPrev = { ActionSender.nav(context, -1) },
                onNext = { ActionSender.nav(context, 1) }
            )
        }

        Spacer(Modifier.height(8.dp))

        // Mode-specific stepper rows + done
        when (exercise.mode) {
            "bodyweight" -> {
                val set = exercise.sets.getOrNull(setIdx) ?: SetEntry("", "", "", false)
                Stepper(
                    label = "RPS",
                    value = set.r.ifEmpty { "—" },
                    onDec = { ActionSender.incReps(context, exercise.id, setIdx, -1) },
                    onInc = { ActionSender.incReps(context, exercise.id, setIdx, 1) }
                )
            }
            "time" -> {
                val set = exercise.sets.getOrNull(setIdx) ?: SetEntry("", "", "", false)
                Stepper(
                    label = "SEC",
                    value = set.t.ifEmpty { "—" },
                    onDec = { ActionSender.incHold(context, exercise.id, setIdx, -1) },
                    onInc = { ActionSender.incHold(context, exercise.id, setIdx, 1) }
                )
            }
            "cardio" -> {
                val c = exercise.cardio
                Stepper(
                    label = "MIN",
                    value = c?.time?.ifEmpty { "—" } ?: "—",
                    onDec = { ActionSender.incTime(context, exercise.id, -1.0) },
                    onInc = { ActionSender.incTime(context, exercise.id, 1.0) }
                )
                Spacer(Modifier.height(4.dp))
                Stepper(
                    label = "MI",
                    value = c?.distance?.ifEmpty { "—" } ?: "—",
                    onDec = { ActionSender.incDistance(context, exercise.id, -1.0) },
                    onInc = { ActionSender.incDistance(context, exercise.id, 1.0) }
                )
            }
            else -> { // "reps" — default strength
                val set = exercise.sets.getOrNull(setIdx) ?: SetEntry("", "", "", false)
                Stepper(
                    label = "WT",
                    value = set.w.ifEmpty { "—" },
                    onDec = { ActionSender.incWeight(context, exercise.id, setIdx, -1) },
                    onInc = { ActionSender.incWeight(context, exercise.id, setIdx, 1) }
                )
                Spacer(Modifier.height(4.dp))
                Stepper(
                    label = "RPS",
                    value = set.r.ifEmpty { "—" },
                    onDec = { ActionSender.incReps(context, exercise.id, setIdx, -1) },
                    onInc = { ActionSender.incReps(context, exercise.id, setIdx, 1) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Done toggle
        val isDone: Boolean = when (exercise.mode) {
            "cardio" -> exercise.cardio?.done == true
            else -> exercise.sets.getOrNull(setIdx)?.d == true
        }
        DoneCircle(
            done = isDone,
            onClick = {
                if (exercise.mode == "cardio") {
                    ActionSender.toggleDone(context, exercise.id, -1)
                } else {
                    ActionSender.toggleDone(context, exercise.id, setIdx)
                }
            }
        )

        Spacer(Modifier.height(6.dp))

        // Progress dots — filled per allComplete exercise on the day
        ProgressDots(exercises = snapshot.exercises)
    }
}

@Composable
private fun ExerciseNavRow(
    setLabel: String,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmallCircleButton(label = "‹", onClick = onPrev)
        Text(
            text = setLabel,
            style = MaterialTheme.typography.caption1,
            color = SplitstakColors.TextDim,
            modifier = Modifier.width(80.dp),
            textAlign = TextAlign.Center
        )
        SmallCircleButton(label = "›", onClick = onNext)
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    onDec: () -> Unit,
    onInc: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption3,
            color = SplitstakColors.TextFaint,
            modifier = Modifier.width(26.dp),
            textAlign = TextAlign.End
        )
        SmallCircleButton(label = "−", onClick = onDec)
        Box(
            modifier = Modifier
                .width(58.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SplitstakColors.Bg)
                .border(1.dp, SplitstakColors.Border, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.body2,
                color = SplitstakColors.Text
            )
        }
        SmallCircleButton(label = "+", onClick = onInc)
    }
}

@Composable
private fun SmallCircleButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(28.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = SplitstakColors.Surface,
            contentColor = SplitstakColors.Text
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            color = SplitstakColors.Text
        )
    }
}

@Composable
private fun DoneCircle(done: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(40.dp).clip(CircleShape),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (done) SplitstakColors.Accent else SplitstakColors.Surface,
            contentColor = if (done) SplitstakColors.Bg else SplitstakColors.TextDim
        )
    ) {
        Text(
            text = if (done) "✓" else "○",
            style = MaterialTheme.typography.title2,
            color = if (done) SplitstakColors.Bg else SplitstakColors.TextDim
        )
    }
}

@Composable
private fun ProgressDots(exercises: List<Exercise>) {
    if (exercises.isEmpty()) return
    val text = buildString {
        for ((i, ex) in exercises.withIndex()) {
            if (i > 0) append(' ')
            append(if (ex.allComplete) '●' else '○')
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        color = SplitstakColors.Accent
    )
}

