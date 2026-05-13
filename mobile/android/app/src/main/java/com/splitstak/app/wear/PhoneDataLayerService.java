package com.splitstak.app.wear;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;
import com.splitstak.app.widget.SplitstakAppWidgetProvider;
import com.splitstak.app.widget.WidgetState;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Receives action messages from the paired Wear OS app at /splitstak/action.
 * Each message is a JSON object carrying a "type" and payload — the same
 * action shapes the lock-screen widget uses. We route them through the
 * existing WidgetState mutation methods so the watch, the lock-screen
 * widget, and the PWA stay perfectly aligned without duplicating logic.
 */
public class PhoneDataLayerService extends WearableListenerService {

    /** Must match com.splitstak.app.wear.data.DataPaths.ACTION in the wear module. */
    private static final String PATH_ACTION = "/splitstak/action";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (!PATH_ACTION.equals(messageEvent.getPath())) {
            super.onMessageReceived(messageEvent);
            return;
        }
        try {
            String json = new String(messageEvent.getData(), StandardCharsets.UTF_8);
            JSONObject action = new JSONObject(json);
            handleAction(action);
        } catch (JSONException e) {
            // Malformed message — ignore. No retry; the watch will be
            // corrected on the next phone-side snapshot publish.
        }
    }

    private void handleAction(JSONObject action) {
        String type = action.optString("type", "");
        WidgetState state = new WidgetState(getApplicationContext());
        JSONObject snap = state.loadSnapshot();

        int setIdx = action.optInt("setIdx", -1);

        switch (type) {
            case "inc_weight": {
                int delta = action.optInt("delta", 0);
                int step = snap == null ? 5 : snap.optInt("weightStep", 5);
                state.applyIncStrength("w", setIdx, delta, step);
                break;
            }
            case "inc_reps": {
                int delta = action.optInt("delta", 0);
                int step = snap == null ? 1 : snap.optInt("repStep", 1);
                state.applyIncStrength("r", setIdx, delta, step);
                break;
            }
            case "inc_hold": {
                int delta = action.optInt("delta", 0);
                int step = snap == null ? 5 : snap.optInt("holdStep", 5);
                state.applyIncHold(setIdx, delta, step);
                break;
            }
            case "inc_time": {
                // Cardio time delta arrives as a double sign (±1.0); step is
                // the minutes-per-tap setting from the snapshot.
                int delta = (int) Math.signum(action.optDouble("delta", 0.0));
                double step = snap == null ? 5.0 : snap.optDouble("timeStep", 5.0);
                state.applyIncCardio("time", delta, step);
                break;
            }
            case "inc_distance": {
                int delta = (int) Math.signum(action.optDouble("delta", 0.0));
                double step = snap == null ? 0.5 : snap.optDouble("distanceStep", 0.5);
                state.applyIncCardio("distance", delta, step);
                break;
            }
            case "toggle_done":
                state.applyToggleDone(setIdx);
                break;
            case "nav":
                state.selectByDelta(action.optInt("delta", 0));
                break;
            case "select":
                // The watch lets the user pick a specific exercise — update
                // the widget's selected id so the lock-screen widget mirrors.
                String exerciseId = action.optString("exerciseId", null);
                if (exerciseId != null && !exerciseId.isEmpty()) {
                    state.setWidgetSelectedId(exerciseId);
                }
                break;
            case "dismiss_rest":
                state.applyDismissRest();
                break;
            case "finish_day":
                state.applyFinishDay();
                break;
            default:
                return;
        }

        // Refresh the lock-screen widget so its visual stays in lockstep
        // with whatever the user just did on the watch.
        SplitstakAppWidgetProvider.refreshAll(getApplicationContext());

        // Republish the snapshot to the watch so the watch's local cache
        // catches up before the JS-side drain runs. (The JS will also
        // publish on its own next saveState, but this closes the loop
        // immediately for the actively-watching user.)
        JSONObject fresh = state.loadSnapshot();
        if (fresh != null) {
            DataLayerPublisher.publishToWatch(getApplicationContext(), fresh);
        }
    }
}
