package com.splitstak.app.wear

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Lightweight haptic feedback. The watch's vibrator is shared across
 * all screens — kept here so RestTimer, value adjustments, exercise
 * navigation, and any future surface all feel consistent.
 *
 * Three intensities:
 *   - tick:  ~30 ms — per-detent click for crown rotation
 *   - short: ~80 ms — rest-timer 10-second warning, timer start
 *   - long:  ~220 ms — rest-timer finish, PR celebration
 */
object Haptic {

    fun tick(context: Context) = vibrate(context, durationMs = 30L)

    fun short(context: Context) = vibrate(context, durationMs = 80L)

    fun long(context: Context) = vibrate(context, durationMs = 220L)

    private fun vibrate(context: Context, durationMs: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (_: Exception) {
            // Vibrator can be null on some emulators; silently no-op.
        }
    }
}
