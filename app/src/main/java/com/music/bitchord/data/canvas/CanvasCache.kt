package com.music.bitchord.data.canvas

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.music.bitchord.data.Http
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.cache.CacheUsageEntry
import com.music.bitchord.playback.DynamicLruCacheEvictor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Disk cache for canvas clips — the looping video some releases publish
 * alongside a track, played over the cover art by
 * [CanvasArtworkPlayer][com.music.bitchord.ui.player.CanvasArtworkPlayer].
 *
 * Without this, that player fetched a clip straight off the network through
 * a bare OkHttp data source, repeating for as long as the track playing over
 * it does ([androidx.media3.common.Player.REPEAT_MODE_ONE]). ExoPlayer frees
 * a sample's buffer as soon as the renderer has consumed it — there is no
 * back buffer configured, nor should there be one sized for an entire clip —
 * so once a loop finishes, nothing of it is left in memory to loop back to,
 * and reaching position zero again means the data source is asked for those
 * bytes again. A five-second clip behind a four-minute track loops around
 * fifty times, which without this cache was fifty downloads of the same few
 * seconds of video rather than one: the "40MB for one song" and multi-
 * gigabyte-day reports trace to exactly this repeat, not to audio bitrate.
 *
 * Wrapping the upstream in [CacheDataSource] means only the first loop of a
 * clip ever reaches the network; every loop after it, and every replay of
 * the same track later in the session, is served from disk instead.
 */
@UnstableApi
object CanvasCache {

    @Volatile private lateinit var cache: SimpleCache
    private val evictor = DynamicLruCacheEvictor(AppSettings.DEFAULT_VIDEO_CACHE_LIMIT_BYTES)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefetchLocks = Array(64) { Mutex() }

    /** Opened once per process, alongside [com.music.bitchord.playback.AudioCache.init]. */
    fun init(context: Context) {
        if (::cache.isInitialized) return
        evictor.maxBytes = AppSettings.videoCacheLimitBytes.value
        cache = SimpleCache(
            File(context.cacheDir, "canvas"),
            evictor,
            StandaloneDatabaseProvider(context),
        )
        // The ceiling is mutable so a settings change applies to the already
        // opened cache and to every later canvas/video write without interrupting
        // a player that currently holds a CacheDataSource reference.
        scope.launch {
            AppSettings.videoCacheLimitBytes.collect { maxBytes ->
                evictor.maxBytes = maxBytes
                evictor.applyNow(cache)
            }
        }
    }

    /**
     * [upstream] wrapped so a clip already on disk never touches the
     * network again — see the class doc for why that is the whole point.
     * A cache write that fails (full disk, evicted mid-write) drops back to
     * plain streaming rather than surfacing as a playback error, the same
     * choice [com.music.bitchord.playback.AudioCache] makes for audio.
     */
    fun dataSourceFactory(upstream: DataSource.Factory): DataSource.Factory =
        if (::cache.isInitialized) {
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            // The video cache is opened off the UI thread during startup. If a
            // player is created before that finishes, stream the clip directly
            // instead of blocking launch or dereferencing an unopened cache.
            upstream
        }

    /** Cache the first video bytes while the mini player is visible. */
    suspend fun prefetchStart(url: String) = withContext(Dispatchers.IO) {
        if (!::cache.isInitialized || url.isBlank()) return@withContext
        prefetchLocks[(url.hashCode() and Int.MAX_VALUE) % prefetchLocks.size].withLock {
            // The mini player and the next-track lookahead may discover the
            // same clip together. A single writer avoids duplicate downloads
            // and two cache holes competing with the actual video player.
            if (cache.getCachedLength(url, 0, 1) > 0) return@withLock
            val source = dataSourceFactory(OkHttpDataSource.Factory(Http.client)).createDataSource()
            try {
                source.open(DataSpec.Builder().setUri(url).build())
                val buffer = ByteArray(16 * 1024)
                var remaining = 256 * 1024
                while (remaining > 0) {
                    val read = source.read(buffer, 0, minOf(buffer.size, remaining))
                    if (read < 0) break
                    remaining -= read
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed prefetch never blocks playback; the player can retry.
            } finally {
                runCatching { source.close() }
            }
        }
    }

    /** Removes every cached animated artwork clip. */
    fun clear() {
        if (!::cache.isInitialized) return
        cache.keys.toList().forEach { key -> runCatching { cache.removeResource(key) } }
    }

    /** Every cached canvas, artist video and animated album cover on disk. */
    suspend fun storageEntries(): List<CacheUsageEntry> = withContext(Dispatchers.IO) {
        if (!::cache.isInitialized) return@withContext emptyList()
        cache.keys.mapNotNull { key ->
            val bytes = cache.getCachedSpans(key)
                .filter { it.isCached && it.file?.exists() == true }
                .sumOf { it.length }
            if (bytes <= 0L) null else CacheUsageEntry(key.substringAfterLast('/').ifBlank { key }, bytes)
        }.sortedByDescending { it.bytes }
    }
}
