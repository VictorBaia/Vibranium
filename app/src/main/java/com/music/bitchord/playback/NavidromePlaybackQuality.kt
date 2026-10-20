package com.music.bitchord.playback

import android.net.Uri
import com.music.bitchord.data.navidrome.NavidromeStore
import com.music.bitchord.data.navidrome.NavidromeTranscodeProfile
import com.music.bitchord.data.navidrome.NavidromeTranscoding
import com.music.bitchord.data.settings.AppSettings

/**
 * Chooses quality once for one DataSource lifetime. A setting/network change
 * therefore never mutates a track already opened, while the next unprepared
 * track observes the new setting.
 */
internal class NavidromePlaybackQuality {
    private var selected: NavidromeTranscodeProfile? = null

    internal fun select(
        current: NavidromeTranscodeProfile,
        cached: NavidromeTranscodeProfile?,
    ): NavidromeTranscodeProfile = selected ?: (cached ?: current).also { selected = it }

    fun bind(uri: Uri, hasCachedBytes: (Uri) -> Boolean): Uri {
        if (uri.authority != "navidrome") return uri
        val forceStreaming = (NavidromeStore.state.value as? NavidromeStore.State.Configured)
            ?.config?.forceStreaming == true
        val old = NavidromeTranscodeProfile.fromWire(
            uri.getQueryParameter("qf"),
            uri.getQueryParameter("qk")?.toIntOrNull(),
        )
        val cached = old?.takeIf {
            selected == null && !forceStreaming && hasCachedBytes(uri)
        }
        val quality = select(
            NavidromeTranscoding.effective(AppSettings.meteredConnection.value),
            cached,
        )

        return uri.buildUpon().clearQuery().apply {
            uri.queryParameterNames.filterNot {
                it == "qf" || it == "qk" || it == "ndPlaybackSource"
            }.forEach { name ->
                uri.getQueryParameters(name).forEach { appendQueryParameter(name, it) }
            }
            appendQueryParameter("qf", quality.format ?: "raw")
            quality.maxBitRateKbps?.let { appendQueryParameter("qk", it.toString()) }
        }.build()
    }
}
