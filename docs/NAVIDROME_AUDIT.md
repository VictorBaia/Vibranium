# Navidrome integration audit

This document records the pre-implementation audit required by `spec.md`. It is
also the upstream-merge checklist for the fork: new behavior should live in new
packages, while existing files receive only explicit routing hooks.

## Baseline

- Branch: `up-1.8`.
- No Navidrome, Subsonic, or OpenSubsonic implementation exists yet.
- The working tree already contained a user-owned change in
  `app/src/main/cpp/CMakeLists.txt`; the Navidrome work must not alter it.
- `spec.md` is an untracked user-provided specification and is treated as the
  source of absolute project rules.
- Android already depends on Ktor, kotlinx.serialization, OkHttp/Media3 and
  `androidx.security:security-crypto`; no new HTTP or secure-storage dependency
  is needed for the foundation.

## Existing extension seam

The project already has a source abstraction under
`com.music.bitchord.data.sources`:

- `MusicSource` exposes health, search and stream resolution.
- `SourceRegistry` stores encrypted source configuration and publishes it via
  `MutableStateFlow`.
- `SourceResolver` chooses a source and maps the current network quality to a
  per-request `StreamRequest`.
- `SourceKind` defines ordering and capabilities.

This seam is useful for stream resolution, but is intentionally too narrow for
Navidrome Home, albums, artists, playlists, favourites and genres. Navidrome
therefore needs a dedicated repository plus a top-level catalogue router. It
must not be forced into `MusicSource` as the only abstraction.

## Required new packages

New implementation should be isolated under
`com.music.bitchord.data.navidrome`:

- configuration and encrypted credential store;
- OpenSubsonic authentication and request builder;
- HTTP client and response/error models;
- stable Navidrome ID codec;
- repository and mapping to the existing UI models;
- random account-scoped cache namespace, rotated when server, user or
  credential changes and never derived from a credential.

A separate source-mode router should own the decision between Navidrome and the
legacy YouTube repository. Navidrome is the default and the two catalogues must
never be combined on one screen.

## Security contract

- Store server URL, username and secret only in encrypted preferences backed by
  Android Keystore.
- Treat password and API key as mutually exclusive authentication modes.
- For password authentication, generate a fresh salt per request and send the
  OpenSubsonic token `md5(password + salt)`; never persist that token.
- For API-key authentication, send `apiKey` without `u`, `p`, `t` or `s`.
- Persist only `bitchord://navidrome/<songId>` in player queues. Authenticated
  HTTP stream URLs are request-scoped values.
- Never include credentials, authentication query parameters or authenticated
  URLs in logs, exceptions, download records, cache keys or databases.

## Error model

The transport and repository layers must distinguish:

- invalid or missing endpoint;
- unreachable server / timeout / TLS failure;
- invalid response;
- invalid credentials (OpenSubsonic errors 40 and 44);
- unsupported authentication (41, 42 and 43);
- forbidden operation (50);
- empty library (successful response with no content).

The OpenSubsonic API version targeted by Navidrome is `1.16.1`. Connection
testing uses `/rest/ping.view` with JSON output.

## Existing files and planned routing hooks

No large file is to be edited until the exact function below is reached in its
implementation phase.

### `MainViewModel.kt`

Planned touch points:

- Home/loading functions currently calling `YtMusicRepository`;
- search source selection and search execution;
- library, likes and playlist mutations;
- `loadLyrics` and provider dispatch when Navidrome lyrics are added.

Do not rewrite its state model. Map Navidrome responses into existing `Song`,
`Album`, artist, playlist and shelf models.

### `PlaybackService.kt`

Planned touch points:

- resolving data-source setup that turns internal media URIs into streams;
- Android Auto cache/fetch helpers for Home, recents, liked songs and playlists;
- `onGetChildren`, `onGetItem`, `onPlaybackResumption`, `onSearch`,
  `onGetSearchResult`, `resolveMediaItems` and `searchAll`;
- playback/scrobble hooks only where source identity affects behavior.

The queue must continue to contain stable internal IDs, never authenticated
URLs. Stream quality is selected when a not-yet-opened item is resolved.

