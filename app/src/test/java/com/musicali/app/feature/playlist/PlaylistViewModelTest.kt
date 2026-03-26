package com.musicali.app.feature.playlist

import com.musicali.app.domain.usecase.PlaylistGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Test 1: Initial state is Idle
    @Test
    fun initialState_isIdle() {
        val vm = PlaylistViewModel(FakePlaylistGenerator(emptyList()))
        assertEquals(PlaylistUiState.Idle, vm.uiState.value)
    }

    // Test 2: generate() is no-op when already Generating (D-04)
    @Test
    fun generate_noOp_whenAlreadyGenerating() = runTest {
        // Emit a StageChanged so ViewModel transitions to Generating, then never complete
        val fake = FakePlaylistGenerator(
            listOf(GenerationProgress.StageChanged(Stage.SCRAPING)),
            neverCompletes = true
        )
        val vm = PlaylistViewModel(fake)

        // Start generation — emits StageChanged(SCRAPING), ViewModel enters Generating, then hangs
        vm.generate()

        // Confirm we are in Generating state
        assertTrue("Expected Generating state but was ${vm.uiState.value}",
            vm.uiState.value is PlaylistUiState.Generating)

        // Call generate() again — D-04 guard should block the second call
        vm.generate()

        // executeCallCount should still be 1 (second call was a no-op)
        assertEquals("generate() should not restart while Generating", 1, fake.executeCallCount)
    }

    // Test 3: StageChanged(SCRAPING) maps to Generating state
    @Test
    fun generate_mapsStageChanged_toGeneratingState() = runTest {
        val fake = FakePlaylistGenerator(
            listOf(GenerationProgress.StageChanged(Stage.SCRAPING))
        )
        val vm = PlaylistViewModel(fake)
        vm.generate()

        val state = vm.uiState.value
        assertTrue("Expected Generating but was $state", state is PlaylistUiState.Generating)
        val generating = state as PlaylistUiState.Generating
        assertEquals(Stage.SCRAPING, generating.stage)
        assertEquals(0, generating.searchedCount)
        assertEquals(65, generating.totalArtists)
        assertEquals(0.0f, generating.progressFraction, 0.001f)
    }

    // Test 4: SearchProgress(23, 65) maps to correct fraction
    @Test
    fun generate_mapsSearchProgress_toGeneratingWithFraction() = runTest {
        val fake = FakePlaylistGenerator(
            listOf(GenerationProgress.SearchProgress(23, 65))
        )
        val vm = PlaylistViewModel(fake)
        vm.generate()

        val state = vm.uiState.value
        assertTrue("Expected Generating but was $state", state is PlaylistUiState.Generating)
        val generating = state as PlaylistUiState.Generating
        assertEquals(Stage.SEARCHING, generating.stage)
        assertEquals(23, generating.searchedCount)
        assertEquals(65, generating.totalArtists)
        val expectedFraction = 0.10f + (23f / 65f) * 0.80f
        assertEquals(expectedFraction, generating.progressFraction, 0.001f)
    }

    // Test 5: Success event maps to Success state
    @Test
    fun generate_mapsSuccess_toSuccessState() = runTest {
        val fake = FakePlaylistGenerator(
            listOf(GenerationProgress.Success(50, 15))
        )
        val vm = PlaylistViewModel(fake)
        vm.generate()

        assertEquals(PlaylistUiState.Success(50, 15), vm.uiState.value)
    }

    // Test 6: Failed(QuotaExceeded) maps to Error state with correct message and action
    @Test
    fun generate_mapsFailed_toErrorState() = runTest {
        val fake = FakePlaylistGenerator(
            listOf(GenerationProgress.Failed(GenerationError.QuotaExceeded))
        )
        val vm = PlaylistViewModel(fake)
        vm.generate()

        val state = vm.uiState.value
        assertTrue("Expected Error but was $state", state is PlaylistUiState.Error)
        val error = state as PlaylistUiState.Error
        assertEquals("YouTube daily quota reached", error.message)
        assertEquals(ErrorAction.DismissOnly, error.action)
    }

    // Test 7: dismissError() resets to Idle
    @Test
    fun dismissError_resetsToIdle() = runTest {
        val fake = FakePlaylistGenerator(
            listOf(GenerationProgress.Failed(GenerationError.NetworkError))
        )
        val vm = PlaylistViewModel(fake)
        vm.generate()

        // Confirm error state
        assertTrue("Expected Error state", vm.uiState.value is PlaylistUiState.Error)

        // Dismiss
        vm.dismissError()
        assertEquals(PlaylistUiState.Idle, vm.uiState.value)
    }
}

/**
 * Fake PlaylistGenerator for ViewModel tests.
 * - If neverCompletes=true, the flow suspends indefinitely (simulates ongoing generation).
 * - Otherwise, it emits all events and completes.
 */
class FakePlaylistGenerator(
    private val events: List<GenerationProgress>,
    private val neverCompletes: Boolean = false
) : PlaylistGenerator {
    var executeCallCount = 0

    override fun execute(): Flow<GenerationProgress> = flow {
        executeCallCount++
        events.forEach { emit(it) }
        if (neverCompletes) {
            delay(Long.MAX_VALUE)
        }
    }
}
