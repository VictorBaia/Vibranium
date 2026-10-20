package com.music.bitchord.data.navidrome

import okhttp3.HttpUrl

internal const val NAVIDROME_SOURCE_HEADER = "X-BitChord-Source"
internal const val NAVIDROME_SOURCE_HEADER_VALUE = "navidrome"

internal data class NavidromeStreamProfile(
    val maxBitRateKbps: Int?,
    val format: String?,
)

internal data class NavidromeDownloadFormat(val extension: String, val mimeType: String)

/** Builds request-scoped media URLs. Callers must never persist or log the result. */
internal object NavidromeMediaResolver {
    fun profile(quality: NavidromeTranscodeProfile) =
        NavidromeStreamProfile(quality.maxBitRateKbps, quality.format)

    fun streamUrl(config: NavidromeConfig, songId: String, quality: NavidromeTranscodeProfile): HttpUrl {
        val profile = profile(quality)
        return authenticatedUrl(
            config = config,
            method = "stream",
            parameters = buildMap {
                put("id", songId)
                profile.maxBitRateKbps?.let { put("maxBitRate", it.toString()) }
                profile.format?.let { put("format", it) }
            },
        )
    }

    /** Download URL minted for one HTTP attempt; it must not be retained by the caller. */
    fun downloadUrl(config: NavidromeConfig, songId: String, quality: NavidromeTranscodeProfile): HttpUrl {
        val profile = profile(quality)
        return authenticatedUrl(
            config,
            "stream",
            buildMap {
                put("id", songId)
                profile.maxBitRateKbps?.let { put("maxBitRate", it.toString()) }
                profile.format?.let { put("format", it) }
                put("estimateContentLength", "true")
            },
        )
    }

    fun downloadFormat(
        quality: NavidromeTranscodeProfile,
        suffix: String?,
        contentType: String?,
    ): NavidromeDownloadFormat? {
        quality.extension?.let { return NavidromeDownloadFormat(it, requireNotNull(quality.mimeType)) }
        return when (suffix?.lowercase()?.trim() ?: contentType?.lowercase()?.trim()) {
            "flac", "audio/flac", "audio/x-flac" -> NavidromeDownloadFormat("flac", "audio/flac")
            "mp3", "mpeg", "audio/mpeg" -> NavidromeDownloadFormat("mp3", "audio/mpeg")
            "m4a", "mp4", "aac", "audio/mp4", "audio/aac" -> NavidromeDownloadFormat("m4a", "audio/mp4")
            "ogg", "opus", "audio/ogg", "audio/opus" -> NavidromeDownloadFormat("ogg", "audio/ogg")
            "wav", "wave", "audio/wav", "audio/x-wav" -> NavidromeDownloadFormat("wav", "audio/x-wav")
            else -> null
        }
    }

    fun artworkUrl(config: NavidromeConfig, internalUri: String): HttpUrl {
        val coverArtId = NavidromeIds.coverArtIdFromUri(internalUri)
            ?: throw IllegalArgumentException("Invalid Navidrome artwork URI")
        val accountKey = NavidromeIds.artworkAccountKeyFromUri(internalUri)
        require(accountKey == null || accountKey == config.accountKey) {
            "Navidrome artwork belongs to a different account"
        }
        return authenticatedUrl(
            config,
            "getCoverArt",
            buildMap {
                put("id", coverArtId)
                NavidromeIds.artworkSizeFromUri(internalUri)?.let { put("size", it.toString()) }
            },
        )
    }

    private fun authenticatedUrl(
        config: NavidromeConfig,
        method: String,
        parameters: Map<String, String>,
    ): HttpUrl = config.endpoint(method).newBuilder().apply {
        NavidromeAuth.parameters(config.username, config.credential).forEach { (key, value) ->
            addQueryParameter(key, value)
        }
        parameters.forEach { (key, value) -> addQueryParameter(key, value) }
    }.build()
}

/** Internal artwork identities include the random account namespace, never a credential. */
internal object NavidromeArtwork {
    fun uri(coverArtId: String): String {
        val accountKey = (NavidromeStore.state.value as? NavidromeStore.State.Configured)
            ?.config?.accountKey
        return NavidromeIds.artworkUri(coverArtId, accountKey)
    }
}
