package com.music.bitchord

import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.navidrome.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeCatalogueTest {
    private val labels = NavidromeCatalogueLabels(
        "Newest", "Recent", "Frequent", "Random", "Liked", "Albums", "Artists", "Playlists", "Genres",
    )
    private val config = NavidromeConfig.create(
        "https://music.example.test", "listener", NavidromeAuth.password("secret"),
    )
    private val catalogue = NavidromeCatalogue(NavidromeRepository({ config }, CatalogueTransport()))

    @Test
    fun `home library explore and search use Navidrome identities`() = runBlocking {
        assertEquals(listOf("Newest", "Recent", "Frequent", "Random"), catalogue.home(labels).getOrThrow().map { it.title })
        assertTrue(catalogue.library(labels).getOrThrow().likedSongs.single().videoId.startsWith("nd:song:"))
        assertTrue(catalogue.explore(labels).getOrThrow().single().items.single().browseId.startsWith("nd:genre:"))
        assertTrue(catalogue.search("song", SearchFilter.ALL).getOrThrow().isNotEmpty())
    }

    @Test
    fun `album and playlist details map into existing detail pages`() = runBlocking {
        val album = catalogue.detail("nd:album:album-1", "", "", null).getOrThrow()
        val playlist = catalogue.detail("nd:playlist:playlist-1", "", "", null).getOrThrow()

        assertTrue(album.songs is UiState.Success)
        assertTrue(playlist.songs is UiState.Success)
        val albumSongs = (album.songs as UiState.Success<List<Song>>).data
        assertEquals(listOf("nd:song:song-1", "nd:song:song-3"), albumSongs.map(Song::videoId))
        assertEquals(listOf(1, 3), albumSongs.map(Song::releaseTrackNumber))
        assertEquals("Artist • 2024", album.subtitle)
        assertEquals("Server album notes", album.description)
    }

    @Test
    fun `artist releases are separated by OpenSubsonic release type`() = runBlocking {
        val artist = catalogue.detail(
            browseId = "nd:artist:artist-1",
            fallbackTitle = "Artist",
            fallbackSubtitle = "",
            fallbackArtwork = null,
            albumSectionTitle = "Albums",
            singleSectionTitle = "Singles",
            epSectionTitle = "EPs",
        ).getOrThrow()

        assertEquals(listOf("Albums", "Singles", "EPs"), artist.sections.map { it.title })
        assertEquals(listOf("Album", "Untyped"), artist.sections[0].items.map { it.title })
        // `single, live` must have one home only: Singles wins over the
        // secondary descriptor, and it must not leak into Albums.
        assertEquals(listOf("Single"), artist.sections[1].items.map { it.title })
        assertEquals(listOf("EP"), artist.sections[2].items.map { it.title })
        assertEquals("Related", artist.relatedArtists?.items?.single()?.title)
    }

    private class CatalogueTransport : NavidromeTransport {
        override suspend fun testConnection(config: NavidromeConfig) =
            NavidromeConnectionResult.Connected("navidrome", "test", true)

        override suspend fun request(
            config: NavidromeConfig,
            method: String,
            parameters: Map<String, String>,
        ): NavidromeApiResult<NavidromeResponse> {
            val song = NavidromeSong("song-1", "Song", artist = "Artist", track = 1)
            val thirdTrack = NavidromeSong("song-3", "Third", artist = "Artist", track = 3)
            val album = NavidromeAlbum(
                "album-1",
                name = parameters["type"] ?: "Album",
                artist = "Artist",
                artistId = "artist-1",
                year = 2024,
                song = listOf(song, thirdTrack),
            )
            return NavidromeApiResult.Success(when (method) {
                "getAlbumList2" -> NavidromeResponse("ok", albumList2 = NavidromeAlbumList(listOf(album)))
                "getStarred2" -> NavidromeResponse("ok", starred2 = NavidromeStarred(song = listOf(song), album = listOf(album)))
                "getPlaylists" -> NavidromeResponse("ok", playlists = NavidromePlaylistList(listOf(NavidromePlaylist("playlist-1", "Mix"))))
                "getArtists" -> NavidromeResponse("ok", artists = NavidromeArtistIndexes(index = listOf(NavidromeArtistIndex("A", listOf(NavidromeArtist("artist-1", "Artist"))))))
                "getArtist" -> NavidromeResponse(
                    "ok",
                    artist = NavidromeArtist(
                        "artist-1",
                        "Artist",
                        album = listOf(
                            NavidromeAlbum("album", name = "Album", releaseTypes = listOf("album")),
                            NavidromeAlbum("single", name = "Single", releaseTypes = listOf("single", "live")),
                            NavidromeAlbum("ep", name = "EP", releaseTypes = listOf("ep")),
                            NavidromeAlbum("untyped", name = "Untyped"),
                        ),
                    ),
                )
                "getArtistInfo2" -> NavidromeResponse(
                    "ok",
                    artistInfo2 = NavidromeArtistInfo(
                        similarArtist = listOf(NavidromeArtist("related", "Related")),
                    ),
                )
                "getAlbumInfo2" -> NavidromeResponse(
                    "ok",
                    albumInfo = NavidromeAlbumInfo(notes = "Server album notes"),
                )
                "getGenres" -> NavidromeResponse("ok", genres = NavidromeGenreList(listOf(NavidromeGenre("Rock", 1, 1))))
                "search3" -> NavidromeResponse("ok", searchResult3 = NavidromeSearchResult(song = listOf(song), album = listOf(album)))
                "getAlbum" -> NavidromeResponse("ok", album = album)
                "getPlaylist" -> NavidromeResponse("ok", playlist = NavidromePlaylist("playlist-1", "Mix", entry = listOf(song)))
                else -> error("Unexpected method $method")
            })
        }
    }
}
