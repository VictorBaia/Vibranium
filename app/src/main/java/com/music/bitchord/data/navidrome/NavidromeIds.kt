package com.music.bitchord.data.navidrome

import java.util.UUID

internal enum class NavidromeEntity(val prefix: String) {
    SONG("nd:"),
    ALBUM("nd:album:"),
    ARTIST("nd:artist:"),
    PLAYLIST("nd:playlist:"),
    GENRE("nd:genre:"),
}

internal data class NavidromeId(val entity: NavidromeEntity, val rawId: String)

internal object NavidromeIds {
    /** Virtual collection backed by OpenSubsonic getStarred2, not a server playlist. */
    const val LIKED_SONGS_BROWSE_ID = "nd:liked-songs"
    private const val PLAYBACK_PREFIX = "bitchord://navidrome/"
    private const val ARTWORK_PREFIX = "bitchord://navidrome-artwork/"

    fun song(id: String): String = encode(NavidromeEntity.SONG, id)
    fun album(id: String): String = encode(NavidromeEntity.ALBUM, id)
    fun artist(id: String): String = encode(NavidromeEntity.ARTIST, id)
    fun playlist(id: String): String = encode(NavidromeEntity.PLAYLIST, id)
    fun genre(id: String): String = encode(NavidromeEntity.GENRE, id)

    fun parse(value: String): NavidromeId? {
        val entity = NavidromeEntity.entries
            .sortedByDescending { it.prefix.length }
            .firstOrNull { value.startsWith(it.prefix) }
            ?: return null
        val raw = value.removePrefix(entity.prefix)
        return raw.takeIf(::validRawId)?.let { NavidromeId(entity, it) }
    }

    fun playbackUri(songId: String): String = PLAYBACK_PREFIX + encodePathSegment(requireRawId(songId))

    /** A credential-free per-queue request identity used to pin the selected quality while seeking. */
    fun playbackRequestUri(
        songId: String,
        durationSeconds: Long?,
    ): String = buildString {
        append(playbackUri(songId)).append("?request=").append(UUID.randomUUID().toString())
        durationSeconds?.takeIf { it > 0 }?.let { append("&d=").append(it) }
    }

    fun artworkUri(coverArtId: String, accountKey: String? = null): String = buildString {
        append(ARTWORK_PREFIX).append(encodePathSegment(requireRawId(coverArtId)))
        accountKey?.takeIf(String::isNotBlank)?.let {
            append("?account=").append(encodePathSegment(it))
        }
    }

    fun songIdFromPlaybackUri(uri: String): String? {
        if (!uri.startsWith(PLAYBACK_PREFIX)) return null
        val encoded = uri.removePrefix(PLAYBACK_PREFIX).substringBefore('?')
        if (encoded.isEmpty() || encoded.contains('/') || encoded.contains('#')) return null
        return decodePathSegment(encoded)?.takeIf(::validRawId)
    }

    fun coverArtIdFromUri(uri: String): String? {
        if (!uri.startsWith(ARTWORK_PREFIX)) return null
        val encoded = uri.removePrefix(ARTWORK_PREFIX).substringBefore('?')
        if (encoded.isEmpty() || encoded.contains('/') || encoded.contains('#')) return null
        return decodePathSegment(encoded)?.takeIf(::validRawId)
    }

    fun artworkAccountKeyFromUri(uri: String): String? {
        if (!uri.startsWith(ARTWORK_PREFIX)) return null
        val query = uri.substringAfter('?', missingDelimiterValue = "")
        val encoded = query.split('&').firstNotNullOfOrNull { parameter ->
            parameter.substringBefore('=').takeIf { it == "account" }
                ?.let { parameter.substringAfter('=', missingDelimiterValue = "") }
        } ?: return null
        return decodePathSegment(encoded)?.takeIf(::validRawId)
    }

    /** Optional server-side thumbnail edge requested by [artworkAt]. */
    fun artworkSizeFromUri(uri: String): Int? {
        if (!uri.startsWith(ARTWORK_PREFIX)) return null
        val query = uri.substringAfter('?', missingDelimiterValue = "")
        return query.split('&').firstNotNullOfOrNull { parameter ->
            parameter.substringBefore('=').takeIf { it == "size" }
                ?.let { parameter.substringAfter('=', missingDelimiterValue = "").toIntOrNull() }
        }?.takeIf { it == ORIGINAL_ARTWORK_SIZE || it in MIN_ARTWORK_SIZE..MAX_ARTWORK_SIZE }
    }

    private fun encode(entity: NavidromeEntity, rawId: String): String = entity.prefix + requireRawId(rawId)

    private fun requireRawId(value: String): String {
        require(validRawId(value)) { "Navidrome ID must be non-blank and must not contain control characters" }
        return value
    }

    private fun validRawId(value: String): Boolean = value.isNotBlank() && value.none(Char::isISOControl)

    private const val ORIGINAL_ARTWORK_SIZE = 0

    private fun encodePathSegment(value: String): String = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val unsigned = byte.toInt() and 0xff
            if (
                unsigned in 'a'.code..'z'.code || unsigned in 'A'.code..'Z'.code ||
                unsigned in '0'.code..'9'.code || unsigned == '-'.code || unsigned == '_'.code ||
                unsigned == '.'.code || unsigned == '~'.code
            ) {
                append(unsigned.toChar())
            } else {
                append('%')
                append(HEX[unsigned ushr 4])
                append(HEX[unsigned and 0x0f])
            }
        }
    }

    private fun decodePathSegment(value: String): String? = runCatching {
        val bytes = ArrayList<Byte>(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] == '%') {
                require(index + 2 < value.length)
                val high = value[index + 1].digitToInt(16)
                val low = value[index + 2].digitToInt(16)
                bytes += ((high shl 4) or low).toByte()
                index += 3
            } else {
                val code = value[index].code
                require(code <= 0x7f)
                bytes += code.toByte()
                index += 1
            }
        }
        bytes.toByteArray().toString(Charsets.UTF_8)
    }.getOrNull()

    private const val HEX = "0123456789ABCDEF"
    private const val MIN_ARTWORK_SIZE = 64
    private const val MAX_ARTWORK_SIZE = 1_200
}

