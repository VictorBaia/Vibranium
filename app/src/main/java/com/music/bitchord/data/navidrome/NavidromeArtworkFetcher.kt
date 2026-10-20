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

internal class NavidromeArtworkFetcher private constructor(
    private val internalUri: String,
    private val diskCache: coil3.disk.DiskCache?,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val cacheKey = PersistentArtworkCache.key("navidrome-artwork", internalUri)
        PersistentArtworkCache.open(diskCache, cacheKey)?.let { return it }
        val config = (NavidromeStore.state.value as? NavidromeStore.State.Configured)?.config
            ?: throw IOException("Navidrome is not configured")
        val url = NavidromeMediaResolver.artworkUrl(config, internalUri)
        val request = Request.Builder().url(url).get().build()
        val (bytes, mimeType) = Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Navidrome artwork request failed (${response.code})")
            val body = response.body ?: throw IOException("Navidrome artwork response was empty")
            if (body.contentLength() > MAX_ARTWORK_BYTES) throw IOException("Navidrome artwork is too large")
            val bytes = body.byteStream().use { input ->
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
                output.toByteArray()
            }
            bytes to body.contentType()?.toString()
        }
        PersistentArtworkCache.store(diskCache, cacheKey, bytes, mimeType)?.let { return it }
        return SourceFetchResult(
            source = ImageSource(bytes.inputStream().source().buffer(), FileSystem.SYSTEM),
            mimeType = mimeType,
            dataSource = DataSource.NETWORK,
        )
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
    }
}
