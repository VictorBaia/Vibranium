package com.music.bitchord

import com.music.bitchord.data.catalog.CatalogGateway
import com.music.bitchord.data.catalog.CatalogConfigurationState
import com.music.bitchord.data.catalog.CatalogSource
import com.music.bitchord.data.catalog.CatalogSourceRouter
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.UserPlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogGatewayTest {
    @Test
    fun `gateway calls one source for each operation`() = runTest {
        val router = CatalogSourceRouter(
            navidromeEnabled = true,
            configurationState = MutableStateFlow<CatalogConfigurationState>(CatalogConfigurationState.NotConfigured),
            scope = this,
        )
        val navidrome = FakeSource(Result.success(emptyList()))
        val youtube = FakeSource(Result.success(emptyList()))
        val gateway = CatalogGateway(router, navidrome, youtube)

        gateway.search("query", SearchFilter.ALL)
        gateway.userPlaylists()

        assertEquals(1, navidrome.searchCalls)
        assertEquals(1, navidrome.playlistCalls)
        assertEquals(0, youtube.searchCalls)
        assertEquals(0, youtube.playlistCalls)
        router.close()
    }

    private class FakeSource(
        private val searchResult: Result<List<SearchResult>>,
    ) : CatalogSource {
        var searchCalls = 0
        var playlistCalls = 0

        override suspend fun search(query: String, filter: SearchFilter): Result<List<SearchResult>> {
            searchCalls++
            return searchResult
        }

        override suspend fun userPlaylists(): Result<List<UserPlaylist>> {
            playlistCalls++
            return Result.success(emptyList())
        }
    }
}
