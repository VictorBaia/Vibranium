package com.music.bitchord.data.navidrome

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.Http
import com.music.bitchord.data.artwork.PersistentArtworkCache
import coil3.SingletonImageLoader
import okhttp3.Request
import java.io.FileNotFoundException
import kotlin.concurrent.thread

/** Makes authenticated cover art available to the system media notification without leaking URLs. */
class NavidromeArtworkProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/*"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Artwork is read-only")
        val coverId = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: throw FileNotFoundException("Missing cover art")
        val size = uri.getQueryParameter("size")?.toIntOrNull()?.takeIf { it in MIN_ARTWORK_SIZE..MAX_ARTWORK_SIZE }
        val pipe = ParcelFileDescriptor.createPipe()
        thread(name = "navidrome-notification-art") {
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                runCatching {
                    val config = (NavidromeStore.state.value as? NavidromeStore.State.Configured)?.config
                        ?: throw FileNotFoundException("Navidrome is not configured")
                    val internalUri = NavidromeIds.artworkUri(coverId, config.accountKey).let {
                        if (size == null) it else "$it${if ('?' in it) '&' else '?'}size=$size"
                    }
                    val diskCache = context?.let { SingletonImageLoader.get(it).diskCache }
                    val cacheKey = PersistentArtworkCache.key("navidrome-artwork", internalUri)
                    if (PersistentArtworkCache.copyTo(diskCache, cacheKey, output)) return@runCatching
                    Http.client.newCall(Request.Builder()
                        .url(NavidromeMediaResolver.artworkUrl(config, internalUri))
                        .build()).execute().use { response ->
                        if (!response.isSuccessful) throw FileNotFoundException("Cover request failed")
                        val body = response.body ?: throw FileNotFoundException("Cover response was empty")
                        val bytes = body.bytes()
                        PersistentArtworkCache.store(diskCache, cacheKey, bytes, body.contentType()?.toString())
                        output.write(bytes)
                    }
                }
            }
        }
        return pipe[0]
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        fun uri(coverId: String, size: Int? = null): Uri = Uri.Builder().scheme("content")
            .authority("${BuildConfig.APPLICATION_ID}.navidrome-artwork")
            .appendPath(coverId)
            .apply { size?.takeIf { it in MIN_ARTWORK_SIZE..MAX_ARTWORK_SIZE }?.let { appendQueryParameter("size", it.toString()) } }
            .build()

        /**
         * Restores the app's original authenticated artwork identity from the
         * content URI exposed to Media3/system surfaces. The content URI may
         * deliberately carry a bounded notification size; app UI must not
         * inherit that bound when a queue item round-trips through Media3.
         */
        fun sourceUri(uri: Uri?): String? {
            if (uri?.scheme != "content" ||
                uri.authority != "${BuildConfig.APPLICATION_ID}.navidrome-artwork"
            ) return null
            val coverId = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
            return NavidromeArtwork.uri(coverId)
        }

        private const val MIN_ARTWORK_SIZE = 64
        private const val MAX_ARTWORK_SIZE = 1_200
    }
}
