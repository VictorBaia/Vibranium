package com.music.bitchord.data.navidrome

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonDecoder

@Serializable
internal data class NavidromeUser(
    val username: String,
    val email: String? = null,
    @Serializable(with = FlexibleBooleanSerializer::class) val scrobblingEnabled: Boolean? = null,
    @Serializable(with = FlexibleBooleanSerializer::class) val adminRole: Boolean? = null,
    @Serializable(with = FlexibleBooleanSerializer::class) val settingsRole: Boolean? = null,
    @Serializable(with = FlexibleBooleanSerializer::class) val downloadRole: Boolean? = null,
    @Serializable(with = FlexibleBooleanSerializer::class) val playlistRole: Boolean? = null,
    @Serializable(with = FlexibleBooleanSerializer::class) val coverArtRole: Boolean? = null,
    @Serializable(with = FlexibleBooleanSerializer::class) val streamRole: Boolean? = null,
    val folder: List<Int> = emptyList(),
)

@Serializable
internal data class NavidromeArtistIndexes(
    val ignoredArticles: String? = null,
    val index: List<NavidromeArtistIndex> = emptyList(),
) {
    val all: List<NavidromeArtist>
        get() = index.flatMap(NavidromeArtistIndex::artist)
}

@Serializable
internal data class NavidromeArtistIndex(
    val name: String,
    val artist: List<NavidromeArtist> = emptyList(),
)

@Serializable
internal data class NavidromeArtist(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val artistImageUrl: String? = null,
    val albumCount: Int? = null,
    val starred: String? = null,
    val album: List<NavidromeAlbum> = emptyList(),
)

/** Optional enrichment supplied by Navidrome when its external integrations are enabled. */
@Serializable
internal data class NavidromeArtistInfo(
    val biography: String? = null,
    val smallImageUrl: String? = null,
    val mediumImageUrl: String? = null,
    val largeImageUrl: String? = null,
    /** OpenSubsonic's server-provided artist recommendations. */
    val similarArtist: List<NavidromeArtist> = emptyList(),
)

/** Optional editorial metadata returned by OpenSubsonic `getAlbumInfo2`. */
@Serializable
internal data class NavidromeAlbumInfo(
    val notes: String? = null,
)

@Serializable
internal data class NavidromeAlbumList(
    val album: List<NavidromeAlbum> = emptyList(),
)

@Serializable
internal data class NavidromeAlbum(
    val id: String,
    val name: String? = null,
    val title: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Long? = null,
    val year: Int? = null,
    /** OpenSubsonic `releaseTypes`: e.g. album, single, ep, live. */
    val releaseTypes: List<String> = emptyList(),
    val genre: String? = null,
    val created: String? = null,
    val starred: String? = null,
    val playCount: Long? = null,
    val song: List<NavidromeSong> = emptyList(),
) {
    val displayName: String get() = name ?: album ?: title.orEmpty()
}

@Serializable
internal data class NavidromeSong(
    val id: String,
    val title: String,
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val duration: Long? = null,
    val track: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val size: Long? = null,
    val contentType: String? = null,
    val suffix: String? = null,
    val bitRate: Int? = null,
    val bitDepth: Int? = null,
    val samplingRate: Int? = null,
    val channelCount: Int? = null,
    val starred: String? = null,
    val created: String? = null,
    val playCount: Long? = null,
    /** OpenSubsonic value emitted by Navidrome: `explicit`, `clean`, or absent. */
    val explicitStatus: String? = null,
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isExplicit: Boolean? = null,
)

@Serializable
internal data class NavidromePlaylistList(
    val playlist: List<NavidromePlaylist> = emptyList(),
)

@Serializable
internal data class NavidromePlaylist(
    val id: String,
    val name: String,
    val owner: String? = null,
    @SerialName("public")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isPublic: Boolean? = null,
    val songCount: Int? = null,
    val duration: Long? = null,
    val created: String? = null,
    val changed: String? = null,
    val coverArt: String? = null,
    val entry: List<NavidromeSong> = emptyList(),
)

@Serializable
internal data class NavidromeGenreList(
    val genre: List<NavidromeGenre> = emptyList(),
)

@Serializable
internal data class NavidromeGenre(
    val value: String,
    val songCount: Int = 0,
    val albumCount: Int = 0,
)

@Serializable
internal data class NavidromeExtension(
    val name: String,
    val versions: List<Int> = emptyList(),
)

