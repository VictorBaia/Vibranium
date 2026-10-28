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

    /** Write source bytes directly to disk without holding two large arrays in the heap. */
    fun storeStream(
        cache: DiskCache,
        key: String,
        input: java.io.InputStream,
        maxBytes: Long,
        mimeType: String?,
    ): SourceFetchResult {
        val editor = cache.openEditor(key) ?: throw java.io.IOException("Artwork cache is unavailable")
        try {
            cache.fileSystem.write(editor.metadata) { writeUtf8(mimeType.orEmpty()) }
            var total = 0L
            cache.fileSystem.write(editor.data) {
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) throw java.io.IOException("Artwork is too large")
                    write(buffer, 0, count)
                }
            }
            val snapshot = editor.commitAndOpenSnapshot()
                ?: throw java.io.IOException("Artwork cache commit failed")
            return SourceFetchResult(
                source = ImageSource(file = snapshot.data, fileSystem = cache.fileSystem, closeable = snapshot),
                mimeType = mimeType,
                dataSource = DataSource.NETWORK,
            )
        } catch (failure: Throwable) {
            editor.abort()
            throw failure
        }
    }

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

    fun copyTo(
        cache: DiskCache?,
        key: String,
        output: java.io.OutputStream,
        maxBytes: Long = Long.MAX_VALUE,
    ): Boolean {
        val snapshot = cache?.openSnapshot(key) ?: return false
        return try {
            val size = cache.fileSystem.metadata(snapshot.data).size
            if (size != null && size > maxBytes) return false
            cache.fileSystem.read(snapshot.data) {
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val count = read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) throw java.io.IOException("Artwork exceeds the copy limit")
                    output.write(buffer, 0, count)
                }
            }
            true
        } catch (_: Throwable) {
            false
        } finally {
            snapshot.close()
        }
    }

    /** Stream a notification image to its pipe and Coil cache without retaining a full image byte array. */
    fun storeStreamToOutput(
        cache: DiskCache?,
        key: String,
        input: java.io.InputStream,
        output: java.io.OutputStream,
        maxBytes: Long,
        mimeType: String?,
    ) {
        val editor = cache?.openEditor(key)
        if (editor == null) {
            copyBounded(input, output, maxBytes)
            return
        }
        try {
            cache.fileSystem.write(editor.metadata) { writeUtf8(mimeType.orEmpty()) }
            var total = 0L
            cache.fileSystem.write(editor.data) {
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) throw java.io.IOException("Artwork exceeds the copy limit")
                    output.write(buffer, 0, count)
                    write(buffer, 0, count)
                }
            }
            val snapshot = editor.commitAndOpenSnapshot()
                ?: throw java.io.IOException("Artwork cache commit failed")
            snapshot.close()
        } catch (failure: Throwable) {
            editor.abort()
            throw failure
        }
    }

    private fun copyBounded(input: java.io.InputStream, output: java.io.OutputStream, maxBytes: Long) {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw java.io.IOException("Artwork exceeds the copy limit")
            output.write(buffer, 0, count)
        }
    }

    fun key(namespace: String, identity: String): String = "$namespace:$identity"
}
