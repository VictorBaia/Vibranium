package com.music.bitchord.data.navidrome

/** OpenSubsonic catalogue boundary. It returns protocol models; UI mapping is a later gate. */
internal class NavidromeRepository(
    private val config: () -> NavidromeConfig?,
    private val client: NavidromeTransport = NavidromeClient(),
) {
    suspend fun connection(): NavidromeConnectionResult = configuredConnection { client.testConnection(it) }

    suspend fun user(username: String): NavidromeApiResult<NavidromeUser> =
        call("getUser", mapOf("username" to username)).required("user") { it.user }

    suspend fun currentUser(): NavidromeApiResult<NavidromeUser> {
        val current = config()
            ?: return NavidromeApiResult.InvalidEndpoint("Navidrome server is not configured")
        val username = current.username.ifBlank {
            when (val token = client.request(current, "tokenInfo").required("tokenInfo") { it.tokenInfo }) {
                is NavidromeApiResult.Success -> token.value.username
                is NavidromeApiResult.InvalidEndpoint -> return token
                is NavidromeApiResult.Unreachable -> return token
                is NavidromeApiResult.InvalidCredentials -> return token
                is NavidromeApiResult.UnsupportedAuthentication -> return token
                is NavidromeApiResult.Forbidden -> return token
                is NavidromeApiResult.NotFound -> return token
                is NavidromeApiResult.InvalidResponse -> return token
            }
        }
        return client.request(current, "getUser", mapOf("username" to username))
            .required("user") { it.user }
    }

    suspend fun extensions(): NavidromeApiResult<List<NavidromeExtension>> =
        call("getOpenSubsonicExtensions").map { it.openSubsonicExtensions }

    suspend fun artists(): NavidromeApiResult<List<NavidromeArtist>> =
        call("getArtists").required("artists") { it.artists }.map(NavidromeArtistIndexes::all)

    suspend fun artist(id: String): NavidromeApiResult<NavidromeArtist> =
        call("getArtist", mapOf("id" to requiredId(id))).required("artist") { it.artist }

    suspend fun artistInfo2(id: String): NavidromeApiResult<NavidromeArtistInfo> =
        call("getArtistInfo2", mapOf("id" to requiredId(id))).required("artistInfo2") { it.artistInfo2 }

    suspend fun album(id: String): NavidromeApiResult<NavidromeAlbum> =
        call("getAlbum", mapOf("id" to requiredId(id))).required("album") { it.album }

    suspend fun albumInfo2(id: String): NavidromeApiResult<NavidromeAlbumInfo> =
        call("getAlbumInfo2", mapOf("id" to requiredId(id))).required("albumInfo") { it.albumInfo }

    suspend fun song(id: String): NavidromeApiResult<NavidromeSong> =
        call("getSong", mapOf("id" to requiredId(id))).required("song") { it.song }

    suspend fun albums(
        type: NavidromeAlbumListType,
        size: Int = 50,
        offset: Int = 0,
    ): NavidromeApiResult<List<NavidromeAlbum>> {
        require(size in 1..500) { "Album page size must be between 1 and 500" }
        require(offset >= 0) { "Album page offset must not be negative" }
        return call(
            "getAlbumList2",
            mapOf("type" to type.wireValue, "size" to size.toString(), "offset" to offset.toString()),
        ).required("albumList2") { it.albumList2 }.map(NavidromeAlbumList::album)
    }

    suspend fun playlists(): NavidromeApiResult<List<NavidromePlaylist>> =
        call("getPlaylists").required("playlists") { it.playlists }.map(NavidromePlaylistList::playlist)

    suspend fun playlist(id: String): NavidromeApiResult<NavidromePlaylist> =
        call("getPlaylist", mapOf("id" to requiredId(id))).required("playlist") { it.playlist }

    suspend fun genres(): NavidromeApiResult<List<NavidromeGenre>> =
        call("getGenres").required("genres") { it.genres }.map(NavidromeGenreList::genre)

    suspend fun starred(): NavidromeApiResult<NavidromeStarred> =
        call("getStarred2").required("starred2") { it.starred2 }

    suspend fun songsByGenre(
        genre: String,
        count: Int = 500,
        offset: Int = 0,
    ): NavidromeApiResult<List<NavidromeSong>> {
        require(genre.isNotBlank()) { "Genre must not be blank" }
        require(count in 1..500) { "Genre song count must be between 1 and 500" }
        require(offset >= 0) { "Genre song offset must not be negative" }
        return call(
            "getSongsByGenre",
            mapOf("genre" to genre, "count" to count.toString(), "offset" to offset.toString()),
        ).required("songsByGenre") { it.songsByGenre }.map(NavidromeSongList::song)
    }

    suspend fun randomSongs(size: Int = 50): NavidromeApiResult<List<NavidromeSong>> {
        require(size in 1..500) { "Random song count must be between 1 and 500" }
        return call("getRandomSongs", mapOf("size" to size.toString()))
            .required("randomSongs") { it.randomSongs }
            .map(NavidromeSongList::song)
    }

    suspend fun lyricsBySongId(id: String): NavidromeApiResult<NavidromeLyricsList> =
        call("getLyricsBySongId", mapOf("id" to requiredId(id), "enhanced" to "true"))
            .required("lyricsList") { it.lyricsList }

    suspend fun starSong(id: String): NavidromeApiResult<Unit> =
        call("star", mapOf("id" to requiredId(id))).map { Unit }

    suspend fun unstarSong(id: String): NavidromeApiResult<Unit> =
        call("unstar", mapOf("id" to requiredId(id))).map { Unit }

    /** Reports both now-playing and completed listens to the active server account. */
    suspend fun scrobble(id: String, submission: Boolean): NavidromeApiResult<Unit> =
        call("scrobble", mapOf("id" to requiredId(id), "submission" to submission.toString())).map { Unit }

    suspend fun createPlaylist(name: String, isPublic: Boolean = false): NavidromeApiResult<NavidromePlaylist> {
        require(name.isNotBlank()) { "Playlist name must not be blank" }
        return call(
            "createPlaylist",
            mapOf("name" to name.trim(), "public" to isPublic.toString()),
        )
            .required("playlist") { it.playlist }
    }

    suspend fun renamePlaylist(id: String, name: String): NavidromeApiResult<Unit> {
        require(name.isNotBlank()) { "Playlist name must not be blank" }
        return call(
            "updatePlaylist",
            mapOf("playlistId" to requiredId(id), "name" to name.trim()),
        ).map { Unit }
    }

    suspend fun addToPlaylist(id: String, songId: String): NavidromeApiResult<Unit> =
        call(
            "updatePlaylist",
            mapOf("playlistId" to requiredId(id), "songIdToAdd" to requiredId(songId)),
        ).map { Unit }

    suspend fun removeFromPlaylist(id: String, songIndex: Int): NavidromeApiResult<Unit> {
        require(songIndex >= 0) { "Playlist song index must not be negative" }
        return call(
            "updatePlaylist",
            mapOf("playlistId" to requiredId(id), "songIndexToRemove" to songIndex.toString()),
        ).map { Unit }
    }

    suspend fun deletePlaylist(id: String): NavidromeApiResult<Unit> =
        call("deletePlaylist", mapOf("id" to requiredId(id))).map { Unit }

    suspend fun search(
        query: String,
        artistCount: Int = 20,
        albumCount: Int = 20,
        songCount: Int = 50,
    ): NavidromeApiResult<NavidromeSearchResult> {
        require(query.isNotBlank()) { "Search query must not be blank" }
        require(artistCount in 0..500 && albumCount in 0..500 && songCount in 0..500) {
            "Search result counts must be between 0 and 500"
        }
        return call(
            "search3",
            mapOf(
                "query" to query,
                "artistCount" to artistCount.toString(),
                "albumCount" to albumCount.toString(),
                "songCount" to songCount.toString(),
            ),
        ).required("searchResult3") { it.searchResult3 }
    }

    private suspend fun call(
        method: String,
        parameters: Map<String, String> = emptyMap(),
    ): NavidromeApiResult<NavidromeResponse> {
        val current = config()
            ?: return NavidromeApiResult.InvalidEndpoint("Navidrome server is not configured")
        return client.request(current, method, parameters)
    }

    private suspend fun configuredConnection(
        block: suspend (NavidromeConfig) -> NavidromeConnectionResult,
    ): NavidromeConnectionResult {
        val current = config()
            ?: return NavidromeConnectionResult.InvalidEndpoint("Navidrome server is not configured")
        return block(current)
    }

    private fun requiredId(id: String): String {
        require(id.isNotBlank()) { "Navidrome ID must not be blank" }
        return id
    }
}

