package com.music.bitchord.data.navidrome

import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UserPlaylist

internal object NavidromeMapper {
    fun song(value: NavidromeSong): Song = Song(
        videoId = NavidromeIds.song(value.id),
        title = value.title,
        artist = value.artist.orEmpty(),
        thumbnailUrl = value.coverArt?.let(NavidromeArtwork::uri),
        durationText = value.duration?.takeIf { it >= 0 }?.let(::durationText),
        artistId = value.artistId?.let(NavidromeIds::artist),
        artists = value.artist?.takeIf(String::isNotBlank)?.let { name ->
            listOf(ArtistRef(name, value.artistId?.let(NavidromeIds::artist)))
        }.orEmpty(),
        albumId = value.albumId?.let(NavidromeIds::album),
        albumName = value.album,
        isVideo = false,
        releaseTrackNumber = value.track?.takeIf { it > 0 },
        isVideoOrigin = false,
        sourceQuality = quality(value),
        isExplicit = value.isExplicit ?: value.explicitStatus.isExplicitStatus(),
    )

    fun artist(value: NavidromeArtist): BrowseItem = BrowseItem(
        browseId = NavidromeIds.artist(value.id),
        title = value.name,
        subtitle = value.albumCount?.let { "$it" }.orEmpty(),
        thumbnailUrl = value.coverArt?.let(NavidromeArtwork::uri)
            ?: value.artistImageUrl,
        type = BrowseType.ARTIST,
    )

    fun album(value: NavidromeAlbum): BrowseItem = BrowseItem(
        browseId = NavidromeIds.album(value.id),
        title = value.displayName,
        subtitle = value.artist.orEmpty(),
        thumbnailUrl = value.coverArt?.let(NavidromeArtwork::uri),
        type = BrowseType.ALBUM,
    )

    fun playlist(value: NavidromePlaylist): BrowseItem = BrowseItem(
        browseId = NavidromeIds.playlist(value.id),
        title = value.name,
        subtitle = value.owner.orEmpty(),
        thumbnailUrl = value.coverArt?.let(NavidromeArtwork::uri),
        type = BrowseType.PLAYLIST,
    )

    fun userPlaylist(value: NavidromePlaylist): UserPlaylist = UserPlaylist(
        playlistId = value.id,
        title = value.name,
        subtitle = value.owner.orEmpty(),
        thumbnailUrl = value.coverArt?.let(NavidromeArtwork::uri),
        sourceBrowseId = NavidromeIds.playlist(value.id),
    )

    fun search(value: NavidromeSearchResult, filter: SearchFilter): List<SearchResult> = buildList {
        if (filter == SearchFilter.ALL || filter == SearchFilter.SONGS) {
            addAll(value.song.map { SearchResult.Track(song(it)) })
        }
        if (filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS) {
            addAll(value.artist.map { SearchResult.Browse(artist(it)) })
        }
        if (filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS) {
            addAll(value.album.map { SearchResult.Browse(album(it)) })
        }
        // OpenSubsonic search3 has no playlist or video result collection.
    }

    private fun durationText(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remaining = seconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remaining)
        else "%d:%02d".format(minutes, remaining)
    }

    private fun quality(value: NavidromeSong): String? = when {
        value.contentType.equals("audio/flac", ignoreCase = true) ||
            value.suffix.equals("flac", ignoreCase = true) -> "LOSSLESS"
        value.bitRate != null && value.bitRate >= 256 -> "HIGH"
        value.bitRate != null -> "LOW"
        else -> null
    }

    /**
     * Navidrome's OpenSubsonic payload uses `explicitStatus`, not the legacy
     * boolean field. Keep null for a missing/unknown value so no badge is
     * invented for tracks whose metadata does not say.
     */
    private fun String?.isExplicitStatus(): Boolean? = when (this?.trim()?.lowercase()) {
        "explicit", "e" -> true
        "clean", "c" -> false
        else -> null
    }
}