### `MainActivity.kt`

Planned touch points:

- settings sub-screen routing for the one allowed Navidrome group/screen;
- Home, Search, Library and detail data callbacks currently bound directly to
  YouTube repository operations;
- feature visibility gates for Google login, subscribe, video, YouTube radio,
  YouTube moods/history and Listen Together.

No visual component, spacing, typography, animation or theme code may change.

### Other integration points

- `AppSettings.kt`: only non-secret source mode and existing user preferences;
  Navidrome secrets belong to the new encrypted store.
- `SettingsSheet.kt`: add the allowed Navidrome group using existing
  `SearchableSettingsGroup`, `SettingsGroup` and row components.
- `AudioCache.kt`: account + Navidrome song ID + quality cache identity and
  complete-file validation.
- `Downloads.kt`, `DownloadStore.kt`, `Downloader.kt`: source-specific records,
  resumable temporary authentication and isolation from YouTube downloads.
- `Autoplay.kt`: Navidrome fallback chain; never seed YouTube radio for a
  Navidrome item.
- `CanvasRepository.kt`: retain provider logic but match from title, artist and
  album without requiring a YouTube ID.
- `PlayerConnection.kt`, widgets and Android Auto: recognize Navidrome IDs and
  retain existing controls.
- lyrics repository: add Navidrome as a provider while preserving request
  identity/cancellation already present in `MainViewModel`.

## Implementation gates

1. Foundation: HTTP/auth models, URL normalization, error mapping, encrypted
   configuration and unit tests.
2. Identity and routing: Navidrome IDs, model mapping and source-mode router.
3. Configuration UI: existing settings components only, connection test and
   reactive session invalidation.
4. Catalogue: Home, library, search, likes, playlists, detail pages and genres.
5. Playback: internal URI, request-time authentication and quality selection.
6. Cache/queue/downloads: account and quality isolation, integrity validation,
   resume, offline playback and restoration.
7. Lyrics/artwork/artists/autoplay.
8. Android Auto/widgets/scrobbling/statistics.
9. Feature gates, performance work and full regression testing.

Each gate requires successful automated tests and a build before the next gate.
Device-only behavior must remain explicitly pending until tested on an APK or
physical/emulated device.

## Gate 1 result: foundation

Status: complete in source and unit-tested on 2026-10-05.

Implemented:

- strict encrypted configuration storage backed by Android Keystore, with no
  plaintext fallback;
- password authentication using a fresh per-request OpenSubsonic salt/token;
- API-key authentication without conflicting username/password parameters;
- API requests sent as form-encoded POST bodies instead of authenticated query
  strings;
- URL normalization and rejection of embedded credentials, queries and
  fragments;
- typed connection/API failures for endpoint, transport, credentials,
  unsupported authentication, forbidden access, missing data and invalid
  responses;
- OpenSubsonic models and repository calls for ping, current user, extensions,
  artists, artist details, album lists, album details, song details, playlists,
  playlist details, genres and search;
- canonical `nd:` entity IDs and credential-free
  `bitchord://navidrome/<songId>` playback URIs;
- random account cache namespace preserved for the same identity and rotated
  when server, user, credential type or credential value changes;
- encrypted preference exclusion from Android cloud backup and device transfer;
- background initialization in `BitChordApplication` alongside the existing
  encrypted source registry.

Validation:

- all production foundation files compile with the Android Studio Kotlin
  compiler, Android API 36, the project Ktor/OkHttp/serialization versions and
  the Android security library;
- all foundation tests compile with the serialization compiler plugin;
- 21 JUnit tests pass, covering authentication vectors, API-key isolation,
  fresh salts, secret redaction, URL validation, cache namespace rotation,
  entity/playback IDs, protocol error mapping, repository extraction and
  paging, API-key user discovery, unknown JSON fields, and textual/native
  OpenSubsonic booleans.

Environment limitation:

- the Gradle wrapper cannot start its local daemon on this Windows host because
  every installed JDK fails with `Unable to establish loopback connection`.
  The failure occurs before project configuration. Compilation and JUnit were
  therefore executed directly with Android Studio's bundled Kotlin compiler
  and JBR. A normal Gradle build and APK/device check remain required as part of
  the later integration gate; this is an environment limitation, not a failing
  foundation test.

## Gate 2 result: identity and routing

Status: complete in source and unit-tested on 2026-10-06.

Implemented:

- Navidrome is the default catalogue mode through the `NAVIDROME_MODE` build
  flag; setting the flag to false keeps the upstream YouTube implementation
  available for an explicit legacy build;
- one catalogue router selects exactly one source for an operation and never
  falls back to or combines the inactive source after an error;
- common search and user-playlist operations are exposed through a gateway
  backed by isolated Navidrome and YouTube adapters;
- Navidrome protocol songs, artists, albums, playlists and search results map
  into the existing shared UI models without creating or changing a visual
  component;
- `UserPlaylist` gained one optional source browse ID. Its default keeps the
  exact upstream YouTube `VL` behavior, while Navidrome playlists use an
  `nd:playlist:` identity;
- song, album, artist, playlist, playback and artwork identifiers remain in
  credential-free Navidrome namespaces;
- source validation rejects an entity ID from the inactive catalogue;
- server/account configuration changes rotate a routing generation and expose
  only the random account namespace, allowing future screen/cache consumers to
  discard stale work without exposing credentials;
- the process router starts only after encrypted storage initialization.

Files newly added for this gate are isolated in
`com.music.bitchord.data.catalog`; existing-file edits are limited to the build
flag, application initialization, and the backward-compatible shared playlist
identity seam. No layout, Compose screen, player, `MainViewModel`,
`PlaybackService`, or `MainActivity` file was changed.

Validation:

- five router/gateway JUnit tests pass, covering default source choice, the
  opt-in legacy source, no fallback, one-source dispatch, ID isolation and
  reactive account generation;
- seven mapper/ID JUnit tests pass, covering song fields, duration/quality,
  search filters, playlist browse identity and safe playback/artwork URIs;
- production router/gateway and mapper subsets compile directly with the
  Android Studio Kotlin compiler;
- `git diff --check` passes for integration-owned files. The only reported
  line-ending warning belongs to the pre-existing user change in
  `app/src/main/cpp/CMakeLists.txt`;
- the normal Gradle task is still blocked before project configuration by the
  host's `Unable to establish loopback connection` failure. APK/device and
  screenshot regression checks therefore remain integration-gate work.

## Gate 3 result: configuration and login

Status: complete in source and unit-tested on 2026-10-06.

Implemented:

- a Navidrome group in the existing Settings page, using the existing
  `SearchableSettingsGroup` and `SettingsRow` visual language;
- a Navidrome configuration subpage assembled from the existing
  `SettingsGroup`, `SettingsRow`, `AlertDialog` and `OutlinedTextField`
  components, without changing shared layout, typography, colors, shapes,
  animations or theme behavior;
- login by server URL, username and password, plus the already-supported
  OpenSubsonic API-key mode;
- connection validation before persistence: rejected, malformed, unreachable,
  forbidden or incompatible configurations are never written;
- redacted reactive session state for the UI. The password/API key is never
  exposed through that state, and the draft's string representation masks its
  secret;
- encrypted persistence only through `NavidromeStore`; Android Keystore
  failure remains a terminal visible state with no plaintext fallback;
- test connection, change login and sign-out actions. Successful saves and
  clears flow through `NavidromeStore.state`, rotating the catalogue router's
  account generation when identity changes;
- Google account/login and Listen Together rows are hidden when the
  `NAVIDROME_MODE` build flag is enabled. Their implementation remains intact
  for an explicit legacy build;
- all new visible strings in English, generic Portuguese and explicit
  Brazilian Portuguese resources.

Existing-file edits for this gate are limited to the Settings callback/group,
four isolated settings-navigation branches in `MainActivity.kt`, string
resources and this audit. The session logic and Navidrome page live in new
files. `MainViewModel.kt` and `PlaybackService.kt` were not changed.

Validation:

