package com.music.bitchord.data.navidrome

import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.lyrics.LyricLine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

internal data class NavidromeCatalogueLabels(
    val newest: String,
    val recentlyPlayed: String,
    val frequentlyPlayed: String,
    val randomAlbums: String,
    val likedSongs: String,
    val albums: String,
    val artists: String,
    val playlists: String,
    val genres: String,
    val singles: String = "Singles",
    val eps: String = "EPs",
    val relatedArtists: String = "Related artists",
)

internal class NavidromeCatalogue(
    private val repository: NavidromeRepository,
) {
    suspend fun home(labels: NavidromeCatalogueLabels): Result<List<HomeShelf>> = supervisorScope {
        val requests = listOf(
            async { repository.albums(NavidromeAlbumListType.NEWEST).valueOrThrow() },
            async { repository.albums(NavidromeAlbumListType.RECENT).valueOrThrow() },
            async { repository.albums(NavidromeAlbumListType.FREQUENT).valueOrThrow() },
            async { repository.albums(NavidromeAlbumListType.RANDOM).valueOrThrow() },
        )
        runCatching {
            val (newest, recent, frequent, random) = requests.awaitAll()
            listOf(
                albumShelf(labels.newest, newest),
                albumShelf(labels.recentlyPlayed, recent),
                albumShelf(labels.frequentlyPlayed, frequent),
                albumShelf(labels.randomAlbums, random),
            ).filter { it.items.isNotEmpty() }
        }
    }

    suspend fun library(labels: NavidromeCatalogueLabels): Result<LibraryPage> = supervisorScope {
        val starred = async { repository.starred().valueOrThrow() }
        val playlists = async { repository.playlists().valueOrThrow() }
        val artists = async { repository.artists().valueOrThrow() }
        // The Library's Albums entry is a complete collection, not one of the
        // recommendation shelves used by Home. Alphabetical order also makes
        // the on-device filter predictable once the user starts typing.
        val albums = async {
            repository.albums(NavidromeAlbumListType.ALPHABETICAL_BY_NAME, size = 500).valueOrThrow()
        }
        runCatching {
            val favourites = starred.await()
            // getStarred2 is the server's authoritative liked-song list. Keep
            // its order and mapped metadata intact; the phone Library exposes
            // it as a first-class text row rather than pretending it is one of
            // the user's playlists.
            val likedSongs = favourites.song
                .sortedByDescending { it.starred.orEmpty() }
                .map(NavidromeMapper::song)
            val playlistItems = playlistShelf(labels.playlists, playlists.await()).items
            LibraryPage(
                likedSongs = likedSongs,
                librarySongs = likedSongs,
                shelves = listOf(
                    albumShelf(labels.albums, albums.await()),
                    artistShelf(labels.artists, artists.await()),
                    HomeShelf(labels.playlists, playlistItems),
                ).filter { it.items.isNotEmpty() },
            )
        }
    }

    suspend fun explore(labels: NavidromeCatalogueLabels): Result<List<MoodGenreSection>> = runCatching {
        val genres = repository.genres().valueOrThrow()
        listOf(
            MoodGenreSection(
                title = labels.genres,
                items = genres.filter { it.songCount > 0 }.map {
                    MoodGenre(it.value, NavidromeIds.genre(it.value), null)
                },
            ),
        ).filter { it.items.isNotEmpty() }
    }

    suspend fun genreShelf(genreId: String): Result<List<HomeShelf>> = runCatching {
        val raw = requireEntity(genreId, NavidromeEntity.GENRE)
        val songs = repository.songsByGenre(raw).valueOrThrow().map(NavidromeMapper::song)
        listOf(songShelf(raw, songs)).filter { it.items.isNotEmpty() }
    }

    suspend fun search(query: String, filter: SearchFilter): Result<List<SearchResult>> = runCatching {
        val includeArtists = filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS
        val includeAlbums = filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS
        val includeSongs = filter == SearchFilter.ALL || filter == SearchFilter.SONGS
        val catalogueRows = if (includeArtists || includeAlbums || includeSongs) {
            val result = repository.search(
                query,
                artistCount = if (includeArtists) 50 else 0,
                albumCount = if (includeAlbums) 50 else 0,
                songCount = if (includeSongs) 100 else 0,
            ).valueOrThrow()
            NavidromeMapper.search(result, filter)
        } else emptyList()
        val playlists = if (filter == SearchFilter.ALL || filter == SearchFilter.PLAYLISTS) {
            repository.playlists().valueOrThrow()
                .filter { it.name.contains(query, ignoreCase = true) || it.owner.orEmpty().contains(query, ignoreCase = true) }
                .map { SearchResult.Browse(NavidromeMapper.playlist(it)) }
        } else emptyList()
        catalogueRows + playlists
    }

    suspend fun detail(
        browseId: String,
        fallbackTitle: String,
        fallbackSubtitle: String,
        fallbackArtwork: String?,
        albumSectionTitle: String = "",
        singleSectionTitle: String = "Singles",
        epSectionTitle: String = "EPs",
        relatedArtistsTitle: String = "Related artists",
        moreFromArtistTitle: String = "More from %s",
        relatedAlbumsTitle: String = "Related albums",
    ): Result<DetailPage> = runCatching {
        if (browseId == NavidromeIds.LIKED_SONGS_BROWSE_ID) {
            val songs = repository.starred().valueOrThrow().song
                .sortedByDescending { it.starred.orEmpty() }
                .map(NavidromeMapper::song)
            return@runCatching DetailPage(
                browseId = browseId,
                title = fallbackTitle,
                subtitle = fallbackSubtitle,
                thumbnailUrl = fallbackArtwork ?: songs.firstOrNull()?.thumbnailUrl,
                songs = songsState(songs),
                type = BrowseType.PLAYLIST,
            )
        }
        val parsed = NavidromeIds.parse(browseId) ?: error("Invalid Navidrome browse ID")
        when (parsed.entity) {
            NavidromeEntity.ALBUM -> albumDetail(
                rawId = parsed.rawId,
                browseId = browseId,
                fallbackTitle = fallbackTitle,
                fallbackSubtitle = fallbackSubtitle,
                fallbackArtwork = fallbackArtwork,
                moreFromArtistTitle = moreFromArtistTitle,
                relatedAlbumsTitle = relatedAlbumsTitle,
            )
            NavidromeEntity.PLAYLIST -> repository.playlist(parsed.rawId).valueOrThrow().let { playlist ->
                DetailPage(
                    browseId = browseId,
                    title = playlist.name.ifBlank { fallbackTitle },
                    subtitle = playlist.owner.orEmpty().ifBlank { fallbackSubtitle },
                    thumbnailUrl = playlist.coverArt?.let(NavidromeArtwork::uri) ?: fallbackArtwork,
                    songs = songsState(playlist.entry.map(NavidromeMapper::song)),
                    type = BrowseType.PLAYLIST,
                )
            }
            NavidromeEntity.ARTIST -> artistDetail(
                rawId = parsed.rawId,
                browseId = browseId,
                fallbackTitle = fallbackTitle,
                fallbackSubtitle = fallbackSubtitle,
                fallbackArtwork = fallbackArtwork,
                albumSectionTitle = albumSectionTitle,
                singleSectionTitle = singleSectionTitle,
                epSectionTitle = epSectionTitle,
                relatedArtistsTitle = relatedArtistsTitle,
            )
            NavidromeEntity.GENRE -> {
                val songs = repository.songsByGenre(parsed.rawId).valueOrThrow().map(NavidromeMapper::song)
                DetailPage(
                    browseId, parsed.rawId.ifBlank { fallbackTitle }, fallbackSubtitle, fallbackArtwork,
                    songsState(songs), BrowseType.OTHER,
                )
            }
            NavidromeEntity.SONG -> error("A song ID is not a browse page")
        }
    }

    /**
     * Builds a Navidrome album page with the same metadata sources as the
     * original app: server notes first, then a strictly matched editorial
     * fallback; recommendation shelves always remain server catalogue data.
     */
    private suspend fun albumDetail(
        rawId: String,
        browseId: String,
        fallbackTitle: String,
        fallbackSubtitle: String,
        fallbackArtwork: String?,
        moreFromArtistTitle: String,
        relatedAlbumsTitle: String,
    ): DetailPage = coroutineScope {
        val album = repository.album(rawId).valueOrThrow()
        val orderedSongs = album.song.withIndex()
            .sortedWith(
                compareBy<IndexedValue<NavidromeSong>> { it.value.discNumber ?: 1 }
                    .thenBy { it.value.track ?: Int.MAX_VALUE }
                    .thenBy { it.index },
            )
            .map { NavidromeMapper.song(it.value) }
        val artistId = album.artistId
        val albumInfo = async {
            (repository.albumInfo2(rawId) as? NavidromeApiResult.Success)?.value
        }
        val sameArtistAlbums = async {
            artistId?.let { id ->
                (repository.artist(id) as? NavidromeApiResult.Success)?.value?.album.orEmpty()
            }.orEmpty()
        }
        val relatedAlbums = async {
            val similar = artistId?.let { id ->
                (repository.artistInfo2(id) as? NavidromeApiResult.Success)?.value?.similarArtist
            }.orEmpty()
            similar.take(5).map { relatedArtist ->
                async {
                    (repository.artist(relatedArtist.id) as? NavidromeApiResult.Success)
                        ?.value?.album.orEmpty()
                }
            }.awaitAll().flatten()
        }
        val notes = albumInfo.await()?.notes?.takeIf(String::isNotBlank)
        val editorialDescription = if (notes == null) {
            withTimeoutOrNull(2_000L) {
                val accountKey = (NavidromeStore.state.value as? NavidromeStore.State.Configured)
                    ?.config?.accountKey ?: "unconfigured"
                NavidromeAlbumEnrichment.description(
                    cacheId = "$accountKey:$rawId",
                    title = album.displayName,
                    albumArtist = album.artist.orEmpty(),
                    tracks = orderedSongs.map(Song::title).toSet(),
                )
            }
        } else {
            null
        }
        val albumId = album.id
        val withoutCurrent: (List<NavidromeAlbum>) -> List<NavidromeAlbum> = { candidates ->
            candidates.asSequence()
                .filterNot { it.id == albumId }
                .distinctBy(NavidromeAlbum::id)
                .take(10)
                .toList()
        }
        val artistName = album.artist.orEmpty()
        val sections = buildList {
            withoutCurrent(sameArtistAlbums.await()).takeIf { it.isNotEmpty() && artistName.isNotBlank() }
                ?.let { add(albumShelf(moreFromArtistTitle.format(artistName), it)) }
            withoutCurrent(relatedAlbums.await()).takeIf { it.isNotEmpty() }
                ?.let { add(albumShelf(relatedAlbumsTitle, it)) }
        }
        DetailPage(
            browseId = browseId,
            title = album.displayName.ifBlank { fallbackTitle },
            subtitle = listOfNotNull(
                album.artist?.takeIf(String::isNotBlank) ?: fallbackSubtitle.takeIf(String::isNotBlank),
                album.year?.toString(),
            ).joinToString(" • "),
            thumbnailUrl = album.coverArt?.let(NavidromeArtwork::uri) ?: fallbackArtwork,
            songs = songsState(orderedSongs),
            type = BrowseType.ALBUM,
            sections = sections,
            description = notes ?: editorialDescription,
        )
    }

    /**
     * Opens an artist that reached the UI through an external metadata card.
     * The external browse id is deliberately not reused for catalogue loading:
     * names are resolved against this Navidrome account before the page is
     * built, so its songs and releases can never come from another service.
     */
    suspend fun artistDetailByName(
        artistName: String,
        fallbackTitle: String,
        fallbackSubtitle: String,
        fallbackArtwork: String?,
        albumSectionTitle: String = "",
        singleSectionTitle: String = "Singles",
        epSectionTitle: String = "EPs",
        relatedArtistsTitle: String = "Related artists",
    ): Result<DetailPage> = runCatching {
        val needle = artistName.trim()
        require(needle.isNotEmpty()) { "Artist name is required" }
        val artist = repository.artists().valueOrThrow()
            .firstOrNull { it.name.equals(needle, ignoreCase = true) }
            ?: error("Artist not found in Navidrome: $artistName")
        artistDetail(
            rawId = artist.id,
            browseId = NavidromeIds.artist(artist.id),
            fallbackTitle = fallbackTitle,
            fallbackSubtitle = fallbackSubtitle,
            fallbackArtwork = fallbackArtwork,
            albumSectionTitle = albumSectionTitle,
            singleSectionTitle = singleSectionTitle,
            epSectionTitle = epSectionTitle,
            relatedArtistsTitle = relatedArtistsTitle,
        )
    }

    private suspend fun artistDetail(
        rawId: String,
        browseId: String,
        fallbackTitle: String,
        fallbackSubtitle: String,
        fallbackArtwork: String?,
        albumSectionTitle: String,
        singleSectionTitle: String,
        epSectionTitle: String,
        relatedArtistsTitle: String,
    ): DetailPage = coroutineScope {
        val artist = repository.artist(rawId).valueOrThrow()
        // Artist enrichment is optional in Navidrome: it needs the server's
        // external integrations, so a missing endpoint must not turn a usable
        // local artist page into an error state.
        val info = async {
            (repository.artistInfo2(rawId) as? NavidromeApiResult.Success)?.value
        }
        // Navidrome returns the artist's complete release list. Keep the
        // newest release first so the artist page's existing TopReleaseCard
        // can represent the actual latest album, EP, or single from this
        // server rather than an arbitrary shelf item.
        val albums = artist.album.sortedWith(
            compareByDescending<NavidromeAlbum> { it.year ?: Int.MIN_VALUE }
                .thenByDescending { it.created.orEmpty() },
        )
        val songs = albums.map { album ->
            async { repository.album(album.id).valueOrThrow().song.map(NavidromeMapper::song) }
        }.awaitAll().flatten().distinctBy(Song::videoId)
        val externalInfo = info.await()
        // Navidrome itself does not expose monthly listeners. The integration
        // used by the original artist page supplies only presentation facts
        // (photo, bio and listener count) after confirming a matching release;
        // catalogue tracks and releases stay entirely server-native.
        val enrichment = withTimeoutOrNull(2_000L) {
            val accountKey = (NavidromeStore.state.value as? NavidromeStore.State.Configured)
                ?.config
                ?.accountKey
                ?: "unconfigured"
            NavidromeArtistEnrichment.find(
                cacheId = "$accountKey:$rawId",
                name = artist.name,
                albums = albums.map(NavidromeAlbum::displayName).toSet(),
                tracks = emptySet(),
            )
        }
        val related = externalInfo?.similarArtist
            .orEmpty()
            .asSequence()
            .filterNot { it.id == rawId }
            .distinctBy(NavidromeArtist::id)
            .map { relatedArtist ->
                ShelfItem(
                    title = relatedArtist.name,
                    subtitle = relatedArtist.albumCount?.toString().orEmpty(),
                    thumbnailUrl = relatedArtist.coverArt?.let(NavidromeArtwork::uri)
                        ?: relatedArtist.artistImageUrl,
                    videoId = null,
                    browseId = NavidromeIds.artist(relatedArtist.id),
                )
            }
            .toList()
        DetailPage(
            browseId = browseId,
            title = artist.name.ifBlank { fallbackTitle },
            subtitle = fallbackSubtitle,
            // Match the original Navidrome page: getArtistInfo2 is the
            // artist-specific image, while coverArt can merely be a release
            // sleeve. Keeping this order makes direct/history opens resolve
            // to the same artist photograph.
            thumbnailUrl = externalInfo?.largeImageUrl
                ?: externalInfo?.mediumImageUrl
                ?: artist.coverArt?.let(NavidromeArtwork::uri)
                ?: artist.artistImageUrl
                ?: enrichment?.image
                ?: fallbackArtwork,
            songs = songsState(songs),
            type = BrowseType.ARTIST,
            sections = artistReleaseShelves(
                albums = albums,
                albumTitle = albumSectionTitle,
                singleTitle = singleSectionTitle,
                epTitle = epSectionTitle,
            ),
            relatedArtists = related.takeIf { it.isNotEmpty() }
                ?.let { HomeShelf(relatedArtistsTitle, it) },
            description = externalInfo?.biography ?: enrichment?.description,
            monthlyListenerCount = enrichment?.monthlyListeners,
        )
    }

    suspend fun playlists(): Result<List<UserPlaylist>> = runCatching {
        repository.playlists().valueOrThrow().map(NavidromeMapper::userPlaylist)
    }

    /**
     * Server-native AutoPlay fallback. It deliberately stays within the active
     * Navidrome account rather than using the dormant YouTube radio route.
     */
    suspend fun randomSongs(existingIds: Set<String>, limit: Int): Result<List<Song>> = runCatching {
        repository.randomSongs(size = (limit * 3).coerceIn(1, 500)).valueOrThrow()
            .map(NavidromeMapper::song)
            .filterNot { it.videoId in existingIds }
            .take(limit)
    }

    /** Returns synchronized server lyrics only when the advertised extension supports them. */
    suspend fun lyrics(songId: String): Result<List<LyricLine>?> = runCatching {
        val raw = requireEntity(songId, NavidromeEntity.SONG)
        val accountKey = (NavidromeStore.state.value as? NavidromeStore.State.Configured)
            ?.config?.accountKey
            ?: error("Navidrome is not configured")
        val response = NavidromeLyricsCache.read(accountKey, raw)
            ?: repository.lyricsBySongId(raw).valueOrThrow().also {
                NavidromeLyricsCache.write(accountKey, raw, it)
            }
        // The current lyrics surface consumes timestamps. Do not invent them
        // for an unsynchronised response: it would make every line appear as
        // though it were timed data. Those responses can still fall through
        // to the existing plain/external providers.
        NavidromeLyricsParser.parse(response)
    }

    suspend fun setStarred(songId: String, starred: Boolean): Result<Unit> = runCatching {
        val raw = requireEntity(songId, NavidromeEntity.SONG)
        if (starred) repository.starSong(raw).valueOrThrow() else repository.unstarSong(raw).valueOrThrow()
    }

    suspend fun createPlaylist(
        title: String,
        songId: String? = null,
        isPublic: Boolean = false,
    ): Result<UserPlaylist> = runCatching {
        val created = repository.createPlaylist(title, isPublic).valueOrThrow()
        songId?.let { repository.addToPlaylist(created.id, requireEntity(it, NavidromeEntity.SONG)).valueOrThrow() }
        NavidromeMapper.userPlaylist(created)
    }

    suspend fun renamePlaylist(playlistId: String, title: String): Result<Unit> = runCatching {
        repository.renamePlaylist(playlistId, title).valueOrThrow()
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> = runCatching {
        repository.deletePlaylist(playlistId).valueOrThrow()
    }

    suspend fun addToPlaylist(playlistId: String, songId: String): Result<Unit> = runCatching {
        repository.addToPlaylist(playlistId, requireEntity(songId, NavidromeEntity.SONG)).valueOrThrow()
    }

    suspend fun removeFromPlaylist(playlistId: String, songId: String): Result<Unit> = runCatching {
        val raw = requireEntity(songId, NavidromeEntity.SONG)
        val playlist = repository.playlist(playlistId).valueOrThrow()
        val index = playlist.entry.indexOfFirst { it.id == raw }
        require(index >= 0) { "Song is not in this playlist" }
        repository.removeFromPlaylist(playlistId, index).valueOrThrow()
    }

    suspend fun reorderPlaylist(
        playlistId: String,
        currentSize: Int,
        targetSongIds: List<String>,
    ): Result<Unit> = runCatching {
        require(currentSize >= 0) { "Playlist size must not be negative" }
        // The sheet may have been open while another client edited the same
        // playlist. Read the server's order before changing it: removing by
        // the stale UI count can otherwise leave a prefix behind, which makes
        // an apparently saved drag revert as soon as Library refreshes.
        val serverEntries = repository.playlist(playlistId).valueOrThrow().entry
        repeat(serverEntries.size) { offset ->
            repository.removeFromPlaylist(playlistId, serverEntries.size - offset - 1).valueOrThrow()
        }
        targetSongIds.forEach { songId ->
            repository.addToPlaylist(playlistId, requireEntity(songId, NavidromeEntity.SONG)).valueOrThrow()
        }
        // `updatePlaylist` succeeds even when an integration silently drops a
        // mutation. Do not tell the UI the order was saved until the server
        // returns precisely the requested sequence (including duplicates).
        val persisted = repository.playlist(playlistId).valueOrThrow().entry.map { it.id }
        val expected = targetSongIds.map { requireEntity(it, NavidromeEntity.SONG) }
        check(persisted == expected) { "Navidrome did not persist the requested playlist order" }
    }

    private fun songsState(songs: List<Song>): UiState<List<Song>> =
        if (songs.isEmpty()) UiState.Error("") else UiState.Success(songs)

    private fun albumShelf(title: String, albums: List<NavidromeAlbum>) = HomeShelf(
        title,
        albums.map { album ->
            ShelfItem(
                album.displayName,
                listOfNotNull(
                    album.artist?.takeIf(String::isNotBlank),
                    album.year?.toString(),
                ).joinToString(" · "),
                album.coverArt?.let(NavidromeArtwork::uri),
                null,
                NavidromeIds.album(album.id),
            )
        },
    )

    /**
     * OpenSubsonic exposes every release type attached to an album. A release
     * must occupy exactly one carousel, so the client applies this priority:
     * Single > EP > Album. Secondary descriptors such as `live` or
     * `compilation` never create another copy. Thus `single, live` belongs to
     * Singles, and an untagged release stays in Albums.
     */
    private fun artistReleaseShelves(
        albums: List<NavidromeAlbum>,
        albumTitle: String,
        singleTitle: String,
        epTitle: String,
    ): List<HomeShelf> {
        // Do not use a callable reference to this member extension: Kotlin
        // rejects `NavidromeAlbum::releaseBucket` for a member extension.
        val grouped = albums.groupBy { album -> album.releaseBucket() }
        return listOf(
            albumShelf(albumTitle, grouped[NavidromeReleaseBucket.ALBUM].orEmpty()),
            albumShelf(singleTitle, grouped[NavidromeReleaseBucket.SINGLE].orEmpty()),
            albumShelf(epTitle, grouped[NavidromeReleaseBucket.EP].orEmpty()),
        ).filter { it.items.isNotEmpty() }
    }

    private enum class NavidromeReleaseBucket {
        ALBUM,
        SINGLE,
        EP,
    }

    private fun NavidromeAlbum.releaseBucket(): NavidromeReleaseBucket {
        val tags = releaseTypes
            .asSequence()
            .flatMap { it.split(',', ';').asSequence() }
            .map { it.trim().lowercase() }
            .filter(String::isNotEmpty)
            .toSet()
        return when {
            "single" in tags -> NavidromeReleaseBucket.SINGLE
            "ep" in tags || "extended play" in tags -> NavidromeReleaseBucket.EP
            else -> NavidromeReleaseBucket.ALBUM
        }
    }

    private fun artistShelf(title: String, artists: List<NavidromeArtist>) = HomeShelf(
        title,
        artists.map { artist ->
            ShelfItem(
                artist.name,
                // `getArtist` may include releases while an artist list only
                // contains Navidrome's aggregate albumCount. Whenever the
                // releases are available, derive this number from the same
                // bucket rule as the page so Singles and EPs never inflate the
                // Albums count presented by the app.
                artist.album
                    .takeIf { it.isNotEmpty() }
                    ?.count { it.releaseBucket() == NavidromeReleaseBucket.ALBUM }
                    ?.toString()
                    ?: artist.albumCount?.toString().orEmpty(),
                artist.coverArt?.let(NavidromeArtwork::uri) ?: artist.artistImageUrl,
                null,
                NavidromeIds.artist(artist.id),
            )
        },
    )

    private fun playlistShelf(title: String, playlists: List<NavidromePlaylist>) = HomeShelf(
        title,
        playlists.map { playlist ->
            ShelfItem(
                playlist.name,
                playlist.owner.orEmpty(),
                playlist.coverArt?.let(NavidromeArtwork::uri),
                null,
                NavidromeIds.playlist(playlist.id),
            )
        },
    )

    private fun songShelf(title: String, songs: List<Song>) = HomeShelf(
        title,
        songs.map { song ->
            ShelfItem(
                title = song.title,
                subtitle = song.artist,
                thumbnailUrl = song.thumbnailUrl,
                videoId = song.videoId,
                browseId = null,
                isExplicit = song.isExplicit,
            )
        },
    )

    private fun requireEntity(value: String, entity: NavidromeEntity): String {
        val parsed = NavidromeIds.parse(value)
        require(parsed?.entity == entity) { "Expected ${entity.name.lowercase()} ID" }
        return parsed.rawId
    }
}

internal object AppNavidromeCatalogue {
    val current: NavidromeCatalogue
        get() = NavidromeCatalogue(
            NavidromeRepository(config = {
                (NavidromeStore.state.value as? NavidromeStore.State.Configured)?.config
            }),
        )
}

internal class NavidromeCatalogueException(message: String) : Exception(message)

private fun <T> NavidromeApiResult<T>.valueOrThrow(): T = when (this) {
    is NavidromeApiResult.Success -> value
    is NavidromeApiResult.InvalidEndpoint -> throw NavidromeCatalogueException(reason)
    is NavidromeApiResult.Unreachable -> throw NavidromeCatalogueException(reason)
    is NavidromeApiResult.InvalidCredentials -> throw NavidromeCatalogueException(reason)
    is NavidromeApiResult.UnsupportedAuthentication -> throw NavidromeCatalogueException(reason)
    is NavidromeApiResult.Forbidden -> throw NavidromeCatalogueException(reason)
    is NavidromeApiResult.NotFound -> throw NavidromeCatalogueException(reason)
    is NavidromeApiResult.InvalidResponse -> throw NavidromeCatalogueException(reason)
}
