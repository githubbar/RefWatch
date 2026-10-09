package com.databelay.refwatch.data.garmin

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GarminLinkParsingTest {
    @Test
    fun parsesAPairingCode() {
        val code = parsePairingCode(mapOf("code" to "012345", "expiresAt" to 1_700_000_600_000L))
        assertThat(code).isEqualTo(PairingCode("012345", 1_700_000_600_000L))
    }

    @Test
    fun expiryArrivingAsAnIntOrDoubleStillParses() {
        assertThat(parsePairingCode(mapOf("code" to "1", "expiresAt" to 5)).expiresAtMillis).isEqualTo(5L)
        assertThat(parsePairingCode(mapOf("code" to "1", "expiresAt" to 5.0)).expiresAtMillis).isEqualTo(5L)
    }

    @Test(expected = IllegalStateException::class)
    fun aReplyWithoutACodeIsAnError() {
        parsePairingCode(mapOf("expiresAt" to 5L))
    }

    @Test
    fun parsesTheDeviceList() {
        val devices = parseDevices(
            mapOf(
                "devices" to listOf(
                    mapOf("id" to "h1", "deviceName" to "006-B2604-00", "createdAt" to 1L, "lastSeenAt" to 9L)
                )
            )
        )
        assertThat(devices).containsExactly(LinkedGarminDevice("h1", "006-B2604-00", 9L))
    }

    @Test
    fun anEmptyOrMissingListIsNoDevices() {
        assertThat(parseDevices(mapOf("devices" to emptyList<Any>()))).isEmpty()
        assertThat(parseDevices(mapOf<String, Any>())).isEmpty()
    }

    @Test
    fun secondsLeftRoundsUpAndNeverGoesNegative() {
        assertThat(secondsLeft(expiresAtMillis = 10_000, nowMillis = 0)).isEqualTo(10)
        assertThat(secondsLeft(expiresAtMillis = 10_000, nowMillis = 9_001)).isEqualTo(1)
        assertThat(secondsLeft(expiresAtMillis = 10_000, nowMillis = 10_000)).isEqualTo(0)
        assertThat(secondsLeft(expiresAtMillis = 10_000, nowMillis = 99_000)).isEqualTo(0)
    }
}
