package com.music.bitchord.data.navidrome

import android.content.Context
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The original app's editorial fallback for albums without server notes.
 *
 * It accepts a description only after title, artist and release tracks agree,
 * and never contributes songs, artwork, or recommendations to Navidrome.
 */
internal object NavidromeAlbumEnrichment {
    private lateinit var preferences: android.content.SharedPreferences

    fun init(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(
            "navidrome_album_enrichment",
            Context.MODE_PRIVATE,
        )
    }

    suspend fun description(
        cacheId: String,
        title: String,
        albumArtist: String,
        tracks: Set<String>,
    ): String? {
        if (!::preferences.isInitialized || title.isBlank() || albumArtist.isBlank()) return null
        val key = "album:$cacheId"
        preferences.getString(key, null)?.let { stored ->
            val json = runCatching { JSONObject(stored) }.getOrNull()
            if (json != null && System.currentTimeMillis() - json.optLong("at") < RETRY_AFTER_MS) {
                return json.text("description")
            }
        }

        val wantedTitle = normalized(title)
        val wantedArtist = normalized(albumArtist)
        val candidates = YtMusicRepository.search("$title $albumArtist", SearchFilter.ALBUMS)
            .getOrNull()
            ?.filterIsInstance<SearchResult.Browse>()
            ?.map { it.item }
            ?.filter { item ->
                item.type == BrowseType.ALBUM &&
                    normalized(item.title) == wantedTitle &&
                    normalized(item.subtitle).contains(wantedArtist)
            }
            .orEmpty()
        val wantedTracks = tracks.map(::normalized).filter(String::isNotBlank).toSet()
        val description = candidates.firstNotNullOfOrNull { candidate ->
            val page = YtMusicRepository.browseSongs(candidate.browseId).getOrNull()
                ?: return@firstNotNullOfOrNull null
            val candidateTracks = page.songs.map { normalized(it.title) }.toSet()
            val overlap = wantedTracks.count { it in candidateTracks }
            val required = minOf(2, wantedTracks.size).coerceAtLeast(1)
            page.description?.takeIf { overlap >= required }
        }
        preferences.edit().putString(
            key,
            JSONObject().put("at", System.currentTimeMillis())
                .put("description", description ?: JSONObject.NULL).toString(),
        ).apply()
        return description
    }

    private fun JSONObject.text(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun normalized(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private val RETRY_AFTER_MS = TimeUnit.DAYS.toMillis(14)
}
