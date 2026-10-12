package com.databelay.refwatch.data.garmin

/** Where the phone sends people to install RefWatch on a Garmin watch. */
object GarminStore {
    /**
     * The Connect IQ store page of the watch app. Null until Garmin approves the app: the Garmin
     * section stays hidden from people who have not linked a watch, so it never points at an app
     * they cannot install.
     */
    val STORE_URL: String? = null

    fun showSection(storeUrl: String?, linkedDevices: Int): Boolean =
        !storeUrl.isNullOrBlank() || linkedDevices > 0
}
