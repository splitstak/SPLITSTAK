package com.splitstak.app.wear.data

import android.content.Context
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Sends user-tap actions (inc weight, inc reps, toggle done, …) back to the
 * phone via MessageClient. The phone's PhoneDataLayerService receives these
 * at /splitstak/action and routes them through the existing WidgetState
 * mutation methods — same code path as the lock-screen widget's broadcasts.
 *
 * One-way fire-and-forget. If a message fails (watch offline, phone killed)
 * the user's local snapshot drift gets corrected on next phone-side publish.
 * Good enough for v1; a queueing layer can be added later if needed.
 */
object ActionSender {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun incWeight(context: Context, exerciseId: String, setIdx: Int, delta: Int) {
        send(context, action("inc_weight").apply {
            put("exerciseId", exerciseId)
            put("setIdx", setIdx)
            put("delta", delta)
        })
    }

    fun incReps(context: Context, exerciseId: String, setIdx: Int, delta: Int) {
        send(context, action("inc_reps").apply {
            put("exerciseId", exerciseId)
            put("setIdx", setIdx)
            put("delta", delta)
        })
    }

    fun incHold(context: Context, exerciseId: String, setIdx: Int, delta: Int) {
        send(context, action("inc_hold").apply {
            put("exerciseId", exerciseId)
            put("setIdx", setIdx)
            put("delta", delta)
        })
    }

    fun incTime(context: Context, exerciseId: String, deltaMin: Double) {
        send(context, action("inc_time").apply {
            put("exerciseId", exerciseId)
            put("delta", deltaMin)
        })
    }

    fun incDistance(context: Context, exerciseId: String, deltaMi: Double) {
        send(context, action("inc_distance").apply {
            put("exerciseId", exerciseId)
            put("delta", deltaMi)
        })
    }

    fun toggleDone(context: Context, exerciseId: String, setIdx: Int) {
        send(context, action("toggle_done").apply {
            put("exerciseId", exerciseId)
            put("setIdx", setIdx)
        })
    }

    fun nav(context: Context, delta: Int) {
        send(context, action("nav").apply { put("delta", delta) })
    }

    fun dismissRest(context: Context) {
        send(context, action("dismiss_rest"))
    }

    fun finishDay(context: Context) {
        send(context, action("finish_day"))
    }

    fun selectExercise(context: Context, exerciseId: String) {
        // Local-only on the watch (no need to bother the phone for a UI hint).
        WatchState.setSelected(context, exerciseId)
        send(context, action("select").apply { put("exerciseId", exerciseId) })
    }

    // ---- internals ------------------------------------------------------

    private fun action(type: String) = JSONObject().apply {
        put("type", type)
        put("ts", System.currentTimeMillis())
    }

    private fun send(context: Context, payload: JSONObject) {
        scope.launch {
            try {
                val nodes: List<Node> = Wearable.getNodeClient(context.applicationContext)
                    .connectedNodes
                    .await()
                if (nodes.isEmpty()) return@launch
                val bytes = payload.toString().toByteArray(Charsets.UTF_8)
                val messageClient = Wearable.getMessageClient(context.applicationContext)
                // Fan out to every reachable node — usually just the paired
                // phone, but a tablet companion would also receive.
                for (node in nodes) {
                    runCatching {
                        messageClient.sendMessage(node.id, DataPaths.ACTION, bytes).await()
                    }
                }
            } catch (_: Exception) {
                // Best effort; intentional silent failure.
            }
        }
    }
}
