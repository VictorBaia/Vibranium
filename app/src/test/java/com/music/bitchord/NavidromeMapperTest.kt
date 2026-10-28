package com.music.bitchord

import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.navidrome.NavidromeAlbum
import com.music.bitchord.data.navidrome.NavidromeArtist
import com.music.bitchord.data.navidrome.NavidromeMapper
import com.music.bitchord.data.navidrome.NavidromePlaylist
import com.music.bitchord.data.navidrome.NavidromeSearchResult
import com.music.bitchord.data.navidrome.NavidromeSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeMapperTest {
    @Test
    fun `song maps into existing model with isolated identities`() {
        val mapped = NavidromeMapper.song(
            NavidromeSong(
                id = "song-1",
                title = "Track",
                artist = "Artist",
                artistId = "artist-1",
                album = "Album",
                albumId = "album-1",
                coverArt = "cover/1",
                duration = 3670,
                suffix = "flac",
                isExplicit = true,
            ),
        )

        assertEquals("nd:song-1", mapped.videoId)
        assertEquals("nd:artist:artist-1", mapped.artistId)
        assertEquals("nd:album:album-1", mapped.albumId)
        assertEquals("bitchord://navidrome-artwork/cover%2F1", mapped.thumbnailUrl)
        assertEquals("1:01:10", mapped.durationText)
        assertEquals("LOSSLESS", mapped.sourceQuality)
        assertFalse(mapped.isVideo)
        assertFalse(mapped.isVideoOrigin)
        assertTrue(mapped.isExplicit == true)
    }

    @Test
    fun `search respects the selected existing filter`() {
        val source = NavidromeSearchResult(
            artist = listOf(NavidromeArtist("ar", "Artist", albumCount = 1)),
            album = listOf(NavidromeAlbum("al", name = "Album")),
            song = listOf(NavidromeSong("so", "Song")),
        )

        val all = NavidromeMapper.search(source, SearchFilter.ALL)
        assertEquals(3, all.size)
        assertTrue(all.filterIsInstance<SearchResult.Track>().single().song.videoId.startsWith("nd:"))
        assertEquals(BrowseType.ARTIST, (all[1] as SearchResult.Browse).item.type)
        assertEquals(BrowseType.ALBUM, (all[2] as SearchResult.Browse).item.type)
        assertEquals(1, NavidromeMapper.search(source, SearchFilter.SONGS).size)
        assertTrue(NavidromeMapper.search(source, SearchFilter.VIDEOS).isEmpty())
        assertTrue(NavidromeMapper.search(source, SearchFilter.PLAYLISTS).isEmpty())
    }

    @Test
    fun `artist browse items do not expose release counts as subtitles`() {
        val artist = NavidromeMapper.artist(NavidromeArtist("ar", "Artist", albumCount = 8))

        assertEquals("nd:artist:ar", artist.browseId)
        assertEquals("", artist.subtitle)
    }

    @Test
    fun `Navidrome OpenSubsonic explicit status maps to the song badge state`() {
        val explicit = NavidromeMapper.song(
            NavidromeSong(id = "explicit", title = "Uncensored", explicitStatus = "explicit"),
        )
        val clean = NavidromeMapper.song(
            NavidromeSong(id = "clean", title = "Radio edit", explicitStatus = "clean"),
        )

        assertTrue(explicit.isExplicit == true)
        assertTrue(clean.isExplicit == false)
    }

    @Test
    fun `playlist uses explicit Navidrome browse identity`() {
        val mapped = NavidromeMapper.userPlaylist(NavidromePlaylist("playlist-1", "Mix"))

        assertEquals("playlist-1", mapped.playlistId)
        assertEquals("nd:playlist:playlist-1", mapped.browseId)
    }
}
