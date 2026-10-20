package com.music.bitchord.data.navidrome

import android.net.Uri
import com.music.bitchord.data.settings.AppSettings
import java.util.concurrent.ConcurrentHashMap

/** Pins a request's quality without putting a server URL or credential in the queue. */
internal object NavidromePlaybackContext {
    private const val MAX_REQUESTS = 512
    private val qualities = ConcurrentHashMap<String, NavidromeTranscodeProfile>()

    fun qualityFor(uri: Uri): NavidromeTranscodeProfile {
        val identity = requestIdentity(uri)
        val explicit = NavidromeTranscodeProfile.fromWire(
            uri.getQueryParameter("qf"),
            uri.getQueryParameter("qk")?.toIntOrNull(),
        )
        if (qualities.size >= MAX_REQUESTS && !qualities.containsKey(identity)) qualities.clear()
        if (explicit != null) {
            qualities[identity] = explicit
            return explicit
        }
        return qualities.getOrPut(identity) {
            NavidromeTranscoding.effective(AppSettings.meteredConnection.value)
        }
    }

    private fun requestIdentity(uri: Uri): String = uri.buildUpon()
        .clearQuery()
        .apply {
            uri.queryParameterNames.filterNot { it == "qf" || it == "qk" }.forEach { name ->
                uri.getQueryParameters(name).forEach { appendQueryParameter(name, it) }
            }
        }
        .build()
        .toString()

    fun cacheKey(uri: Uri, config: NavidromeConfig): String? {
        val songId = NavidromeIds.songIdFromPlaybackUri(uri.toString()) ?: return null
        return "navidrome|${config.accountKey}|$songId|${qualityFor(uri).name.lowercase()}"
    }
}