- five login-validator JUnit tests pass: successful password login, rejected
  credentials, validation without network access for invalid input, API-key
  login without username and secret redaction;
- the production session/login layer compiles directly against the tested
  foundation classes;
- the new Compose page passes Kotlin frontend/type analysis against the
  project's existing settings components and Compose dependencies;
- English, Portuguese and Brazilian Portuguese XML files parse successfully,
  and every Brazilian Portuguese key has a base-English counterpart;
- `git diff --check` passes for the gate's files;
- the normal Gradle build remains blocked before project configuration by the
  host-wide `Unable to establish loopback connection` error. Consequently an
  APK/device screenshot comparison cannot be produced on this host; this is
  still explicitly pending rather than reported as passed.

## Gate 4 result: server catalogue

Status: complete in source on 2026-10-06; Gradle verification is pending only
because the host cannot establish the loopback connection required to start
Gradle.

Implemented:

- Home shelves backed exclusively by Navidrome newest, recently played,
  frequently played and random album lists;
- Explore backed by server genres, with genre pages populated by
  `getSongsByGenre`;
- server search for songs, albums and artists, with unsupported YouTube video
  and playlist filters removed while Navidrome mode is active;
- album, artist, playlist and genre detail pages mapped into the existing
  `DetailPage`, `HomeShelf`, `ShelfItem` and `Song` models;
- Library backed by `getStarred2`, server artists and server playlists;
- song favourite/unfavourite and complete server-playlist create, seed, add,
  remove, rename, reorder and delete paths;
- Navidrome playlists are immediately editable because the authenticated
  server list is the ownership boundary;
- disconnected Home and Library actions route to the existing Navidrome
  settings page instead of opening Google login;
- YouTube-only save-release and subscribe actions are hidden behind the build
  flag. Their upstream implementations remain untouched and available in the
  legacy build;
- all user-visible catalogue labels were added in English, generic Portuguese
  and Brazilian Portuguese resources.

New production code for the gate is isolated in
`com.music.bitchord.data.navidrome.NavidromeCatalogue`. Existing-file edits are
routing hooks in `MainViewModel.kt` and `MainActivity.kt`, one optional search
filter-list parameter in the existing shared search screen, protocol response
fields and localized resources. `PlaybackService.kt` and visual component
implementations were not changed.

Validation:

- repository tests cover the documented wrappers and request parameters for
  favourites, genre songs and all playlist mutations;
- serialization tests cover `starred2` and `songsByGenre` payloads;
- `git diff --check` passes for integration files; its only warning is the
  pre-existing user-owned `app/src/main/cpp/CMakeLists.txt` line ending;
- Gradle stops before project configuration with the host-wide
  `Unable to establish loopback connection` exception, so APK/device and visual
  regression checks remain for the integration environment.

## Gate 5 result: basic playback and authenticated artwork

Status: complete in source on 2026-10-06; local Gradle verification is pending
because this host still fails before project configuration with
`Unable to establish loopback connection`.

Implemented:

- Navidrome songs enter Media3 as credential-free
  `bitchord://navidrome/<songId>` items while retaining their `nd:` media IDs,
  metadata, queue order and artwork identity;
- the playback service recognizes that internal URI and creates a fresh
  authenticated `stream` request only when Media3 opens the item;
- the request uses the currently effective Wi-Fi/mobile quality: raw for
  Lossless and explicit 320/192/64 kbps ceilings for High/Medium/Low;
- authenticated stream URLs are never placed in a `MediaItem`, queue snapshot,
  cache metadata, application log or catalogue model;
- Navidrome requests carry a non-secret in-memory source marker so the shared
  ranged data source suppresses exception text and never forwards an
  authenticated URL to the legacy YouTube refusal tracker;
- Navidrome audio bypasses the legacy audio cache until Gate 6 can key and
  validate it by account, song and quality. This prevents cross-account or
  cross-quality reuse while preserving range-based network seek;
- Navidrome cover-art IDs are resolved by a dedicated Coil fetcher. Its cache
  identity includes the random, non-secret account namespace, while the real
  authenticated `getCoverArt` URL exists only for the network request;
