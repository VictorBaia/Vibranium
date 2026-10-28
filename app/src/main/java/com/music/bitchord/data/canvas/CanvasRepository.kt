package com.music.bitchord.data.canvas

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.navidrome.NavidromeApiResult
import com.music.bitchord.data.navidrome.NavidromeEntity
import com.music.bitchord.data.navidrome.NavidromeIds
import com.music.bitchord.data.navidrome.NavidromeRepository
import com.music.bitchord.data.navidrome.NavidromeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Apple Music motion artwork, matched by song title and album artist only. */
object CanvasRepository {
    private const val TAG = "CanvasRepository"
    private const val CACHE_SIZE = 64
    private val cache = object : LinkedHashMap<String, CanvasArtwork>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CanvasArtwork>) =
            size > CACHE_SIZE
    }
    private val albumArtistCache = ConcurrentHashMap<String, String>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    // A release-wide clip must never bypass the song title check.
    fun rememberAlbum(browseId: String, artwork: CanvasArtwork) = Unit

    suspend fun canvasFor(song: Song): CanvasArtwork? {
        if (song.videoId.isBlank() && (song.localUri != null || song.localPath != null)) return null
        val title = song.title.cleaned()
        val artist = albumArtist(song)?.cleaned().orEmpty()
        if (title.isBlank() || artist.isBlank()) return null
        val key = "apple|song|${title.normalizeForMatch()}|${artist.normalizeForMatch()}"
        return resolve(key, { it.matches(title, artist, null) }) {
            runCatching { AppleMusicCanvas.search(title, artist, null) }
                .onFailure { Log.d(TAG, "Apple Music lookup failed: ${it.message}") }
                .getOrNull()
                ?.takeIf { it.matches(title, artist, null) }
        }
    }

    fun cached(song: Song): CanvasArtwork? {
        val title = song.title.cleaned()
        val artist = song.albumArtist?.cleaned()
            ?: song.albumId?.let { albumArtistCache[albumCacheKey(it)] }?.cleaned()
            ?: return null
        val key = "apple|song|${title.normalizeForMatch()}|${artist.normalizeForMatch()}"
        return synchronized(cache) { cache[key] }?.takeIf { it.matches(title, artist, null) }
    }

    suspend fun canvasForAlbum(album: String, artist: String): CanvasArtwork? {
        val name = album.cleaned()
        val credit = artist.cleaned()
        if (name.isBlank() || credit.isBlank()) return null
        return resolve("apple|album|${name.normalizeForMatch()}|${credit.normalizeForMatch()}") {
            runCatching { AppleMusicCanvas.searchAlbum(name, credit) }
                .onFailure { Log.d(TAG, "Apple Music album lookup failed: ${it.message}") }
                .getOrNull()
                ?.takeIf { it.matches(name, credit, name) }
        }
    }

    private suspend fun albumArtist(song: Song): String? {
        song.albumArtist?.takeIf(String::isNotBlank)?.let { return it }
        val albumId = song.albumId ?: return null
        val cacheKey = albumCacheKey(albumId)
        albumArtistCache[cacheKey]?.let { return it }
        val parsed = NavidromeIds.parse(albumId)
            ?.takeIf { it.entity == NavidromeEntity.ALBUM } ?: return null
        val config = (NavidromeStore.state.value as? NavidromeStore.State.Configured)?.config
            ?: return null
        val result = withContext(Dispatchers.IO) {
            NavidromeRepository({ config }).album(parsed.rawId)
        }
        val artist = (result as? NavidromeApiResult.Success)?.value?.artist
            ?.takeIf(String::isNotBlank) ?: return null
        albumArtistCache[cacheKey] = artist
        return artist
    }

    private fun albumCacheKey(albumId: String): String {
        val account = (NavidromeStore.state.value as? NavidromeStore.State.Configured)
            ?.config?.accountKey.orEmpty()
        return "$account|$albumId"
    }

    private suspend fun resolve(
        key: String,
        acceptCached: (CanvasArtwork) -> Boolean = { true },
        lookUp: suspend () -> CanvasArtwork?,
    ): CanvasArtwork? = locks.computeIfAbsent(key) { Mutex() }.withLock {
        synchronized(cache) { cache[key] }?.takeIf(acceptCached)?.let { return@withLock it }
        val found = withContext(Dispatchers.IO) { lookUp() }
        if (found != null) synchronized(cache) { cache[key] = found }
        found
    }

    private fun String.cleaned(): String = replace(NOISE, " ")
        .substringBefore(" | ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val NOISE = Regex(
        """\((?:from|official|lyrical|video|audio)[^)]*\)|\[[^]]*]|""" +
            """\b(?:official (?:video|audio|music video)|lyrical|full song|4k video)\b""",
        RegexOption.IGNORE_CASE,
    )
}
