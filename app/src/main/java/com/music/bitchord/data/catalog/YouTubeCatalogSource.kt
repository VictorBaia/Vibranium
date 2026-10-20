package com.music.bitchord.data.catalog

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.UserPlaylist

/** Dormant adapter retained for upstream compatibility and opt-in builds. */
internal object YouTubeCatalogSource : CatalogSource {
    override suspend fun search(query: String, filter: SearchFilter): Result<List<SearchResult>> =
        YtMusicRepository.search(query, filter)

    override suspend fun userPlaylists(): Result<List<UserPlaylist>> =
        YtMusicRepository.userPlaylists()
}
