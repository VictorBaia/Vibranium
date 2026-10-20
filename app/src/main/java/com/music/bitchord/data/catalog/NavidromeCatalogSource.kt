package com.music.bitchord.data.catalog

import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.navidrome.NavidromeApiResult
import com.music.bitchord.data.navidrome.NavidromeMapper
import com.music.bitchord.data.navidrome.NavidromeRepository

internal class NavidromeCatalogSource(
    private val repository: NavidromeRepository,
) : CatalogSource {
    override suspend fun search(query: String, filter: SearchFilter): Result<List<SearchResult>> {
        val includeArtists = filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS
        val includeAlbums = filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS
        val includeSongs = filter == SearchFilter.ALL || filter == SearchFilter.SONGS
        return repository.search(
            query = query,
            artistCount = if (includeArtists) DEFAULT_SEARCH_COUNT else 0,
            albumCount = if (includeAlbums) DEFAULT_SEARCH_COUNT else 0,
            songCount = if (includeSongs) DEFAULT_SONG_COUNT else 0,
        ).toResult { NavidromeMapper.search(it, filter) }
    }

    override suspend fun userPlaylists(): Result<List<UserPlaylist>> =
        repository.playlists().toResult { playlists -> playlists.map(NavidromeMapper::userPlaylist) }

    private companion object {
        const val DEFAULT_SEARCH_COUNT = 20
        const val DEFAULT_SONG_COUNT = 50
    }
}

internal class NavidromeCatalogException(
    val category: Category,
    message: String,
) : Exception(message) {
    enum class Category {
        INVALID_ENDPOINT,
        UNREACHABLE,
        INVALID_CREDENTIALS,
        UNSUPPORTED_AUTHENTICATION,
        FORBIDDEN,
        NOT_FOUND,
        INVALID_RESPONSE,
    }
}

private inline fun <T, R> NavidromeApiResult<T>.toResult(transform: (T) -> R): Result<R> = when (this) {
    is NavidromeApiResult.Success -> runCatching { transform(value) }
    is NavidromeApiResult.InvalidEndpoint -> failure(NavidromeCatalogException.Category.INVALID_ENDPOINT, reason)
    is NavidromeApiResult.Unreachable -> failure(NavidromeCatalogException.Category.UNREACHABLE, reason)
    is NavidromeApiResult.InvalidCredentials -> failure(NavidromeCatalogException.Category.INVALID_CREDENTIALS, reason)
    is NavidromeApiResult.UnsupportedAuthentication -> failure(NavidromeCatalogException.Category.UNSUPPORTED_AUTHENTICATION, reason)
    is NavidromeApiResult.Forbidden -> failure(NavidromeCatalogException.Category.FORBIDDEN, reason)
    is NavidromeApiResult.NotFound -> failure(NavidromeCatalogException.Category.NOT_FOUND, reason)
    is NavidromeApiResult.InvalidResponse -> failure(NavidromeCatalogException.Category.INVALID_RESPONSE, reason)
}

private fun <T> failure(category: NavidromeCatalogException.Category, reason: String): Result<T> =
    Result.failure(NavidromeCatalogException(category, reason))
