package com.music.bitchord.data.catalog

import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.UserPlaylist

internal interface CatalogSource {
    suspend fun search(query: String, filter: SearchFilter): Result<List<SearchResult>>
    suspend fun userPlaylists(): Result<List<UserPlaylist>>
}

/** Common catalogue operations routed to exactly one active source. */
internal class CatalogGateway(
    private val router: CatalogSourceRouter,
    private val navidrome: CatalogSource,
    private val youtube: CatalogSource,
) {
    suspend fun search(query: String, filter: SearchFilter): Result<List<SearchResult>> =
        router.route(
            navidrome = { navidrome.search(query, filter) },
            youtube = { youtube.search(query, filter) },
        )

    suspend fun userPlaylists(): Result<List<UserPlaylist>> = router.route(
        navidrome = navidrome::userPlaylists,
        youtube = youtube::userPlaylists,
    )
}
