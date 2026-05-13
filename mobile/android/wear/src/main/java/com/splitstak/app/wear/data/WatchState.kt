package com.splitstak.app.wear.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide cache of the most recent Snapshot received from the phone.
 *
 *  - The Data Layer listener calls [update] whenever the phone publishes
 *    a new snapshot. The listener fires regardless of whether any UI is
 *    bound, which is why this is a singleton object rather than a
 *    ViewModel.
 *  - UI / Tile / Complication consumers observe [snapshotFlow] (Compose)
 *    or call [currentSnapshot] (Tile / Complication onRequest callbacks).
 *  - Snapshots are also written to SharedPreferences so a watch reboot
 *    doesn't show "syncing…" until the phone reconnects.
 */
object WatchState {

    private const val PREFS = "splitstak_wear"
    private const val KEY_SNAPSHOT_JSON = "snapshot_json"
    private const val KEY_WIDGET_SELECTED_ID = "widget_selected_id"

    private val _snapshotFlow = MutableStateFlow<Snapshot?>(null)
    val snapshotFlow: StateFlow<Snapshot?> = _snapshotFlow.asStateFlow()

    private val _widgetSelectedFlow = MutableStateFlow<String?>(null)
    val widgetSelectedFlow: StateFlow<String?> = _widgetSelectedFlow.asStateFlow()

    /** Load the cached snapshot. Safe to call repeatedly; idempotent. */
    fun hydrate(context: Context) {
        if (_snapshotFlow.value != null) return
        val prefs = prefs(context)
        val raw = prefs.getString(KEY_SNAPSHOT_JSON, null)
        if (raw != null) {
            _snapshotFlow.value = Snapshot.fromJson(raw)
        }
        _widgetSelectedFlow.value = prefs.getString(KEY_WIDGET_SELECTED_ID, null)
    }

    /** Replace the snapshot, persist to prefs, fan out to observers. */
    fun update(context: Context, json: String) {
        val snap = Snapshot.fromJson(json) ?: return
        prefs(context).edit().putString(KEY_SNAPSHOT_JSON, json).apply()
        _snapshotFlow.value = snap
        // If watch had no selection yet (cold start) OR the previous
        // selection is no longer in the new snapshot, fall back to the
        // PWA's selected exercise.
        val current = _widgetSelectedFlow.value
        if (current == null || snap.exercises.none { it.id == current }) {
            val newId = snap.selectedExerciseId
                ?: snap.exercises.firstOrNull()?.id
            if (newId != null) setSelected(context, newId)
        }
    }

    fun currentSnapshot(context: Context): Snapshot? {
        if (_snapshotFlow.value == null) hydrate(context)
        return _snapshotFlow.value
    }

    fun setSelected(context: Context, exerciseId: String) {
        prefs(context).edit().putString(KEY_WIDGET_SELECTED_ID, exerciseId).apply()
        _widgetSelectedFlow.value = exerciseId
    }

    /** Resolve the exercise the watch is currently displaying. */
    fun currentExercise(context: Context): Exercise? {
        val snap = currentSnapshot(context) ?: return null
        val id = _widgetSelectedFlow.value
        if (id != null) {
            snap.exercises.firstOrNull { it.id == id }?.let { return it }
        }
        return snap.currentExercise()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
