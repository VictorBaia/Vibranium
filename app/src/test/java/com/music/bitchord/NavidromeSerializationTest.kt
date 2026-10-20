package com.music.bitchord

import com.music.bitchord.data.navidrome.NavidromeEnvelope
import com.music.bitchord.data.navidrome.NavidromeJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeSerializationTest {

    @Test
    fun `documented user response accepts textual permissions`() {
        val envelope = NavidromeJson.parser.decodeFromString<NavidromeEnvelope>(
            """{"subsonic-response":{"status":"ok","version":"1.16.1","unknown":"kept-compatible","user":{"username":"listener","adminRole":"false","streamRole":"true"}}}""",
        )

        assertEquals("listener", envelope.response.user?.username)
        assertFalse(envelope.response.user?.adminRole ?: true)
        assertTrue(envelope.response.user?.streamRole == true)
    }

    @Test
    fun `playlist response accepts native boolean permissions`() {
        val envelope = NavidromeJson.parser.decodeFromString<NavidromeEnvelope>(
            """{"subsonic-response":{"status":"ok","playlists":{"playlist":[{"id":"1","name":"Mix","public":false}]}}}""",
        )

        assertFalse(envelope.response.playlists?.playlist?.single()?.isPublic ?: true)
    }

    @Test
    fun `catalogue collections deserialize from documented wrappers`() {
        val envelope = NavidromeJson.parser.decodeFromString<NavidromeEnvelope>(
            """{"subsonic-response":{"status":"ok","starred2":{"song":[{"id":"s1","title":"Favourite"}]},"songsByGenre":{"song":[{"id":"s2","title":"Rock song"}]}}}""",
        )

        assertEquals("Favourite", envelope.response.starred2?.song?.single()?.title)
        assertEquals("Rock song", envelope.response.songsByGenre?.song?.single()?.title)
    }

    @Test
    fun `OpenSubsonic song explicit status is retained`() {
        val envelope = NavidromeJson.parser.decodeFromString<NavidromeEnvelope>(
            """{"subsonic-response":{"status":"ok","song":{"id":"s1","title":"Explicit song","explicitStatus":"explicit"}}}""",
        )

        assertEquals("explicit", envelope.response.song?.explicitStatus)
    }

    @Test
    fun `OpenSubsonic structured lyrics and random songs deserialize`() {
        val envelope = NavidromeJson.parser.decodeFromString<NavidromeEnvelope>(
            """{"subsonic-response":{"status":"ok","randomSongs":{"song":[{"id":"s3","title":"Random"}]},"lyricsList":{"structuredLyrics":[{"synced":true,"offset":-100,"line":[{"start":1000,"value":"A line"}]}]}}}""",
        )

        assertEquals("Random", envelope.response.randomSongs?.song?.single()?.title)
        val lyrics = envelope.response.lyricsList?.structuredLyrics?.single()
        assertTrue(lyrics?.synced == true)
        assertEquals(-100, lyrics?.offset)
        assertEquals("A line", lyrics?.line?.single()?.value)
    }

    @Test
    fun `optional artist enrichment deserializes without changing the base artist`() {
        val envelope = NavidromeJson.parser.decodeFromString<NavidromeEnvelope>(
            """{"subsonic-response":{"status":"ok","artist":{"id":"artist-1","name":"Artist"},"artistInfo2":{"biography":"Server bio","largeImageUrl":"https://images.example.test/artist.jpg"}}}""",
        )

        assertEquals("Artist", envelope.response.artist?.name)
        assertEquals("Server bio", envelope.response.artistInfo2?.biography)
    }
}