@Serializable
internal data class NavidromeSearchResult(
    val artist: List<NavidromeArtist> = emptyList(),
    val album: List<NavidromeAlbum> = emptyList(),
    val song: List<NavidromeSong> = emptyList(),
)

@Serializable
internal data class NavidromeStarred(
    val artist: List<NavidromeArtist> = emptyList(),
    val album: List<NavidromeAlbum> = emptyList(),
    val song: List<NavidromeSong> = emptyList(),
)

@Serializable
internal data class NavidromeSongList(
    val song: List<NavidromeSong> = emptyList(),
)

/** Structured server lyrics, available only with OpenSubsonic's songLyrics extension. */
@Serializable
internal data class NavidromeLyricsList(
    @Serializable(with = FlexibleStructuredLyricsListSerializer::class)
    val structuredLyrics: List<NavidromeStructuredLyrics> = emptyList(),
)

@Serializable
internal data class NavidromeStructuredLyrics(
    val kind: String? = null,
    @Serializable(with = FlexibleBooleanSerializer::class) val synced: Boolean? = null,
    val offset: Long? = null,
    @Serializable(with = FlexibleLyricsLineListSerializer::class)
    val line: List<NavidromeLyricsLine> = emptyList(),
    @Serializable(with = FlexibleCueLineListSerializer::class)
    val cueLine: List<NavidromeLyricsCueLine> = emptyList(),
    @Serializable(with = FlexibleAgentListSerializer::class)
    val agents: List<NavidromeLyricsAgent> = emptyList(),
)

@Serializable
internal data class NavidromeLyricsLine(
    val start: Long? = null,
    val value: String = "",
    val end: Long? = null,
    @Serializable(with = FlexibleCueListSerializer::class)
    val cue: List<NavidromeLyricsCue> = emptyList(),
)

@Serializable
internal data class NavidromeLyricsAgent(
    val id: String = "",
    val role: String = "",
)

@Serializable
internal data class NavidromeLyricsCueLine(
    val index: Int = -1,
    val agentId: String = "",
    val start: Long? = null,
    val end: Long? = null,
    val value: String = "",
    @Serializable(with = FlexibleCueListSerializer::class)
    val cue: List<NavidromeLyricsCue> = emptyList(),
)

@Serializable
internal data class NavidromeLyricsCue(
    val start: Long? = null,
    val end: Long? = null,
    val value: String = "",
)

private fun <T> decodeFlexibleList(
    decoder: Decoder,
    serializer: KSerializer<T>,
): List<T> {
    val jsonDecoder = decoder as? JsonDecoder
        ?: throw SerializationException("Lyrics list requires JSON")
    return flexibleJsonArray(jsonDecoder).map { jsonDecoder.json.decodeFromJsonElement(serializer, it) }
}

private abstract class FlexibleListDescriptor<T>(
    private val elementSerializer: KSerializer<T>,
) : KSerializer<List<T>> {
    override val descriptor: SerialDescriptor =
        kotlinx.serialization.builtins.ListSerializer(elementSerializer).descriptor

    override fun deserialize(decoder: Decoder): List<T> = decodeFlexibleList(decoder, elementSerializer)

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: List<T>) =
        encoder.encodeSerializableValue(kotlinx.serialization.builtins.ListSerializer(elementSerializer), value)
}

private object FlexibleStructuredLyricsListSerializer :
    FlexibleListDescriptor<NavidromeStructuredLyrics>(NavidromeStructuredLyrics.serializer())

private object FlexibleLyricsLineListSerializer :
    FlexibleListDescriptor<NavidromeLyricsLine>(NavidromeLyricsLine.serializer())

private object FlexibleCueLineListSerializer :
    FlexibleListDescriptor<NavidromeLyricsCueLine>(NavidromeLyricsCueLine.serializer())

private object FlexibleAgentListSerializer :
    FlexibleListDescriptor<NavidromeLyricsAgent>(NavidromeLyricsAgent.serializer())

private object FlexibleCueListSerializer :
    FlexibleListDescriptor<NavidromeLyricsCue>(NavidromeLyricsCue.serializer())

internal enum class NavidromeAlbumListType(val wireValue: String) {
    RANDOM("random"),
    NEWEST("newest"),
    HIGHEST("highest"),
    FREQUENT("frequent"),
    RECENT("recent"),
    ALPHABETICAL_BY_NAME("alphabeticalByName"),
    ALPHABETICAL_BY_ARTIST("alphabeticalByArtist"),
    STARRED("starred"),
}

