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
}
