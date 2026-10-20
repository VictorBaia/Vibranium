package com.music.bitchord.data.navidrome

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class NavidromeEnvelope(
    @SerialName("subsonic-response")
    val response: NavidromeResponse,
)

@Serializable
internal data class NavidromeResponse(
    val status: String,
    val version: String? = null,
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean? = null,
    val error: NavidromeApiError? = null,
    val user: NavidromeUser? = null,
    val artists: NavidromeArtistIndexes? = null,
    val artist: NavidromeArtist? = null,
    val artistInfo2: NavidromeArtistInfo? = null,
    val album: NavidromeAlbum? = null,
    val albumInfo: NavidromeAlbumInfo? = null,
    val song: NavidromeSong? = null,
    val albumList2: NavidromeAlbumList? = null,
    val playlists: NavidromePlaylistList? = null,
    val playlist: NavidromePlaylist? = null,
    val genres: NavidromeGenreList? = null,
    val openSubsonicExtensions: List<NavidromeExtension> = emptyList(),
    val tokenInfo: NavidromeTokenInfo? = null,
    val searchResult3: NavidromeSearchResult? = null,
    val starred2: NavidromeStarred? = null,
    val songsByGenre: NavidromeSongList? = null,
    val randomSongs: NavidromeSongList? = null,
    val lyricsList: NavidromeLyricsList? = null,
)

@Serializable
internal data class NavidromeApiError(
    val code: Int,
    val message: String? = null,
    val helpUrl: String? = null,
)

@Serializable
internal data class NavidromeTokenInfo(val username: String)

internal sealed interface NavidromeConnectionResult {
    data class Connected(
        val serverType: String?,
        val serverVersion: String?,
        val openSubsonic: Boolean,
    ) : NavidromeConnectionResult

    data class InvalidEndpoint(val reason: String) : NavidromeConnectionResult
    data class Unreachable(val reason: String) : NavidromeConnectionResult
    data class InvalidCredentials(val reason: String) : NavidromeConnectionResult
    data class UnsupportedAuthentication(val reason: String) : NavidromeConnectionResult
    data class Forbidden(val reason: String) : NavidromeConnectionResult
    data class InvalidResponse(val reason: String) : NavidromeConnectionResult
}

internal sealed interface NavidromeApiResult<out T> {
    data class Success<T>(val value: T) : NavidromeApiResult<T>
    data class InvalidEndpoint(val reason: String) : NavidromeApiResult<Nothing>
    data class Unreachable(val reason: String) : NavidromeApiResult<Nothing>
    data class InvalidCredentials(val reason: String) : NavidromeApiResult<Nothing>
    data class UnsupportedAuthentication(val reason: String) : NavidromeApiResult<Nothing>
    data class Forbidden(val reason: String) : NavidromeApiResult<Nothing>
    data class NotFound(val reason: String) : NavidromeApiResult<Nothing>
    data class InvalidResponse(val reason: String) : NavidromeApiResult<Nothing>
}

