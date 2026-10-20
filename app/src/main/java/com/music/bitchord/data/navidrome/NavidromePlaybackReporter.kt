package com.music.bitchord.data.navidrome

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/** Account-scoped OpenSubsonic playback reports without using title matching. */
internal class NavidromePlaybackReporter(private val scope: CoroutineScope) {
    private var songId: String? = null
    private var scrobbled = false
    private var nowPlayingSent = false
    private var job: Job? = null

    fun onTrackChanged(song: Song?, durationMs: Long?, isPlaying: Boolean) {
        job?.cancel()
        songId = NavidromeIds.parse(song?.videoId.orEmpty())
            ?.takeIf { it.entity == NavidromeEntity.SONG }?.rawId
        scrobbled = false
        nowPlayingSent = false
        if (songId == null) return
        if (isPlaying) schedule(durationMs)
    }

    fun onPlayingChanged(isPlaying: Boolean, song: Song?, durationMs: Long?) {
        if (!isPlaying) {
            job?.cancel()
            job = null
            return
        }
        val currentId = NavidromeIds.parse(song?.videoId.orEmpty())
            ?.takeIf { it.entity == NavidromeEntity.SONG }?.rawId
        if (currentId != songId) onTrackChanged(song, durationMs, isPlaying = true)
        if (isPlaying && songId != null && job == null && !scrobbled) {
            schedule(durationMs)
        }
        if (songId == null || nowPlayingSent) return
        nowPlayingSent = true
        scope.launch { submit(false) }
    }

    fun stop() {
        job?.cancel()
        job = null
        songId = null
        scrobbled = false
        nowPlayingSent = false
    }

    private suspend fun submit(submission: Boolean) {
        val id = songId ?: return
        if (submission && scrobbled) return
        if (submission) scrobbled = true
        NavidromeRepository(config = {
            (NavidromeStore.state.value as? NavidromeStore.State.Configured)?.config
        }).scrobble(id, submission)
    }

    private fun schedule(durationMs: Long?) {
        val threshold = durationMs?.takeIf { it > 0L }?.let { min(it / 2L, MAX_SCROBBLE_DELAY_MS) } ?: return
        job = scope.launch {
            delay(threshold)
            submit(true)
        }
    }

    private companion object {
        const val MAX_SCROBBLE_DELAY_MS = 240_000L
    }
}
