package com.splitstak.app.wear.data

import android.content.Intent
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.splitstak.app.wear.complication.RefreshComplications
import com.splitstak.app.wear.tile.RefreshTiles

/**
 * Bound by the Wear OS framework whenever the phone publishes a new snapshot
 * to /splitstak/snapshot or sends a message under the /splitstak/ prefix.
 * We update the shared WatchState cache, then nudge Tile + Complication
 * services to redraw.
 *
 * The listener is intentionally lean — heavy work belongs in the consumers,
 * not in the broadcast handler. Reading 1 KB of JSON and bumping a StateFlow
 * is cheap enough to do synchronously.
 */
class WatchDataLayerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            if (item.uri.path != DataPaths.SNAPSHOT) continue

            val map = DataMapItem.fromDataItem(item).dataMap
            val json = map.getString(DataPaths.SNAPSHOT_KEY) ?: continue
            WatchState.update(applicationContext, json)
            RefreshTiles.requestUpdate(applicationContext)
            RefreshComplications.requestUpdate(applicationContext)
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        // The phone doesn't currently send messages to the watch; reserved
        // for future server-pushed events (e.g., "PR celebration please").
        super.onMessageReceived(messageEvent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Treat as a non-sticky service; we rely on the framework re-binding
        // us when the next data-changed event arrives.
        return super.onStartCommand(intent, flags, startId)
    }
}

