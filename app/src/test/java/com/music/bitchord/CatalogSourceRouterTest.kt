package com.music.bitchord

import com.music.bitchord.data.catalog.CatalogSourceMode
import com.music.bitchord.data.catalog.CatalogSourceRouter
import com.music.bitchord.data.catalog.CatalogConfigurationState
import com.music.bitchord.data.catalog.NavidromeAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogSourceRouterTest {
    @Test
    fun `Navidrome is selected by default with no fallback`() = runTest {
        val store = MutableStateFlow<CatalogConfigurationState>(CatalogConfigurationState.NotConfigured)
        val router = CatalogSourceRouter(navidromeEnabled = true, configurationState = store, scope = this)
        var youtubeCalls = 0

        val result = router.route(
            navidrome = { Result.failure<String>(IllegalStateException("offline")) },
            youtube = { youtubeCalls++; Result.success("youtube") },
        )

        assertEquals(CatalogSourceMode.NAVIDROME, router.state.value.mode)
        assertEquals("offline", result.exceptionOrNull()?.message)
        assertEquals(0, youtubeCalls)
        router.close()
    }

    @Test
    fun `opt-in legacy build selects only YouTube`() = runTest {
        val store = MutableStateFlow<CatalogConfigurationState>(CatalogConfigurationState.NotConfigured)
        val router = CatalogSourceRouter(navidromeEnabled = false, configurationState = store, scope = this)

        assertEquals("youtube", router.route({ "navidrome" }, { "youtube" }))
        assertEquals(CatalogSourceMode.YOUTUBE, router.state.value.mode)
        router.close()
    }

    @Test
    fun `active source rejects IDs from the other namespace`() = runTest {
        val store = MutableStateFlow<CatalogConfigurationState>(CatalogConfigurationState.NotConfigured)
        val navidrome = CatalogSourceRouter(true, store, this)
        val youtube = CatalogSourceRouter(false, store, this)

        assertEquals("nd:song", navidrome.requireActiveId("nd:song"))
        assertIllegalState { navidrome.requireActiveId("dQw4w9WgXcQ") }
        assertEquals("dQw4w9WgXcQ", youtube.requireActiveId("dQw4w9WgXcQ"))
        assertIllegalState { youtube.requireActiveId("nd:song") }
        assertIllegalState { youtube.requireActiveId("nd:") }
        navidrome.close()
        youtube.close()
    }

    @Test
    fun `configuration changes rotate the routing generation and account scope`() = runTest {
        val store = MutableStateFlow<CatalogConfigurationState>(CatalogConfigurationState.NotConfigured)
        val router = CatalogSourceRouter(true, store, this)

        store.value = CatalogConfigurationState.Configured("account-a")
        advanceUntilIdle()

        assertEquals(NavidromeAvailability.READY, router.state.value.navidromeAvailability)
        assertEquals("account-a", router.state.value.accountKey)
        assertEquals(1, router.state.value.generation)

        store.value = CatalogConfigurationState.NotConfigured
        advanceUntilIdle()
        assertEquals(NavidromeAvailability.NOT_CONFIGURED, router.state.value.navidromeAvailability)
        assertEquals(null, router.state.value.accountKey)
        assertEquals(2, router.state.value.generation)
        router.close()
    }

    private fun assertIllegalState(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // Expected.
        }
    }
}