- artwork responses are bounded to 25 MiB and a stale artwork identity from a
  different account is rejected;
- YouTube AutoPlay exits immediately for a Navidrome item, and the explicit
  YouTube radio and unsupported dislike actions are hidden in Navidrome mode.

New production code is confined to `NavidromeMediaResolver.kt` and
`NavidromeArtworkFetcher.kt`. Existing-file changes are narrow routing hooks in
`PlayerConnection.kt`, the stream resolver and AutoPlay guard in
`PlaybackService.kt`, the cache bypass in `AudioCache.kt`, the Coil component
registration in `BitChordApplication.kt`, and nullable visibility callbacks in
the existing song-actions sheet and its single caller. No player layout,
animation, typography, colour, shape or control was changed.

Validation:

- unit tests cover internal playback URI persistence, media-ID recovery,
  YouTube identity exclusion, quality parameters, password non-disclosure,
  fresh request salts and artwork account isolation;
- official OpenSubsonic `stream` and `getCoverArt` contracts were used for
  request parameters and binary response handling;
- the media URL/profile and Coil fetcher production subsets compile directly
  with Android Studio's Kotlin compiler against the project's OkHttp, Okio and
  Coil 3.0.4 artifacts;
- `git diff --check` passes for integration-owned files; the only warning is
  the pre-existing user-owned `app/src/main/cpp/CMakeLists.txt` line ending;
- APK tests still required on the integration machine: play/pause, seek,
  next/previous, background playback, FLAC/MP3/AAC, server transcoding and
  authenticated cover rendering.

## Gate 6 result: account-safe cache, queue restoration and offline downloads

Status: complete in source on 2026-10-06; APK/device verification remains
pending because Gradle on this host fails before Kotlin compilation with
`Unable to establish loopback connection`.

Implemented:

- each queued Navidrome item receives a credential-free request identity;
  streaming quality is selected when Media3 first opens that identity and stays
  pinned for its seeks/reopens, while unopened queue entries read the latest
  Wi-Fi/mobile quality;
- audio cache keys are `account namespace + Navidrome song ID + quality` and
  never contain server URLs, usernames, tokens, salts or passwords;
- Navidrome cache metadata records only the internal `nd:` ID, non-secret
  account namespace, quality and existing display metadata. Entries from a
  different account are neither reused nor exposed by the cached-songs view;
- speculative YouTube read-ahead is explicitly bypassed for `nd:` IDs. Normal
  playback still fills the Media3 cache, including partial spans used by seek,
  but partial cache entries are never advertised as offline downloads;
- persisted queues continue to contain only songs/internal IDs plus a random
  non-secret account namespace. A Navidrome queue is restored only when that
  namespace matches the active account; legacy snapshots without a namespace
  are rejected instead of being opened against a different server;
- Navidrome downloads use a newly authenticated request for every connection,
  select raw source files for Lossless and MP3 transcoding for Standard/High,
  keep source-specific format mapping out of the legacy YouTube/source route,
  and add a non-secret account suffix to filenames to prevent cross-account
  adoption or overwrite;
- direct downloads require an advertised file length, verify the completed byte
  count, remain hidden in the existing pending destination until metadata and
  artwork are written, and abort/delete the incomplete destination on failure;
- interrupted Navidrome transfers retry with a fresh authenticated URL and
  continue through HTTP `Range` only when the server returns `206` for the same
  total length;
- active Navidrome downloads can be paused and resumed from the existing song
  action and download-manager rows. Pausing preserves progress and the pending
  file; cancellation still removes both state and incomplete data;
- saved download records persist internal ID, local URI, metadata, format,
  quality label, date and non-secret account namespace only. Authenticated URLs
  remain request-local and Navidrome exceptions are redacted before logging.

Existing-file edits remain routing/storage hooks in `PlayerConnection.kt`,
`PlaybackService.kt`, `AudioCache.kt`, `LastPlayed.kt`, `Downloads.kt`,
`Downloader.kt`, `DownloadStore.kt`, `DownloadSession.kt`, and the two existing
download action sheets. New source-specific logic is in
`NavidromePlaybackContext.kt` and `NavidromeQueueIdentity.kt`, with media URL and
format policy in `NavidromeMediaResolver.kt`. No player or catalogue layout was
changed.

