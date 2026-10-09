package com.databelay.refwatch.data.garmin

import com.google.firebase.Firebase
import com.google.firebase.functions.functions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** A code the referee enters on the watch, valid until [expiresAtMillis]. */
data class PairingCode(val code: String, val expiresAtMillis: Long)

/** A Garmin watch linked to this account. [id] is the server's token hash. */
data class LinkedGarminDevice(val id: String, val deviceName: String, val lastSeenAtMillis: Long)

/** Linking Garmin watches, through the Cloud Functions in functions/garmin. */
interface GarminLinkRepository {
    suspend fun createPairingCode(): Result<PairingCode>
    suspend fun listDevices(): Result<List<LinkedGarminDevice>>
    suspend fun unlink(deviceId: String): Result<Unit>
}

class FirebaseGarminLinkRepository @Inject constructor() : GarminLinkRepository {
    override suspend fun createPairingCode(): Result<PairingCode> = runCatching {
        parsePairingCode(call("createGarminPairingCode"))
    }

    override suspend fun listDevices(): Result<List<LinkedGarminDevice>> = runCatching {
        parseDevices(call("listGarminDevices"))
    }

    override suspend fun unlink(deviceId: String): Result<Unit> = runCatching {
        call("unlinkGarminDevice", mapOf("tokenHash" to deviceId))
        Unit
    }

    private suspend fun call(name: String, data: Any? = null): Any? =
        Firebase.functions.getHttpsCallable(name).call(data).await().data
}

/** The reply of createGarminPairingCode: {code, expiresAt}. */
fun parsePairingCode(data: Any?): PairingCode {
    val map = data as? Map<*, *> ?: error("Unexpected reply: $data")
    val code = map["code"] as? String ?: error("Reply has no code: $data")
    val expiresAt = (map["expiresAt"] as? Number)?.toLong() ?: error("Reply has no expiry: $data")
    return PairingCode(code, expiresAt)
}

/** The reply of listGarminDevices: {devices: [{id, deviceName, lastSeenAt, ...}]}. */
fun parseDevices(data: Any?): List<LinkedGarminDevice> {
    val list = (data as? Map<*, *>)?.get("devices") as? List<*> ?: return emptyList()
    return list.mapNotNull { item ->
        val device = item as? Map<*, *> ?: return@mapNotNull null
        LinkedGarminDevice(
            id = device["id"] as? String ?: return@mapNotNull null,
            deviceName = device["deviceName"] as? String ?: "Garmin watch",
            lastSeenAtMillis = (device["lastSeenAt"] as? Number)?.toLong() ?: 0L
        )
    }
}

/** Whole seconds until [expiresAtMillis], rounded up, never below zero. */
fun secondsLeft(expiresAtMillis: Long, nowMillis: Long): Long =
    ((expiresAtMillis - nowMillis + 999) / 1000).coerceAtLeast(0)
