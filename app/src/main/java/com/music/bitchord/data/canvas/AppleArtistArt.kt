package com.music.bitchord.data.canvas

import android.content.Context
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.Http
import androidx.compose.ui.graphics.Color
import com.music.bitchord.ui.theme.ArtworkKeyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * What Apple Music's artist page shows at the top: a hero photograph, usually
 * the artist's title logo drawn over it as a separate image, and for some
 * artists a looping video that stands in for the photograph.
 *
 * [logoUrl] is a transparent PNG, null for the many artists without one;
 * [logoAspect] is its width over its height, so the caller can size it without
 * waiting for the image to decode. [videoUrl] is an HLS playlist.
 *
 * [background] and [accent] are Apple's own colours for the photograph (as
 * ARGB), so the page can be tinted without decoding the image to find them.
 */
data class AppleArtistArt(
    val heroUrl: String,
    val logoUrl: String?,
    val logoAspect: Float,
    val videoUrl: String?,
    val background: Int,
    val accent: Int,
    /** The colour Apple fills the artist's Play button with; absent for some artists. */
    val keyColor: Int?,
)

/** Apple's colours for this artist, in the form the palette takes. */
fun AppleArtistArt.keyColors() = ArtworkKeyColors(Color(background), Color(accent))

/**
 * Finds [AppleArtistArt] from nothing but an artist's name.
 *
 * Two public, unauthenticated requests: the iTunes search API turns the name
 * into an Apple Music artist id, and the artist's own music.apple.com page
 * carries its header art in the JSON it embeds for hydration. That JSON is not
 * an API Apple publishes, so every step here treats a surprise as "no art" —
 * the YouTube header is always underneath.
 *
 * A search will happily return somebody else, so an answer is only taken when
 * the name Apple gives back is the name that was asked for. Most artists have
 * no logo; successful artwork lookups are cached. Misses are not cached,
 * because an unreachable Apple endpoint is not evidence that an artist has no
 * artwork.
 */
object AppleArtistArtRepository {

