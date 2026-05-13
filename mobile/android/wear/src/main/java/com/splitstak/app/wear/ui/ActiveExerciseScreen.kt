package com.splitstak.app.wear.ui

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.Surface
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.splitstak.app.wear.RotaryDispatcher
import com.splitstak.app.wear.data.ActionSender
import com.splitstak.app.wear.data.Exercise
import com.splitstak.app.wear.data.SetEntry
import com.splitstak.app.wear.data.Snapshot
import com.splitstak.app.wear.data.WatchState
import kotlin.math.abs

/**
 * The main interaction surface — one exercise, one set displayed at a time.
 *
 * Three focusable shapes, all driven by the same pattern:
 *   1. Tap a shape → orange pulsing border (focused).
 *   2. Spin the crown → adjusts the focused field. The crown indicator
 *      arrow pulses on the same side the physical crown is on, in sync
 *      with the focused box's border (one shared animation clock).
 *   3. Tap again → release focus, crown becomes idle.
 *
 * The three shapes:
 *   - Exercise semicircle (top): the entire upper half of the screen, a
 *     half-circle whose curve traces the watch's bezel. Contains
 *     name + target + "SET 2/3". Crown cycles exercises.
 *   - WT / RPS boxes (or mode-equivalent): crown adjusts the value.
 *
 * Rotary input is read from [RotaryDispatcher] — a SharedFlow fed by
 * MainActivity.dispatchGenericMotionEvent. This avoids Compose's focus
 * system, which was unreliable with nested tap targets.
 *
 * On hitting a PR, a full-face black overlay flashes "PR" for ~3s.
 * Every interaction calls [ActionSender], which optimistically mutates
 * the watch's local snapshot for instant feedback and sends the same
 * action to the phone over MessageClient for the source-of-truth update.
 */
@Composable
fun ActiveExerciseScreen(snapshot: Snapshot) {
    val context = LocalContext.current
    val selectedId by WatchState.widgetSelectedFlow.collectAsState()
    val exercise = snapshot.exercises.firstOrNull { it.id == selectedId }
        ?: snapshot.currentExercise()
        ?: return

    val isCardio = exercise.kind == "cardio"

    val setIdx = remember(exercise.id, exercise.sets) {
        val idx = exercise.sets.indexOfFirst { !it.d }
        if (idx >= 0) idx else (exercise.sets.size - 1).coerceAtLeast(0)
    }

    // Which shape is focused for crown input. Intentionally NOT keyed
    // on exercise.id — we want focus to persist while the user spins
    // through exercises with the crown.
    var focused by remember { mutableStateOf<String?>(null) }

    val isLefty = remember { detectLefty(context) }

    // ONE shared pulse for the focused border + crown arrow, smooth
    // ease-in-out so it breathes rather than blinks.
    val pulse = rememberInfiniteTransition(label = "focus-pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "focus-pulse-alpha"
    )

    // Subscribe to rotary events. The collector is only active while a
    // shape is focused; otherwise crown rotation is ignored.
    LaunchedEffect(focused, exercise.id, setIdx) {
        val f = focused ?: return@LaunchedEffect
        // Per-mode detent thresholds. Lower for inc-style adjustments
        // so the crown feels responsive; higher for exercise nav so a
        // single roll doesn't skip past three exercises.
        val threshold = if (f == "exercise") 50f else 24f
        var accum = 0f
        RotaryDispatcher.events.collect { delta ->
            accum += delta
            while (abs(accum) >= threshold) {
                val sign = if (accum > 0) 1 else -1
                accum -= sign * threshold
                when (f) {
                    "exercise" -> ActionSender.nav(context, sign)
                    "weight" -> ActionSender.incWeight(context, exercise.id, setIdx, sign)
                    "reps"   -> ActionSender.incReps(context, exercise.id, setIdx, sign)
                    "hold"   -> ActionSender.incHold(context, exercise.id, setIdx, sign)
                    "ctime"  -> ActionSender.incTime(context, exercise.id, sign.toDouble())
                    "cdist"  -> ActionSender.incDistance(context, exercise.id, sign.toDouble())
                }
            }
        }
    }

    // PR overlay — rising edge of exercise.isPr while id stays constant.
    var showPrOverlay by remember { mutableStateOf(false) }
    val prevPr = remember(exercise.id) { mutableStateOf(exercise.isPr) }
    LaunchedEffect(exercise.id, exercise.isPr) {
        if (exercise.isPr && !prevPr.value) {
            showPrOverlay = true
            kotlinx.coroutines.delay(3000)
            showPrOverlay = false
        }
        prevPr.value = exercise.isPr
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplitstakColors.Bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ExerciseSemicircle(
                exercise = exercise,
                setIdx = setIdx,
                isCardio = isCardio,
                focused = focused == "exercise",
                pulseAlpha = pulseAlpha,
                onClick = {
                    focused = if (focused == "exercise") null else "exercise"
                }
            )

            BodyBoxes(
                exercise = exercise,
                setIdx = setIdx,
                isCardio = isCardio,
                focused = focused,
                pulseAlpha = pulseAlpha,
                onToggleFocus = { tag -> focused = if (focused == tag) null else tag }
            )

            val isDone: Boolean = if (isCardio) {
                exercise.cardio?.done == true
            } else {
                exercise.sets.getOrNull(setIdx)?.d == true
            }
            DoneCircle(
                done = isDone,
                onClick = {
                    if (isCardio) {
                        ActionSender.toggleDone(context, exercise.id, -1)
                    } else {
                        ActionSender.toggleDone(context, exercise.id, setIdx)
                    }
                }
            )

            ProgressDots(exercises = snapshot.exercises)
        }

        // Crown indicator arrow — pulses on whichever side the crown is
        // physically on, in sync with the focused border.
        if (focused != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp),
                contentAlignment = if (isLefty) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Text(
                    text = if (isLefty) "‹" else "›",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = SplitstakColors.Accent,
                    modifier = Modifier.alpha(pulseAlpha)
                )
            }
        }

        // PR overlay
        if (showPrOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SplitstakColors.Bg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PR",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Black,
                    color = SplitstakColors.Accent
                )
            }
        }
    }
}

