package com.music.bitchord.data.smb

import coil3.ImageLoader
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.music.bitchord.data.artwork.PersistentArtworkCache
import okio.FileSystem
import okio.buffer
import okio.source

/**
 * Covers off the share for Coil. Reads the whole picture — covers are
 * kilobytes, and listings already cap what can be one — then hands Coil
 * bytes it decodes and caches exactly like any network image.
 *
 * Registered in [BitChordApplication][com.music.bitchord.BitChordApplication]
 * alongside the defaults: anything that isn't `smb://` falls straight
 * through to them.
 */
class SmbCoverFetcher private constructor(
    private val url: String,
    private val diskCache: coil3.disk.DiskCache?,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val cacheKey = PersistentArtworkCache.key("smb-artwork", url)
        PersistentArtworkCache.open(diskCache, cacheKey)?.let { return it }
        val bytes = SmbClient.readFully(url)
        val mime = when (url.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> null
        }
        PersistentArtworkCache.store(diskCache, cacheKey, bytes, mime)?.let { return it }
        return SourceFetchResult(
            source = ImageSource(bytes.inputStream().source().buffer(), FileSystem.SYSTEM),
            mimeType = mime,
            dataSource = coil3.decode.DataSource.NETWORK,
        )
    }

    class Factory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (SmbConfig.isSmbUrl(data)) SmbCoverFetcher(data, imageLoader.diskCache) else null
    }
}
