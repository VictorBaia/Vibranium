package com.music.bitchord

import com.music.bitchord.data.navidrome.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeRepositoryTest {
    private val config = NavidromeConfig.create(
        "https://music.example.test", "listener", NavidromeAuth.password("secret"),
    )

    @Test
    fun `foundation endpoints extract documented response fields`() = runBlocking {
        val repository = NavidromeRepository({ config }, FakeTransport())

        assertEquals("listener", repository.currentUser().value().username)
        assertEquals("apiKeyAuthentication", repository.extensions().value().single().name)
        assertEquals("Artist", repository.artists().value().single().name)
        assertEquals("Album", repository.artist("artist-1").value().album.single().displayName)
        assertEquals("Artist biography", repository.artistInfo2("artist-1").value().biography)
        assertEquals("Song", repository.album("album-1").value().song.single().title)
        assertEquals("audio/flac", repository.song("song-1").value().contentType)
        assertEquals("Newest", repository.albums(NavidromeAlbumListType.NEWEST).value().single().displayName)
        assertEquals("Mix", repository.playlists().value().single().name)
        assertEquals("Song", repository.playlist("playlist-1").value().entry.single().title)
        assertEquals("Rock", repository.genres().value().single().value)
        assertEquals("Favourite", repository.starred().value().song.single().title)
        assertEquals("Genre song", repository.songsByGenre("Rock").value().single().title)
        assertEquals("Random song", repository.randomSongs().value().single().title)
        assertEquals("Server line", repository.lyricsBySongId("song-1").value().structuredLyrics.single().line.single().value)
        assertEquals("Song", repository.search("song").value().song.single().title)
    }

    @Test
    fun `unconfigured repository does not access transport`() = runBlocking {
        val transport = FakeTransport()
        val result = NavidromeRepository({ null }, transport).genres()
        assertTrue(result is NavidromeApiResult.InvalidEndpoint)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `album paging parameters are explicit`() = runBlocking {
        val transport = FakeTransport()
        NavidromeRepository({ config }, transport)
            .albums(NavidromeAlbumListType.RECENT, size = 25, offset = 50)
        assertEquals(
            mapOf("type" to "recent", "size" to "25", "offset" to "50"),
            transport.requests.single().second,
        )
    }

    @Test
    fun `API key resolves username through token info`() = runBlocking {
        val apiConfig = NavidromeConfig.create(
            "https://music.example.test", "", NavidromeAuth.apiKey("api-secret"),
        )
        val transport = FakeTransport()
        val user = NavidromeRepository({ apiConfig }, transport).currentUser().value()

        assertEquals("token-user", user.username)
        assertEquals(listOf("tokenInfo", "getUser"), transport.requests.map { it.first })
        assertEquals("token-user", transport.requests.last().second["username"])
    }

    @Test
    fun `playlist and favourite writes use OpenSubsonic parameters`() = runBlocking {
        val transport = FakeTransport()
        val repository = NavidromeRepository({ config }, transport)

        repository.starSong("song-1")
        repository.unstarSong("song-1")
        repository.createPlaylist("Road trip", isPublic = true)
        repository.renamePlaylist("playlist-1", "Renamed")
        repository.addToPlaylist("playlist-1", "song-2")
        repository.removeFromPlaylist("playlist-1", 3)
        repository.deletePlaylist("playlist-1")

        assertEquals("song-1", transport.requests[0].second["id"])
        assertEquals("song-1", transport.requests[1].second["id"])
        assertEquals(mapOf("name" to "Road trip", "public" to "true"), transport.requests[2].second)
        assertEquals("Renamed", transport.requests[3].second["name"])
        assertEquals("song-2", transport.requests[4].second["songIdToAdd"])
        assertEquals("3", transport.requests[5].second["songIndexToRemove"])
        assertEquals("playlist-1", transport.requests[6].second["id"])
    }

    private class FakeTransport : NavidromeTransport {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()

        override suspend fun testConnection(config: NavidromeConfig) =
            NavidromeConnectionResult.Connected("navidrome", "test", true)

        override suspend fun request(
            config: NavidromeConfig,
            method: String,
            parameters: Map<String, String>,
        ): NavidromeApiResult<NavidromeResponse> {
            requests += method to parameters
            val response = when (method) {
                "tokenInfo" -> NavidromeResponse("ok", tokenInfo = NavidromeTokenInfo("token-user"))
                "getUser" -> NavidromeResponse("ok", user = NavidromeUser(parameters["username"] ?: config.username))
                "getOpenSubsonicExtensions" -> NavidromeResponse(
                    "ok", openSubsonicExtensions = listOf(NavidromeExtension("apiKeyAuthentication", listOf(1))),
                )
                "getArtists" -> NavidromeResponse(
                    "ok", artists = NavidromeArtistIndexes(
                        index = listOf(NavidromeArtistIndex("A", listOf(NavidromeArtist("artist-1", "Artist")))),
                    ),
                )
                "getArtist" -> NavidromeResponse(
                    "ok", artist = NavidromeArtist(
                        "artist-1", "Artist", album = listOf(NavidromeAlbum("album-1", name = "Album")),
                    ),
                )
                "getArtistInfo2" -> NavidromeResponse(
                    "ok", artistInfo2 = NavidromeArtistInfo(biography = "Artist biography"),
                )
                "getAlbum" -> NavidromeResponse(
                    "ok", album = NavidromeAlbum(
                        "album-1", name = "Album", song = listOf(NavidromeSong("song-1", "Song")),
                    ),
                )
                "getSong" -> NavidromeResponse(
                    "ok", song = NavidromeSong("song-1", "Song", contentType = "audio/flac"),
                )
                "getAlbumList2" -> NavidromeResponse(
                    "ok", albumList2 = NavidromeAlbumList(listOf(NavidromeAlbum("album-2", name = "Newest"))),
                )
                "getPlaylists" -> NavidromeResponse(
                    "ok", playlists = NavidromePlaylistList(listOf(NavidromePlaylist("playlist-1", "Mix"))),
                )
                "getPlaylist" -> NavidromeResponse(
                    "ok", playlist = NavidromePlaylist(
                        "playlist-1", "Mix", entry = listOf(NavidromeSong("song-1", "Song")),
                    ),
                )
                "getGenres" -> NavidromeResponse(
                    "ok", genres = NavidromeGenreList(listOf(NavidromeGenre("Rock", 1, 1))),
                )
                "getStarred2" -> NavidromeResponse(
                    "ok", starred2 = NavidromeStarred(song = listOf(NavidromeSong("fav-1", "Favourite"))),
                )
                "getSongsByGenre" -> NavidromeResponse(
                    "ok", songsByGenre = NavidromeSongList(listOf(NavidromeSong("genre-1", "Genre song"))),
                )
                "getRandomSongs" -> NavidromeResponse(
                    "ok", randomSongs = NavidromeSongList(listOf(NavidromeSong("random-1", "Random song"))),
                )
                "getLyricsBySongId" -> NavidromeResponse(
                    "ok", lyricsList = NavidromeLyricsList(
                        listOf(NavidromeStructuredLyrics(synced = true, line = listOf(NavidromeLyricsLine(0, "Server line")))),
                    ),
                )
                "star", "unstar", "updatePlaylist", "deletePlaylist" -> NavidromeResponse("ok")
                "createPlaylist" -> NavidromeResponse(
                    "ok", playlist = NavidromePlaylist("created-1", parameters["name"].orEmpty()),
                )
                "search3" -> NavidromeResponse(
                    "ok", searchResult3 = NavidromeSearchResult(song = listOf(NavidromeSong("song-1", "Song"))),
                )
                else -> return NavidromeApiResult.NotFound(method)
            }
            return NavidromeApiResult.Success(response)
        }
    }

    private fun <T> NavidromeApiResult<T>.value(): T =
        (this as NavidromeApiResult.Success<T>).value
}

