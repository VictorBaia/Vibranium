package com.music.bitchord.data.navidrome

import android.content.Context
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Optional presentation-only enrichment for a Navidrome artist. It never
 * contributes tracks or releases: a match must share an album or track name
 * with the server before its photo, bio and monthly listeners are accepted.
 */
internal object NavidromeArtistEnrichment {
    data class Facts(val image: String?, val description: String?, val monthlyListeners: String?)

    private lateinit var preferences: android.content.SharedPreferences

    fun init(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(
            "navidrome_artist_enrichment",
            Context.MODE_PRIVATE,
        )
    }

    suspend fun find(cacheId: String, name: String, albums: Set<String>, tracks: Set<String>): Facts? {
        if (!::preferences.isInitialized || name.isBlank()) return null
        val key = "artist:$cacheId"
        preferences.getString(key, null)?.let { stored ->
            val json = runCatching { JSONObject(stored) }.getOrNull()
            if (json != null && System.currentTimeMillis() - json.optLong("at") < RETRY_AFTER_MS) {
                if (json.optBoolean("miss")) return null
                return Facts(json.text("image"), json.text("description"), json.text("monthly"))
            }
        }
        val hit = YtMusicRepository.search(name, SearchFilter.ARTISTS).getOrNull()
            ?.filterIsInstance<SearchResult.Browse>()
            ?.map { it.item }
            ?.firstOrNull { it.title.equals(name, ignoreCase = true) }
        val page = hit?.let { YtMusicRepository.artistPage(it.browseId).getOrNull() }
        val candidateAlbums = page?.sections.orEmpty().flatMap { it.items }
            .map { normalized(it.title) }.toSet()
        val candidateTracks = page?.songs.orEmpty().map { normalized(it.title) }.toSet()
        val confirmed = albums.map(::normalized).any(candidateAlbums::contains) ||
            tracks.map(::normalized).any(candidateTracks::contains)
        val facts = if (confirmed && page != null) {
            Facts(page.thumbnailUrl ?: hit.thumbnailUrl, page.description, page.monthlyListenerCount)
        } else {
            null
        }
        val stored = JSONObject().put("at", System.currentTimeMillis()).put("miss", facts == null)
        facts?.let {
            stored.put("image", it.image ?: JSONObject.NULL)
            stored.put("description", it.description ?: JSONObject.NULL)
            stored.put("monthly", it.monthlyListeners ?: JSONObject.NULL)
        }
        preferences.edit().putString(key, stored.toString()).apply()
        return facts
    }

    private fun JSONObject.text(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun normalized(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private val RETRY_AFTER_MS = TimeUnit.DAYS.toMillis(14)
}