    private const val TAG = "AppleArtistArt"
    private const val CACHE_SIZE = 64
    private const val HERO_WIDTH = 1200
    private const val LOGO_WIDTH = 1000
    private val DISK_TTL_MS = TimeUnit.HOURS.toMillis(24)
    private var diskCache: android.content.SharedPreferences? = null
    private val lookupLocks = ConcurrentHashMap<String, Mutex>()
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private val cache = object : LinkedHashMap<String, AppleArtistArt>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AppleArtistArt>) =
            size > CACHE_SIZE
    }

    fun init(context: Context) {
        diskCache = context.applicationContext.getSharedPreferences("apple_artist_art", Context.MODE_PRIVATE)
    }

    private val _updates = MutableStateFlow(0)

    /**
     * Ticks whenever a lookup settles. The page that asked for the art is not the
     * only thing coloured by it — the bars above and below the page are drawn
     * from outside it — so anything that reads [cached] re-reads on this.
     */
    val updates: StateFlow<Int> = _updates

    /**
     * What is already known for [artistName], without a request — null for a
     * name has not resolved yet.
     */
    fun cached(artistName: String): AppleArtistArt? {
        val key = artistName.normalized()
        synchronized(cache) { cache[key] }?.let { return it }
        val json = diskCache?.getString(key, null)?.let {
            runCatching { JSONObject(it) }.getOrNull()
        } ?: return null
        if (System.currentTimeMillis() - json.optLong("at") > DISK_TTL_MS) return null
        val hero = json.optString("hero").takeIf(String::isNotBlank) ?: return null
        val art = AppleArtistArt(
            heroUrl = hero,
            logoUrl = json.optString("logo").takeIf(String::isNotBlank),
            logoAspect = json.optDouble("aspect", 1.0).toFloat(),
            videoUrl = json.optString("video").takeIf(String::isNotBlank),
            background = json.optInt("background"),
            accent = json.optInt("accent"),
            keyColor = if (json.has("keyColor")) json.optInt("keyColor") else null,
        )
        synchronized(cache) { cache[key] = art }
        return art
    }

    /** The header art for [artistName], or null when Apple has none. Never throws. */
    suspend fun artFor(artistName: String): AppleArtistArt? {
        val key = artistName.normalized()
        if (key.isEmpty()) return null
        cached(artistName)?.let { return it }
        return lookupLocks.computeIfAbsent(key) { Mutex() }.withLock {
        // A search result may prefetch at the same instant the detail screen
        // asks for this artist. Recheck after the first request completes.
        cached(artistName)?.let { return@withLock it }
        val art = withContext(Dispatchers.IO) {
            runCatching { lookup(artistName, key) }
                .onFailure { Log.d(TAG, "lookup failed for $artistName: $it") }
                .getOrNull()
        } ?: return@withLock null
        synchronized(cache) { cache[key] = art }
        diskCache?.edit()?.putString(key, JSONObject().apply {
            put("at", System.currentTimeMillis())
            put("hero", art.heroUrl)
            put("logo", art.logoUrl ?: "")
            put("aspect", art.logoAspect.toDouble())
            put("video", art.videoUrl ?: "")
            put("background", art.background)
            put("accent", art.accent)
            art.keyColor?.let { put("keyColor", it) }
        }.toString())?.apply()
        _updates.value++
        art
        }
    }

    private fun lookup(artistName: String, key: String): AppleArtistArt? {
        val search = get(
            "https://itunes.apple.com/search?term=${URLEncoder.encode(artistName, "UTF-8")}" +
                "&entity=musicArtist&limit=5",
        ) ?: return null
        val results = JSONObject(search).optJSONArray("results") ?: JSONArray()
        var page: String? = null
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            if (r.optString("artistName").normalized() == key) {
                // The link carries the artist's slug; the bare id answers with
                // a redirect to it, one more round trip for the same page.
                page = r.optString("artistLinkUrl").substringBefore('?').takeIf { it.isNotBlank() }
                    ?: r.optLong("artistId").takeIf { it > 0 }?.let { "https://music.apple.com/us/artist/$it" }
                break
            }
        }
        if (page == null) return null

        val html = get(page) ?: return null
        return parse(html)
    }

    private fun parse(html: String): AppleArtistArt? {
        val marker = html.indexOf("id=\"serialized-server-data\"")
        if (marker < 0) return null
        val start = html.indexOf('>', marker) + 1
        val end = html.indexOf("</script>", start)
        if (start <= 0 || end < 0) return null
        val header = findHeader(JSONObject(html.substring(start, end).let { "{\"d\":$it}" }).get("d"))
            ?: return null

        // Absent for most artists: the key is there and null.
        val logo = header.optJSONObject("artistLogo")?.optJSONObject("dictionary")
        val hero = (header.optJSONObject("artwork") ?: header.optJSONObject("colorBackdropArtwork"))
            ?.optJSONObject("dictionary") ?: return null

        val logoW = logo?.optInt("width") ?: 0
        val logoH = logo?.optInt("height") ?: 0
        val heroW = hero.optInt("width").takeIf { it > 0 } ?: return null
        val heroH = hero.optInt("height").takeIf { it > 0 } ?: return null

        val stillUrl = hero.optString("url").fill(
            HERO_WIDTH, (HERO_WIDTH.toLong() * heroH / heroW).toInt(), "jpg",
        ) ?: return null
        val logoUrl = if (logo != null && logoW > 0 && logoH > 0) {
            logo.optString("url").fill(LOGO_WIDTH, (LOGO_WIDTH.toLong() * logoH / logoW).toInt(), "png")
        } else {
            null
        }
        val clip = header.optJSONObject("videoArtwork")?.optJSONObject("dictionary")
            ?.optJSONObject("motionArtistSquare1x1")
        val video = clip?.optString("video")?.takeIf { it.startsWith("http") }
        // Use the clip's own preview as its loading frame. The ordinary hero
        // can be a different photograph, making the header visibly jump just
        // before the first decoded video frame arrives.
        val preview = clip?.optJSONObject("previewFrame")?.takeIf { video != null }
        val previewW = preview?.optInt("width") ?: 0
        val previewH = preview?.optInt("height") ?: 0
        val heroUrl = if (previewW > 0 && previewH > 0) {
            preview?.optString("url")?.fill(
                HERO_WIDTH, (HERO_WIDTH.toLong() * previewH / previewW).toInt(), "jpg",
            ) ?: stillUrl
        } else stillUrl
        // The clip is a different picture from the still, with its own colours
        // — the page has to match whichever one is on screen.
        val colours = preview ?: hero
        val background = colours.optString("bgColor").toArgb()
            ?: hero.optString("bgColor").toArgb() ?: return null
        val accent = colours.optString("textColor1").toArgb() ?: background
        return AppleArtistArt(
            heroUrl = heroUrl,
            logoUrl = logoUrl,
            logoAspect = if (logoUrl != null) logoW.toFloat() / logoH else 1f,
            videoUrl = video,
            background = background,
            accent = accent,
            keyColor = header.optString("keyColor").toArgb(),
        )
    }

    /** The first object anywhere in [node] that has an `artistLogo` key. */
    private fun findHeader(node: Any?): JSONObject? {
        when (node) {
            is JSONObject -> {
                if (node.has("artistLogo")) return node
                val keys = node.keys()
                while (keys.hasNext()) findHeader(node.opt(keys.next()))?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findHeader(node.opt(i))?.let { return it }
        }
        return null
    }

    /** Apple's colours are six hex digits with no prefix. */
    private fun String.toArgb(): Int? =
        takeIf { it.length == 6 }?.toIntOrNull(16)?.let { 0xFF000000.toInt() or it }

    private fun String.fill(w: Int, h: Int, format: String): String? {
        if (isBlank() || !contains("{w}")) return null
        return replace("{w}", w.toString())
            .replace("{h}", h.toString())
            .replace("{c}", "bb")
            .replace("{f}", format)
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    /** Case, accents and punctuation are not what makes two names different. */
    private fun String.normalized(): String =
        java.text.Normalizer.normalize(lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .filter { it.isLetterOrDigit() }
}
