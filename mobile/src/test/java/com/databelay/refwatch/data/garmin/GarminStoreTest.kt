package com.databelay.refwatch.data.garmin

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GarminStoreTest {
    @Test
    fun hiddenWithoutUrlOrDevices() {
        assertThat(GarminStore.showSection(storeUrl = null, linkedDevices = 0)).isFalse()
    }

    @Test
    fun showsWhenDevicesLinked() {
        assertThat(GarminStore.showSection(storeUrl = null, linkedDevices = 1)).isTrue()
    }

    @Test
    fun showsOnceStoreUrlIsSet() {
        assertThat(GarminStore.showSection(storeUrl = "https://apps.garmin.com/apps/x", linkedDevices = 0)).isTrue()
    }

    @Test
    fun blankUrlCountsAsUnset() {
        assertThat(GarminStore.showSection(storeUrl = " ", linkedDevices = 0)).isFalse()
    }

    @Test
    fun enabledConfigGivesItsUrl() {
        val config = parseStoreConfig(mapOf("enabled" to true, "storeUrl" to "https://apps.garmin.com/apps/x"))
        assertThat(config.activeStoreUrl).isEqualTo("https://apps.garmin.com/apps/x")
    }

    @Test
    fun disabledConfigGivesNoUrl() {
        val config = parseStoreConfig(mapOf("enabled" to false, "storeUrl" to "https://apps.garmin.com/apps/x"))
        assertThat(config.activeStoreUrl).isNull()
    }

    @Test
    fun missingDocumentGivesNoUrl() {
        assertThat(parseStoreConfig(null).activeStoreUrl).isNull()
    }

    @Test
    fun enabledWithoutUrlGivesNoUrl() {
        assertThat(parseStoreConfig(mapOf("enabled" to true)).activeStoreUrl).isNull()
        assertThat(parseStoreConfig(mapOf("enabled" to true, "storeUrl" to " ")).activeStoreUrl).isNull()
    }

    @Test
    fun enabledMustBeABoolean() {
        val config = parseStoreConfig(mapOf("enabled" to "true", "storeUrl" to "https://apps.garmin.com/apps/x"))
        assertThat(config.activeStoreUrl).isNull()
    }

    @Test
    fun onlyHttpsUrlsAreUsed() {
        val config = parseStoreConfig(mapOf("enabled" to true, "storeUrl" to "http://apps.garmin.com/apps/x"))
        assertThat(config.activeStoreUrl).isNull()
    }
}