private inline fun <T, R> NavidromeApiResult<T>.map(transform: (T) -> R): NavidromeApiResult<R> = when (this) {
    is NavidromeApiResult.Success -> NavidromeApiResult.Success(transform(value))
    is NavidromeApiResult.InvalidEndpoint -> this
    is NavidromeApiResult.Unreachable -> this
    is NavidromeApiResult.InvalidCredentials -> this
    is NavidromeApiResult.UnsupportedAuthentication -> this
    is NavidromeApiResult.Forbidden -> this
    is NavidromeApiResult.NotFound -> this
    is NavidromeApiResult.InvalidResponse -> this
}

private inline fun <T> NavidromeApiResult<NavidromeResponse>.required(
    field: String,
    extract: (NavidromeResponse) -> T?,
): NavidromeApiResult<T> = when (this) {
    is NavidromeApiResult.Success -> extract(value)?.let { NavidromeApiResult.Success(it) }
        ?: NavidromeApiResult.InvalidResponse("Successful response did not contain $field")
    is NavidromeApiResult.InvalidEndpoint -> this
    is NavidromeApiResult.Unreachable -> this
    is NavidromeApiResult.InvalidCredentials -> this
    is NavidromeApiResult.UnsupportedAuthentication -> this
    is NavidromeApiResult.Forbidden -> this
    is NavidromeApiResult.NotFound -> this
    is NavidromeApiResult.InvalidResponse -> this
}

