package com.music.bitchord.data.navidrome

import android.content.Context
import android.util.Log
import com.music.bitchord.data.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Native Navidrome album tags, cached independently from the OpenSubsonic catalogue. */
internal object NavidromeEssentialAlbums {
    private const val TAG = "NavidromeEssentials"
    private const val PREFS = "navidrome_essential_albums"
    private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
    private const val PAGE_SIZE = 500
    private val mutex = Mutex()

    @Volatile
    private var memory: Snapshot? = null

    suspend fun forArtist(
        context: Context,
        artistId: String,
        artistName: String,
        forceRefresh: Boolean = false,
    ): Set<String> = withContext(Dispatchers.IO) {
        val config = (NavidromeStore.state.value as? NavidromeStore.State.Configured)?.config
            ?: return@withContext emptySet()
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mutex.withLock {
            val cached =
                memory?.takeIf { it.accountKey == config.accountKey }
                    ?: readCache(prefs, config.accountKey)?.also { memory = it }
            if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.fetchedAt < CACHE_TTL_MS) {
                return@withLock cached.albumIdsFor(artistId, artistName)
            }

            val fresh = try {
                fetch(config)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Could not refresh native album tags", failure)
                return@withLock cached?.albumIdsFor(artistId, artistName).orEmpty()
            }
            val snapshot = Snapshot(config.accountKey, System.currentTimeMillis(), fresh)
            memory = snapshot
            writeCache(prefs, snapshot)
            snapshot.albumIdsFor(artistId, artistName)
        }
    }

    fun invalidate(context: Context) {
        memory = null
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun fetch(config: NavidromeConfig): List<TaggedAlbum> {
        val password = (config.credential as? NavidromeCredential.Password)?.value
            ?: throw IOException("Native Navidrome album tags require password authentication")
        val base = config.baseUrl()
        val loginUrl = base.newBuilder()
            .addPathSegment("auth")
            .addPathSegment("login")
            .build()
        val loginJson = JSONObject()
            .put("username", config.username)
            .put("password", password)
            .toString()
        var token = Http.client.newCall(
            Request.Builder()
                .url(loginUrl)
                .post(loginJson.toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        ).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Navidrome login returned HTTP ${response.code}")
            val body = response.body.string()
            response.header(AUTH_HEADER)?.let(::tokenFromHeader)
                ?: JSONObject(body).optString("token").takeIf(String::isNotBlank)
                ?: throw IOException("Navidrome login response did not contain a token")
        }

        val albums = ArrayList<TaggedAlbum>()
        var start = 0
        while (true) {
            val url = base.newBuilder()
                .addPathSegment("api")
                .addPathSegment("album")
                .addQueryParameter("_start", start.toString())
                .addQueryParameter("_end", (start + PAGE_SIZE).toString())
                .addQueryParameter("_sort", "name")
                .addQueryParameter("_order", "ASC")
                .build()
            val page = Http.client.newCall(
                Request.Builder()
                    .url(url)
                    .header(AUTH_HEADER, "Bearer $token")
                    .get()
                    .build(),
            ).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Navidrome album API returned HTTP ${response.code}")
                response.header(AUTH_HEADER)?.let(::tokenFromHeader)?.let { token = it }
                parseAlbumPage(response.body.string())
            }
            albums += page.essentialAlbums
            if (page.albumCount < PAGE_SIZE) break
            start += PAGE_SIZE
        }
        return albums
    }

    private fun parseAlbumPage(json: String): ParsedPage {
        val array = when (val root = json.trim().firstOrNull()) {
            '[' -> JSONArray(json)
            '{' -> {
                val objectRoot = JSONObject(json)
                objectRoot.optJSONArray("data")
                    ?: objectRoot.optJSONArray("albums")
                    ?: throw IOException("Navidrome album API returned an unexpected object")
            }
            else -> throw IOException("Navidrome album API returned invalid JSON")
        }
        val essentialAlbums = buildList(array.length()) {
            for (index in 0 until array.length()) {
                val album = array.optJSONObject(index) ?: continue
                val id = album.optString("id").takeIf(String::isNotBlank) ?: continue
                val tags = album.optJSONObject("tags") ?: continue
                val values = tags.keys().asSequence()
                    .firstOrNull { it.equals("relesential", ignoreCase = true) }
                    ?.let(tags::opt)
                    .let(::tagValues)
                val normalized = values.map(String::trim).filter(String::isNotEmpty)
                val hasTrue = normalized.any { it.equals("true", true) || it == "1" }
                val hasFalse = normalized.any { it.equals("false", true) || it == "0" }
                if (hasTrue && hasFalse) {
                    Log.w(TAG, "Mixed relesential values for album $id; treating it as non-essential")
                } else if (hasTrue) {
                    add(
                        TaggedAlbum(
                            id = id,
                            albumArtistId = album.optString("albumArtistId").takeIf(String::isNotBlank),
                            albumArtist = album.optString("albumArtist").takeIf(String::isNotBlank),
                        ),
                    )
                }
            }
        }
        return ParsedPage(array.length(), essentialAlbums)
    }

    private fun tagValues(value: Any?): List<String> = when (value) {
        is JSONArray -> buildList(value.length()) {
            for (index in 0 until value.length()) value.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
        is String -> listOf(value)
        else -> emptyList()
    }

    private fun readCache(prefs: android.content.SharedPreferences, accountKey: String): Snapshot? =
        runCatching {
            val json = prefs.getString(cacheKey(accountKey), null) ?: return null
            val root = JSONObject(json)
            val entries = root.optJSONArray("albums") ?: return null
            val albums = buildList(entries.length()) {
                for (index in 0 until entries.length()) {
                    val album = entries.optJSONObject(index) ?: continue
                    val id = album.optString("id").takeIf(String::isNotBlank) ?: continue
                    add(
                        TaggedAlbum(
                            id = id,
                            albumArtistId = album.optString("artistId").takeIf(String::isNotBlank),
                            albumArtist = album.optString("artist").takeIf(String::isNotBlank),
                        ),
                    )
                }
            }
            Snapshot(accountKey, root.optLong("fetchedAt"), albums)
        }.getOrNull()

    private fun writeCache(prefs: android.content.SharedPreferences, snapshot: Snapshot) {
        val albums = JSONArray().apply {
            snapshot.albums.forEach { album ->
                put(
                    JSONObject()
                        .put("id", album.id)
                        .put("artistId", album.albumArtistId)
                        .put("artist", album.albumArtist),
                )
            }
        }
        prefs.edit().putString(
            cacheKey(snapshot.accountKey),
            JSONObject().put("fetchedAt", snapshot.fetchedAt).put("albums", albums).toString(),
        ).apply()
    }

    private fun cacheKey(accountKey: String) = "albums:$accountKey"

    private fun tokenFromHeader(header: String): String = header.removePrefix("Bearer ").trim()

    private data class TaggedAlbum(
        val id: String,
        val albumArtistId: String?,
        val albumArtist: String?,
    )

    private data class ParsedPage(
        val albumCount: Int,
        val essentialAlbums: List<TaggedAlbum>,
    )

    private data class Snapshot(
        val accountKey: String,
        val fetchedAt: Long,
        val albums: List<TaggedAlbum>,
    ) {
        fun albumIdsFor(artistId: String, artistName: String): Set<String> = albums.asSequence()
            .filter { album ->
                album.albumArtistId == artistId ||
                    (album.albumArtistId.isNullOrBlank() && album.albumArtist.equals(artistName, ignoreCase = true))
            }
            .map(TaggedAlbum::id)
            .toSet()
    }

    private const val AUTH_HEADER = "x-nd-authorization"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
}
