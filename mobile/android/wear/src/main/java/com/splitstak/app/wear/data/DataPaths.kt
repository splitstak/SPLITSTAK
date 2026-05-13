package com.splitstak.app.wear.data

/**
 * Single source of truth for the Wearable Data Layer paths shared between
 * the phone and the watch. Mirrored on the phone side at
 * com.splitstak.app.wear.DataLayerPublisher and PhoneDataLayerService.
 */
object DataPaths {
    const val SNAPSHOT = "/splitstak/snapshot"
    const val SNAPSHOT_KEY = "snapshot_json"
    const val ACTION = "/splitstak/action"
}