Validation:

- direct Kotlin 2.3.20 compilation passes for the Navidrome media/queue
  identity subset, `LastPlayed`, `AudioCache`, and the complete download core;
- 7 Navidrome ID/account-isolation tests and 20 download-session tests pass via
  JUnit 4.13.2, including paused progress remaining active;
- resource XML parses successfully with no duplicate string names in English,
  Portuguese or Brazilian Portuguese;
- the security audit finds no persistence or logging path for authenticated
  stream/download URLs;
- `git diff --check` passes for integration-owned files; its only warning is
  the pre-existing user-owned `app/src/main/cpp/CMakeLists.txt` line ending;
- device checks still required: cache miss/partial/full, quality change in an
  existing queue, process restart, seek, pause long enough to force a ranged
  reconnect, cancellation cleanup, offline playback and account switching.

Known behavior: Navidrome speculative next-track byte prefetch is intentionally
disabled. Playback caching and Media3 buffering remain active; this avoids ever
routing an `nd:` ID through YouTube or assuming range/content-length behavior a
server has not demonstrated.

## Gate 7 result: lyrics, artwork, artist enrichment and autoplay

Status: complete in source on 2026-10-07; APK/device verification remains
pending because Gradle on this host fails before project configuration with
`Unable to establish loopback connection`.

Implemented:

- catalogue artwork continues to use only the server-supplied `coverArt` ID;
  the existing authenticated Coil fetcher resolves that ID at request time,
  retaining account-separated cache identity and never fabricating an album or
  artist ID as a cover-art ID;
- optional artist biography and external artist image enrichment via
  `getArtistInfo2`; a missing integration or unsupported endpoint leaves the
  local artist page usable;
- synchronized server lyrics through `getLyricsBySongId`, and only when the
  server advertises the `songLyrics` extension. Unsynchronised lines are not
  given invented timestamps and instead preserve the existing provider fallback;
- account-native AutoPlay through `getRandomSongs`, with duplicate queue IDs
  excluded. A Navidrome seed never calls YouTube radio;
- the existing Navidrome settings subpage now exposes the persisted Wi-Fi,
  mobile-data and download-quality selectors, audio-cache limit, clear-song
  cache and clear-image-cache actions using the existing settings components;
- no nonfunctional Force streaming or image-cache-size controls were added:
  there is no corresponding persisted setting or resolver/cache behavior to
  wire safely yet.

Existing-file edits are limited to the declared lyric dispatch in
`MainViewModel.kt`, the declared autoplay fill routine in `PlaybackService.kt`,
the existing shared settings helpers' visibility, and the allowed Navidrome
settings subpage. Protocol and catalogue logic remain under
`com.music.bitchord.data.navidrome`.

Validation:

- serialization and repository tests cover `getRandomSongs`,
  `getLyricsBySongId` and `getArtistInfo2` response shapes and request wrappers;
- the project resource XML is checked for the reused settings strings, and no
  visible strings were added by this gate;
- device checks still required: artwork on home/search/album/artist surfaces,
  artwork after account switch, embedded versus server lyrics, no-lyrics
  fallback, and AutoPlay after the explicit queue ends.

## Validation matrix

- connection: unset, malformed URL, offline, timeout, bad TLS, bad credentials,
  unsupported auth, successful ping;
- account changes: invalidate session, isolate old cache, retain data until
  confirmation and refresh all catalogue surfaces;
- streaming: FLAC, MP3, AAC, transcode, range/seek, network change and quality
  change during an existing queue;
- cache: miss, partial, complete, corrupt, wrong quality, wrong account and app
  restart;
- downloads: queued, active, paused, resumed, failed, cancelled, invalid,
  deleted and offline playback;
- race safety: stale lyrics, artwork, metadata and recommendation responses;
- regression: screenshots before/after for every touched screen and no visual
  delta outside the allowed Navidrome settings group.

