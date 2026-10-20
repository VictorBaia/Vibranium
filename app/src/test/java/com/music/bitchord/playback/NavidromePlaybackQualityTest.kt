package com.music.bitchord.playback

import com.music.bitchord.data.navidrome.NavidromeTranscodeProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class NavidromePlaybackQualityTest {
    @Test
    fun unopenedTrackUsesSettingsAtLoadNotAtQueueCreation() {
        val playing = NavidromePlaybackQuality()
        val upcoming = NavidromePlaybackQuality()

        assertEquals(
            NavidromeTranscodeProfile.AAC_192,
            playing.select(NavidromeTranscodeProfile.AAC_192, null),
        )
        assertEquals(
            NavidromeTranscodeProfile.ORIGINAL,
            upcoming.select(NavidromeTranscodeProfile.ORIGINAL, null),
        )
        assertEquals(
            NavidromeTranscodeProfile.AAC_192,
            playing.select(NavidromeTranscodeProfile.ORIGINAL, null),
        )
    }

    @Test
    fun preloadedTrackAndItsSeekKeepTheirRendition() {
        val upcoming = NavidromePlaybackQuality()

        assertEquals(
            NavidromeTranscodeProfile.AAC_320,
            upcoming.select(NavidromeTranscodeProfile.AAC_320, null),
        )
        assertEquals(
            NavidromeTranscodeProfile.AAC_320,
            upcoming.select(NavidromeTranscodeProfile.MP3_128, null),
        )
    }

    @Test
    fun existingCachedRenditionIsPreserved() {
        val track = NavidromePlaybackQuality()

        assertEquals(
            NavidromeTranscodeProfile.AAC_256,
            track.select(
                NavidromeTranscodeProfile.ORIGINAL,
                NavidromeTranscodeProfile.AAC_256,
            ),
        )
        assertEquals(
            NavidromeTranscodeProfile.AAC_256,
            track.select(NavidromeTranscodeProfile.MP3_320, null),
        )
    }
}
