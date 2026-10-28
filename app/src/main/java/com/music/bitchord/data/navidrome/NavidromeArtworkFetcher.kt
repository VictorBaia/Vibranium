package com.music.bitchord.data.navidrome

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.music.bitchord.data.Http
import com.music.bitchord.data.artwork.PersistentArtworkCache
import okhttp3.Request
import okio.FileSystem
import okio.buffer
import okio.source
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class NavidromeArtworkFetcher private constructor(
    private val internalUri: String,
    private val diskCache: coil3.disk.DiskCache?,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val cacheKey = PersistentArtworkCache.key("navidrome-artwork", internalUri)
        PersistentArtworkCache.open(diskCache, cacheKey)?.let { return it }
        return locks[(cacheKey.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            // The cover, palette and blurred mirror can arrive together. Only
            // one of them should download the source; the rest read the bytes
            // committed to Coil's disk cache by the first request.
            PersistentArtworkCache.open(diskCache, cacheKey)?.let { return@withLock it }
            fetchAndStore(cacheKey)
        }
    }

    private fun fetchAndStore(cacheKey: String): FetchResult {
        val config = (NavidromeStore.state.value as? NavidromeStore.State.Configured)?.config
            ?: throw IOException("Navidrome is not configured")
        val url = NavidromeMediaResolver.artworkUrl(config, internalUri)
        val request = Request.Builder().url(url).get().build()
        return Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Navidrome artwork request failed (${response.code})")
            val body = response.body ?: throw IOException("Navidrome artwork response was empty")
            if (body.contentLength() > MAX_ARTWORK_BYTES) throw IOException("Navidrome artwork is too large")
            val mimeType = body.contentType()?.toString()
            body.byteStream().use inputUse@{ input ->
                diskCache?.let { cache ->
                    return@inputUse PersistentArtworkCache.storeStream(
                        cache, cacheKey, input, MAX_ARTWORK_BYTES, mimeType,
                    )
                }
                // The application's loader always has a disk cache. Keep a
                // fallback for an alternate loader with no cache configured.
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_ARTWORK_BYTES) throw IOException("Navidrome artwork is too large")
                    output.write(buffer, 0, read)
                }
                SourceFetchResult(
                    source = ImageSource(output.toByteArray().inputStream().source().buffer(), FileSystem.SYSTEM),
                    mimeType = mimeType,
                    dataSource = DataSource.NETWORK,
                )
            }
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        // Coil maps String request data to its own cross-platform Uri before
        // asking fetcher factories. android.net.Uri and String factories are
        // therefore never considered for an AsyncImage string request.
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            data.toString().takeIf { NavidromeIds.coverArtIdFromUri(it) != null }
                ?.let { NavidromeArtworkFetcher(it, imageLoader.diskCache) }
    }

    private companion object {
        const val MAX_ARTWORK_BYTES = 25L * 1024 * 1024
        private val locks = Array(64) { Mutex() }
    }
}
