package com.databelay.refwatch.data.garmin

/**
 * The Firestore document `config/garmin`, edited in the Firebase console. `enabled` stays false
 * until Garmin approves the watch app, so the Garmin section never points at an app people cannot
 * install; turning it on needs no phone release.
 */
data class GarminStoreConfig(val enabled: Boolean, val storeUrl: String?) {
    /** The Connect IQ store page to link to, or null while the section is switched off. */
    val activeStoreUrl: String?
        get() = storeUrl?.takeIf { enabled && it.startsWith("https://") }
}

/** Reads `config/garmin`; a missing document or a field of the wrong type counts as off. */
fun parseStoreConfig(data: Map<String, Any?>?): GarminStoreConfig =
    GarminStoreConfig(
        enabled = data?.get("enabled") as? Boolean ?: false,
        storeUrl = (data?.get("storeUrl") as? String)?.trim()?.takeIf { it.isNotEmpty() }
    )

/** Where the phone sends people to install RefWatch on a Garmin watch. */
object GarminStore {
    /** Shown once the store link is on, and always to people who already linked a watch. */
    fun showSection(storeUrl: String?, linkedDevices: Int): Boolean =
        !storeUrl.isNullOrBlank() || linkedDevices > 0
}
