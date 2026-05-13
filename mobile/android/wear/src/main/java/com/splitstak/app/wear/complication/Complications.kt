package com.splitstak.app.wear.complication

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.splitstak.app.wear.data.WatchState

/**
 * Tiny pill showing "N/M" set progress on any watch face. Updates only when
 * the snapshot changes — the watch face polls cheaply, we recompute from
 * cached WatchState on every onComplicationRequest.
 */
class SetProgressComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("2/3").build(),
                contentDescription = PlainComplicationText.Builder("Set progress").build()
            ).setTitle(PlainComplicationText.Builder("SET").build()).build()
            else -> null
        }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val snapshot = WatchState.currentSnapshot(applicationContext)
            ?: return NoDataComplicationData()
        val ex = snapshot.currentExercise() ?: return NoDataComplicationData()
        if (ex.kind == "cardio" || ex.mode == "cardio") {
            // Cardio is one entry — show done/not-done glyph instead.
            val text = if (ex.cardio?.done == true) "✓" else "○"
            return ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(text).build(),
                contentDescription = PlainComplicationText.Builder("Cardio").build()
            ).setTitle(PlainComplicationText.Builder("CRD").build()).build()
        }
        val done = ex.sets.count { it.d }
        val total = ex.sets.size
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("$done/$total").build(),
            contentDescription = PlainComplicationText.Builder("Set progress").build()
        ).setTitle(PlainComplicationText.Builder("SET").build()).build()
    }
}

/**
 * Live countdown pill while a rest timer is running. Renders as "—" when
 * no rest is active — the watch face hides empty values by default, so
 * the user only sees the pill during actual rests.
 */
class RestTimerComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("1:23").build(),
                contentDescription = PlainComplicationText.Builder("Rest timer").build()
            ).setTitle(PlainComplicationText.Builder("REST").build()).build()
            else -> null
        }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val snapshot = WatchState.currentSnapshot(applicationContext)
            ?: return NoDataComplicationData()
        val endsAt = snapshot.restEndsAt ?: return NoDataComplicationData()
        val remaining = endsAt - System.currentTimeMillis()
        if (remaining <= 0L) return NoDataComplicationData()
        val totalSec = remaining / 1000L
        val text = "%d:%02d".format(totalSec / 60, totalSec % 60)
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder("Rest remaining").build()
        ).setTitle(PlainComplicationText.Builder("REST").build()).build()
    }
}

/**
 * Helper invoked from the Data Layer listener whenever the phone publishes a
 * fresh snapshot. Asks the system to re-poll both complications, which then
 * pull updated values from WatchState.
 */
object RefreshComplications {
    fun requestUpdate(context: Context) {
        listOf(
            SetProgressComplicationService::class.java,
            RestTimerComplicationService::class.java
        ).forEach { cls ->
            try {
                ComplicationDataSourceUpdateRequester
                    .create(context.applicationContext, ComponentName(context, cls))
                    .requestUpdateAll()
            } catch (_: Exception) {
                // No watch face is using this complication; safe no-op.
            }
        }
    }
}