/**
 * Worn-on-right-wrist mode rotates the display 180°. Check both the
 * Display's rotation and the user setting fallback.
 */
private fun detectLefty(context: Context): Boolean {
    val rot = runCatching {
        if (Build.VERSION.SDK_INT >= 30) {
            context.display?.rotation
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.rotation
        }
    }.getOrNull()
    if (rot == Surface.ROTATION_180) return true
    val user = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.USER_ROTATION, 0)
    }.getOrDefault(0)
    return user == Surface.ROTATION_180
}

/**
 * True semicircle shape — flat chord at the bottom, full arc from
 * bottom-left up over the top to bottom-right. No flat sides. Uses a
 * quadratic bezier; tune the control point so the apex sits exactly at
 * the box's top edge.
 */
private object SemicircleShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        // Bezier control point at (w/2, -h) yields a peak at (w/2, 0).
        // Math: B(0.5).y = 0.25·h + 0.5·(-h) + 0.25·h = 0.
        val path = Path().apply {
            moveTo(0f, h)
            quadraticBezierTo(w / 2f, -h, w, h)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun ExerciseSemicircle(
    exercise: Exercise,
    setIdx: Int,
    isCardio: Boolean,
    focused: Boolean,
    pulseAlpha: Float,
    onClick: () -> Unit
) {
    val borderColor: Color = if (focused) {
        SplitstakColors.Accent.copy(alpha = pulseAlpha)
    } else {
        SplitstakColors.Border
    }

    val targetLine = buildString {
        if (exercise.target.isNotEmpty()) append(exercise.target)
        if (exercise.isPr) {
            if (isNotEmpty()) append(" · ")
            append("PR")
        }
    }

    // Width ≈ 92% of screen so the arc clears the bezel. Height fixed
    // so the bezier produces a visually-balanced semicircle (height
    // around half the width gives a near-circular look).
    Column(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .height(110.dp)
            .background(SplitstakColors.Surface, shape = SemicircleShape)
            .border(1.5.dp, borderColor, shape = SemicircleShape)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            // Top padding clears the narrow apex of the arc so text
            // sits in the wider lower portion of the semicircle.
            .padding(start = 14.dp, end = 14.dp, top = 38.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = exercise.name.uppercase(),
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = SplitstakColors.Text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (targetLine.isNotEmpty()) {
            Text(
                text = targetLine,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = SplitstakColors.Accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (isCardio) "CARDIO" else "SET ${setIdx + 1}/${exercise.sets.size}",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = SplitstakColors.TextDim,
            maxLines = 1
        )
    }
}

@Composable
private fun BodyBoxes(
    exercise: Exercise,
    setIdx: Int,
    isCardio: Boolean,
    focused: String?,
    pulseAlpha: Float,
    onToggleFocus: (String) -> Unit
) {
    val set = exercise.sets.getOrNull(setIdx) ?: SetEntry("", "", "", false)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            isCardio -> {
                val c = exercise.cardio
                ValueBox("MIN", c?.time ?: "", focused == "ctime", pulseAlpha) { onToggleFocus("ctime") }
                ValueBox("MI", c?.distance ?: "", focused == "cdist", pulseAlpha) { onToggleFocus("cdist") }
            }
            exercise.mode == "bodyweight" -> {
                ValueBox("RPS", set.r, focused == "reps", pulseAlpha) { onToggleFocus("reps") }
            }
            exercise.mode == "time" -> {
                ValueBox("SEC", set.t, focused == "hold", pulseAlpha) { onToggleFocus("hold") }
            }
            else -> {
                ValueBox("WT", set.w, focused == "weight", pulseAlpha) { onToggleFocus("weight") }
                ValueBox("RPS", set.r, focused == "reps", pulseAlpha) { onToggleFocus("reps") }
            }
        }
    }
}

@Composable
private fun ValueBox(
    label: String,
    value: String,
    focused: Boolean,
    pulseAlpha: Float,
    onClick: () -> Unit
) {
    val borderColor: Color = if (focused) {
        SplitstakColors.Accent.copy(alpha = pulseAlpha)
    } else {
        SplitstakColors.Border
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            color = SplitstakColors.TextFaint
        )
        Spacer(Modifier.height(1.dp))
        Box(
            modifier = Modifier
                .width(54.dp)
                .height(32.dp)
                .background(SplitstakColors.Surface)
                .border(1.5.dp, borderColor)
                .pointerInput(Unit) { detectTapGestures { onClick() } },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value.ifEmpty { "—" },
                fontFamily = FontFamily.Monospace,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = SplitstakColors.Text
            )
        }
    }
}

@Composable
private fun DoneCircle(done: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (done) SplitstakColors.Accent else SplitstakColors.Surface,
            contentColor = if (done) SplitstakColors.Bg else SplitstakColors.TextDim
        )
    ) {
        Text(
            text = if (done) "✓" else "○",
            fontFamily = FontFamily.SansSerif,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (done) SplitstakColors.Bg else SplitstakColors.TextDim
        )
    }
}

@Composable
private fun ProgressDots(exercises: List<Exercise>) {
    if (exercises.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (ex in exercises) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(
                        if (ex.allComplete) SplitstakColors.Accent
                        else SplitstakColors.Border,
                        shape = CircleShape
                    )
            )
        }
    }
}
