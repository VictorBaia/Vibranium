package com.music.bitchord

import com.music.bitchord.data.navidrome.NavidromeEntity
import com.music.bitchord.data.navidrome.NavidromeId
import com.music.bitchord.data.navidrome.NavidromeIds
import com.music.bitchord.data.navidrome.NavidromeQueueIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavidromeIdsTest {

    @Test
    fun `entity IDs round trip without colliding`() {
        assertEquals(NavidromeId(NavidromeEntity.SONG, "42"), NavidromeIds.parse(NavidromeIds.song("42")))
        assertEquals(NavidromeId(NavidromeEntity.ALBUM, "42"), NavidromeIds.parse(NavidromeIds.album("42")))
        assertEquals(NavidromeId(NavidromeEntity.ARTIST, "42"), NavidromeIds.parse(NavidromeIds.artist("42")))
        assertEquals(NavidromeId(NavidromeEntity.PLAYLIST, "42"), NavidromeIds.parse(NavidromeIds.playlist("42")))
        assertEquals(NavidromeId(NavidromeEntity.GENRE, "42"), NavidromeIds.parse(NavidromeIds.genre("42")))
    }

    @Test
    fun `YouTube IDs are never parsed as Navidrome IDs`() {
        assertNull(NavidromeIds.parse("dQw4w9WgXcQ"))
        assertNull(NavidromeIds.parse(""))
    }

    @Test
    fun `playback URI safely carries opaque song IDs`() {
        val raw = "folder/song 7: versão"
        val uri = NavidromeIds.playbackUri(raw)

        assertEquals("bitchord://navidrome/folder%2Fsong%207%3A%20vers%C3%A3o", uri)
        assertEquals(raw, NavidromeIds.songIdFromPlaybackUri(uri))
        assertNull(NavidromeIds.songIdFromPlaybackUri("https://server/rest/stream?id=secret"))
    }

    @Test
    fun `playback request URI keeps the song ID internal and uses a fresh request identity`() {
        val first = NavidromeIds.playbackRequestUri("song-1", durationSeconds = null)
        val second = NavidromeIds.playbackRequestUri("song-1", durationSeconds = null)

        assertEquals("song-1", NavidromeIds.songIdFromPlaybackUri(first))
        assertEquals("song-1", NavidromeIds.songIdFromPlaybackUri(second))
        org.junit.Assert.assertNotEquals(first, second)
        org.junit.Assert.assertFalse(first.contains("token", ignoreCase = true))
        org.junit.Assert.assertFalse(first.contains("password", ignoreCase = true))
    }

    @Test
    fun `artwork URI safely carries opaque cover IDs`() {
        val raw = "album/cover 7"
        val uri = NavidromeIds.artworkUri(raw)

        assertEquals("bitchord://navidrome-artwork/album%2Fcover%207", uri)
        assertEquals(raw, NavidromeIds.coverArtIdFromUri(uri))
        assertNull(NavidromeIds.coverArtIdFromUri("https://server/rest/getCoverArt?id=secret"))
    }

    @Test
    fun `artwork URI carries only the non secret account namespace`() {
        val uri = NavidromeIds.artworkUri("cover-1", "account-a")

        assertEquals("cover-1", NavidromeIds.coverArtIdFromUri(uri))
        assertEquals("account-a", NavidromeIds.artworkAccountKeyFromUri(uri))
    }

    @Test
    fun `persisted Navidrome queue restores only for the same account namespace`() {
        val ids = listOf("nd:song-1", "nd:song-2")

        assertEquals("account-a", NavidromeQueueIdentity.accountFor(ids, "account-a"))
        org.junit.Assert.assertTrue(NavidromeQueueIdentity.canRestore(ids, "account-a", "account-a"))
        org.junit.Assert.assertFalse(NavidromeQueueIdentity.canRestore(ids, "account-a", "account-b"))
        org.junit.Assert.assertFalse(NavidromeQueueIdentity.canRestore(ids, null, "account-a"))
        org.junit.Assert.assertTrue(NavidromeQueueIdentity.canRestore(listOf("youtube-id"), null, null))
    }
}

