package com.music.bitchord

import com.music.bitchord.data.navidrome.NavidromeAuth
import com.music.bitchord.data.navidrome.NavidromeConfig
import com.music.bitchord.data.navidrome.NavidromeIds
import com.music.bitchord.data.navidrome.NavidromeMediaResolver
import com.music.bitchord.data.navidrome.NavidromeTranscodeProfile
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.hasYouTubeOriginal
import com.music.bitchord.playback.mediaIdIn
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.playback.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavidromeMediaResolverTest {
    @Test
    fun `Navidrome queue item persists only its internal playback URI`() {
        val item = Song("nd:song-1", "Song", "Artist", null).toMediaItem()
        val uri = requireNotNull(item.localConfiguration?.uri)

        assertTrue(uri.toString().startsWith("bitchord://navidrome/song-1?request="))
        assertEquals("nd:song-1", mediaIdIn(uri))
        assertFalse(uri.toString().contains("api-secret"))
        assertFalse(item.toSongForTest().hasYouTubeOriginal())
    }

    @Test
    fun `original requests raw while AAC targets request format and bitrate`() {
        val config = config(NavidromeAuth.apiKey("api-secret"))
        val lossless = NavidromeMediaResolver.streamUrl(config, "song-1", NavidromeTranscodeProfile.ORIGINAL)
        val low = NavidromeMediaResolver.streamUrl(config, "song-1", NavidromeTranscodeProfile.AAC_128)

        assertEquals("raw", lossless.queryParameter("format"))
        assertNull(lossless.queryParameter("maxBitRate"))
        assertEquals("128", low.queryParameter("maxBitRate"))
        assertEquals("aac", low.queryParameter("format"))
    }

    @Test
    fun `password stream URL contains a fresh token but never the password`() {
        val config = config(NavidromeAuth.password("plain-secret"))
        val first = NavidromeMediaResolver.streamUrl(config, "song-1", NavidromeTranscodeProfile.MP3_320)
        val second = NavidromeMediaResolver.streamUrl(config, "song-1", NavidromeTranscodeProfile.MP3_320)

        assertFalse(first.toString().contains("plain-secret"))
        assertTrue(first.queryParameter("t")?.isNotBlank() == true)
        assertTrue(first.queryParameter("s")?.isNotBlank() == true)
        assertNotEquals(first.queryParameter("s"), second.queryParameter("s"))
    }

    @Test
    fun `download quality selects a compatible container without exposing credentials`() {
        val config = config(NavidromeAuth.password("plain-secret"))
        val standard = NavidromeMediaResolver.downloadUrl(config, "song-1", NavidromeTranscodeProfile.MP3_128)
        val lossless = NavidromeMediaResolver.downloadUrl(config, "song-1", NavidromeTranscodeProfile.ORIGINAL)

        assertEquals("128", standard.queryParameter("maxBitRate"))
        assertEquals("mp3", standard.queryParameter("format"))
        assertNull(lossless.queryParameter("maxBitRate"))
        assertEquals("raw", lossless.queryParameter("format"))
        assertFalse(standard.toString().contains("plain-secret"))
        assertNotEquals(standard.queryParameter("s"), lossless.queryParameter("s"))
    }

    @Test
    fun `Navidrome download formats remain source specific`() {
        assertEquals(
            "flac",
            NavidromeMediaResolver.downloadFormat(NavidromeTranscodeProfile.ORIGINAL, "flac", null)?.extension,
        )
        assertEquals(
            "ogg",
            NavidromeMediaResolver.downloadFormat(NavidromeTranscodeProfile.ORIGINAL, "opus", null)?.extension,
        )
        assertEquals(
            "mp3",
            NavidromeMediaResolver.downloadFormat(NavidromeTranscodeProfile.MP3_320, "flac", "audio/flac")?.extension,
        )
    }

    @Test
    fun `artwork resolution rejects another account namespace`() {
        val config = config(NavidromeAuth.apiKey("api-secret"))
        val foreign = NavidromeIds.artworkUri("cover-1", "account-b")

        assertTrue(runCatching { NavidromeMediaResolver.artworkUrl(config, foreign) }.isFailure)
    }

    @Test
    fun `native artwork request explicitly asks Navidrome for original size`() {
        val config = config(NavidromeAuth.apiKey("api-secret"))

        val url = NavidromeMediaResolver.artworkUrl(
            config,
            NavidromeIds.artworkUri("cover-1", "account-a") + "&size=0",
        )

        assertEquals("cover-1", url.queryParameter("id"))
        assertEquals("0", url.queryParameter("size"))
    }

    @Test
    fun `Media3 round trip preserves original artwork source for app player`() {
        val source = NavidromeIds.artworkUri("cover-1", "account-a")
        val song = Song("nd:song-1", "Song", "Artist", source)

        val restored = song.toMediaItem().toSong()

        assertEquals(source, restored.thumbnailUrl)
        assertTrue(restored.thumbnailUrl?.startsWith("bitchord://navidrome-artwork/") == true)
    }

    private fun config(credential: com.music.bitchord.data.navidrome.NavidromeCredential) =
        NavidromeConfig.create(
            "https://music.example.test",
            "listener",
            credential,
            accountKey = "account-a",
        )

    private fun androidx.media3.common.MediaItem.toSongForTest() = Song(
        videoId = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        thumbnailUrl = mediaMetadata.artworkUri?.toString(),
    )
}
