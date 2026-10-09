package com.databelay.refwatch.data.garmin

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GarminLinkViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeRepository : GarminLinkRepository {
        var codeResult: Result<PairingCode> = Result.success(PairingCode("123456", 600_000))
        var devices = mutableListOf<LinkedGarminDevice>()
        var unlinked = mutableListOf<String>()

        override suspend fun createPairingCode() = codeResult
        override suspend fun listDevices() = Result.success(devices.toList())
        override suspend fun unlink(deviceId: String): Result<Unit> {
            unlinked += deviceId
            devices.removeAll { it.id == deviceId }
            return Result.success(Unit)
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(repo: GarminLinkRepository) =
        GarminLinkViewModel(repo).also { it.nowMillis = { testScheduler.currentTime } }

    @Test
    fun startsByLoadingLinkedWatches() = runTest(dispatcher) {
        val repo = FakeRepository().apply { devices += LinkedGarminDevice("h1", "fenix", 1) }
        val vm = viewModel(repo)
        advanceUntilIdle()
        assertThat(vm.state.value.devices.map { it.id }).containsExactly("h1")
    }

    @Test
    fun aRequestedCodeCountsDownAndDisappearsAtExpiry() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = viewModel(repo)
        vm.requestCode()
        runCurrent()
        assertThat(vm.state.value.code?.code).isEqualTo("123456")
        assertThat(vm.state.value.secondsLeft).isEqualTo(600)
        advanceTimeBy(60_000)
        runCurrent()
        assertThat(vm.state.value.secondsLeft).isEqualTo(540)
        advanceTimeBy(540_000)
        runCurrent()
        assertThat(vm.state.value.code).isNull()
    }

    @Test
    fun aNewlyLinkedWatchClearsTheCode() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = viewModel(repo)
        vm.requestCode()
        runCurrent()
        repo.devices += LinkedGarminDevice("h9", "fenix", 1)
        advanceTimeBy(GarminLinkViewModel.POLL_INTERVAL_MS + 1)
        runCurrent()
        assertThat(vm.state.value.code).isNull()
        assertThat(vm.state.value.devices.map { it.id }).containsExactly("h9")
    }

    @Test
    fun aFailedRequestShowsAnError() = runTest(dispatcher) {
        val repo = FakeRepository().apply { codeResult = Result.failure(RuntimeException("offline")) }
        val vm = viewModel(repo)
        vm.requestCode()
        advanceUntilIdle()
        assertThat(vm.state.value.code).isNull()
        assertThat(vm.state.value.error).isNotNull()
        assertThat(vm.state.value.busy).isFalse()
    }

    @Test
    fun unlinkRemovesTheWatch() = runTest(dispatcher) {
        val repo = FakeRepository().apply { devices += LinkedGarminDevice("h1", "fenix", 1) }
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.unlink("h1")
        advanceUntilIdle()
        assertThat(repo.unlinked).containsExactly("h1")
        assertThat(vm.state.value.devices).isEmpty()
    }
}
