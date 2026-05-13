package com.splitstak.app.wear.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Mirror of the JSON snapshot the phone publishes via WidgetState.publishSnapshot.
 * Keep field names lined up with index.html:buildWidgetSnapshot so a mismatch
 * never silently swallows data.
 */
data class Snapshot(
    val schemaVersion: Int,
    val isRestDay: Boolean,
    val currentDay: String?,
    val currentDayLabel: String?,
    val exercises: List<Exercise>,
    val selectedExerciseId: String?,
    val weightStep: Int,
    val repStep: Int,
    val timeStep: Double,
    val distanceStep: Double,
    val holdStep: Int,
    val timerEnabled: Boolean,
    val timerDuration: Int,
    /** Epoch ms when the current rest ends, or null if no rest is active. */
    val restEndsAt: Long?
) {
    val dayAllComplete: Boolean
        get() = !isRestDay && exercises.isNotEmpty() && exercises.all { it.allComplete }

    fun currentExercise(): Exercise? {
        val id = selectedExerciseId
        if (id != null) {
            exercises.firstOrNull { it.id == id }?.let { return it }
        }
        return exercises.firstOrNull()
    }

    fun indexOf(exerciseId: String): Int = exercises.indexOfFirst { it.id == exerciseId }

    companion object {
        fun fromJson(json: String): Snapshot? = try {
            parse(JSONObject(json))
        } catch (e: JSONException) {
            null
        }

        fun parse(o: JSONObject): Snapshot {
            val exercises = mutableListOf<Exercise>()
            val arr = o.optJSONArray("exercises") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val ex = arr.optJSONObject(i) ?: continue
                exercises.add(Exercise.parse(ex))
            }
            val restEndsAtRaw = o.opt("restEndsAt")
            val restEndsAt: Long? = when (restEndsAtRaw) {
                is Number -> restEndsAtRaw.toLong().takeIf { it > 0L }
                else -> null
            }
            return Snapshot(
                schemaVersion = o.optInt("schemaVersion", 1),
                isRestDay = o.optBoolean("isRestDay", false),
                currentDay = o.optString("currentDay", null),
                currentDayLabel = o.optString("currentDayLabel", null),
                exercises = exercises,
                selectedExerciseId = o.optString("selectedExerciseId", null),
                weightStep = o.optInt("weightStep", 5),
                repStep = o.optInt("repStep", 1),
                timeStep = o.optDouble("timeStep", 5.0),
                distanceStep = o.optDouble("distanceStep", 0.5),
                holdStep = o.optInt("holdStep", 5),
                timerEnabled = o.optBoolean("timerEnabled", true),
                timerDuration = o.optInt("timerDuration", 90),
                restEndsAt = restEndsAt
            )
        }
    }
}

data class Exercise(
    val id: String,
    val name: String,
    val target: String,
    val kind: String,           // "strength" | "cardio"
    val mode: String,           // "reps" | "bodyweight" | "time" | "cardio"
    val sets: List<SetEntry>,
    val cardio: CardioEntry?,
    val lastTop: String?,
    val allComplete: Boolean,
    val prevBestWeight: Double?,
    val prevBestReps: Int
) {
    /** PR detection mirrors the lock-screen widget's isPR(). */
    val isPr: Boolean
        get() {
            if (mode == "cardio" || mode == "time") return false
            if (mode == "bodyweight") {
                val topReps = sets.maxOfOrNull { parseIntSafe(it.r) } ?: 0
                if (topReps <= 0) return false
                val prev = prevBestReps
                if (prev <= 0) return false
                return topReps > prev
            }
            var topW = 0.0
            var topRAtTopW = 0
            for (s in sets) {
                val w = parseDoubleSafe(s.w)
                val r = parseIntSafe(s.r)
                if (w > topW) { topW = w; topRAtTopW = r }
                else if (w == topW && r > topRAtTopW) { topRAtTopW = r }
            }
            if (topW <= 0.0) return false
            val pw = prevBestWeight ?: return false
            if (topW > pw) return true
            if (topW == pw && topRAtTopW > prevBestReps) return true
            return false
        }

    companion object {
        fun parse(o: JSONObject): Exercise {
            val sets = mutableListOf<SetEntry>()
            val arr = o.optJSONArray("sets") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                sets.add(SetEntry(
                    w = s.optString("w", ""),
                    r = s.optString("r", ""),
                    t = s.optString("t", ""),
                    d = s.optBoolean("d", false)
                ))
            }
            val cardio = o.optJSONObject("cardio")?.let {
                CardioEntry(
                    time = it.optString("time", ""),
                    distance = it.optString("distance", ""),
                    done = it.optBoolean("done", false)
                )
            }
            val prevW = if (o.isNull("prevBestWeight")) null
                        else o.optDouble("prevBestWeight").takeIf { !it.isNaN() }
            return Exercise(
                id = o.optString("id"),
                name = o.optString("name", ""),
                target = o.optString("target", ""),
                kind = o.optString("kind", "strength"),
                mode = o.optString("mode", "reps"),
                sets = sets,
                cardio = cardio,
                lastTop = o.optString("lastTop", null),
                allComplete = o.optBoolean("allComplete", false),
                prevBestWeight = prevW,
                prevBestReps = o.optInt("prevBestReps", 0)
            )
        }
    }
}

data class SetEntry(val w: String, val r: String, val t: String, val d: Boolean)
data class CardioEntry(val time: String, val distance: String, val done: Boolean)

private fun parseIntSafe(s: String): Int = s.trim().toIntOrNull() ?: 0
private fun parseDoubleSafe(s: String): Double = s.trim().toDoubleOrNull() ?: 0.0
