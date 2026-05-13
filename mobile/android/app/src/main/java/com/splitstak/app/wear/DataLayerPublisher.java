package com.splitstak.app.wear;

import android.content.Context;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONObject;

/**
 * Phone-side mirror of the lock-screen widget's snapshot publishing. Whenever
 * the WebView's JS calls Capacitor.Plugins.SplitstakWidget.publishSnapshot(),
 * we also fan the snapshot out to any paired Wear OS device via DataClient.
 *
 * Watch consumer: WatchDataLayerService in the :wear module.
 *
 * Sync semantics: putDataItem buffers if the watch is offline and delivers
 * when reconnected. We bump a timestamp on every put so the system treats
 * even semantically-identical snapshots as fresh (otherwise unchanged JSON
 * wouldn't trigger an onDataChanged event on the watch).
 */
public class DataLayerPublisher {

    /** Must match com.splitstak.app.wear.data.DataPaths.SNAPSHOT in the wear module. */
    private static final String PATH_SNAPSHOT = "/splitstak/snapshot";
    private static final String KEY_SNAPSHOT_JSON = "snapshot_json";
    private static final String KEY_TS = "ts";

    public static void publishToWatch(Context context, JSONObject snapshot) {
        if (snapshot == null) return;
        try {
            PutDataMapRequest req = PutDataMapRequest.create(PATH_SNAPSHOT);
            req.getDataMap().putString(KEY_SNAPSHOT_JSON, snapshot.toString());
            // Timestamp forces the data item to be considered changed even
            // when the JSON body is byte-identical — the Wearable Data
            // Layer dedupes identical items by default.
            req.getDataMap().putLong(KEY_TS, System.currentTimeMillis());
            req.setUrgent();
            Wearable.getDataClient(context.getApplicationContext())
                    .putDataItem(req.asPutDataRequest())
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            // Best-effort — no watch paired, or Bluetooth off.
                        }
                    });
        } catch (Exception ignored) {
            // Wearable Data Layer can throw if Play services is missing.
        }
    }
}
