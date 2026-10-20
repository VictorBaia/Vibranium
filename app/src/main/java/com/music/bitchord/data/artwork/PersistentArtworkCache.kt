package com.music.bitchord.data.artwork

import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult

/**
 * Persistent source-byte cache for image fetchers that cannot use Coil's HTTP
 * fetcher (authenticated Navidrome cover URIs and SMB files). Coil's normal
 * network fetcher already owns its disk cache; custom fetchers must do this
 * explicitly or they silently download the same cover on every new screen.
 */
internal object PersistentArtworkCache {

    fun open(cache: DiskCache?, key: String): SourceFetchResult? {
        val snapshot = cache?.openSnapshot(key) ?: return null
        return runCatching {
            SourceFetchResult(
                source = ImageSource(
                    file = snapshot.data,
                    fileSystem = cache.fileSystem,
                    closeable = snapshot,
                ),
                mimeType = cache.fileSystem.read(snapshot.metadata) { readUtf8().ifBlank { null } },
                dataSource = DataSource.DISK,
            )
        }.getOrElse {
            snapshot.close()
            null
        }
    }

    fun store(
        cache: DiskCache?,
        key: String,
        bytes: ByteArray,
        mimeType: String?,
    ): SourceFetchResult? {
        val editor = cache?.openEditor(key) ?: return null
        return try {
            cache.fileSystem.write(editor.metadata) { writeUtf8(mimeType.orEmpty()) }
            cache.fileSystem.write(editor.data) { write(bytes) }
            val snapshot = editor.commitAndOpenSnapshot() ?: return null
            SourceFetchResult(
                source = ImageSource(
                    file = snapshot.data,
                    fileSystem = cache.fileSystem,
                    closeable = snapshot,
                ),
                mimeType = mimeType,
                dataSource = DataSource.NETWORK,
            )
        } catch (error: Throwable) {
            editor.abort()
            null
        }
    }

    fun copyTo(cache: DiskCache?, key: String, output: java.io.OutputStream): Boolean {
        val snapshot = cache?.openSnapshot(key) ?: return false
        return try {
            // Okio's BufferedSource has no OutputStream copy helper. Reading
            // through FileSystem also keeps this independent of platform-only
            // java.io file access used by Coil's disk cache implementation.
            cache.fileSystem.read(snapshot.data) {
                output.write(readByteArray())
            }
            true
        } catch (_: Throwable) {
            false
        } finally {
            snapshot.close()
        }
    }

    fun key(namespace: String, identity: String): String = "$namespace:$identity"
}
