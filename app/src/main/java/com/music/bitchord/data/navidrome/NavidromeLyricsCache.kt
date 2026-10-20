package com.music.bitchord.data.navidrome

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** Persistent, account-scoped Navidrome lyrics. Intentionally has no eviction limit. */
internal object NavidromeLyricsCache {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var root: File? = null

    fun init(context: Context) {
        root = File(context.applicationContext.filesDir, "navidrome-lyrics").apply { mkdirs() }
    }

    suspend fun read(accountKey: String, songId: String): NavidromeLyricsList? = withContext(Dispatchers.IO) {
        val file = file(accountKey, songId) ?: return@withContext null
        runCatching { json.decodeFromString<NavidromeLyricsList>(file.readText()) }.getOrNull()
    }

    suspend fun write(accountKey: String, songId: String, lyrics: NavidromeLyricsList): Unit = withContext(Dispatchers.IO) {
        val target = file(accountKey, songId) ?: return@withContext
        runCatching {
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "${target.name}.tmp")
            temporary.writeText(json.encodeToString(lyrics))
            if (!temporary.renameTo(target)) {
                target.writeText(temporary.readText())
                temporary.delete()
            }
        }.getOrNull()
        Unit
    }

    private fun file(accountKey: String, songId: String): File? {
        val directory = root ?: return null
        return File(File(directory, digest(accountKey)), "${digest(songId)}.json")
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
