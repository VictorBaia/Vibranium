# Navidrome release types

The artist page reads OpenSubsonic `releaseTypes` from each album returned by
`getArtist`/`getAlbum`. OpenSubsonic defines it as an optional array of strings,
so an older server or a release without the tag is deliberately treated as an
album.

## App classification

Each release is shown once only:

| Tags returned by the server | App carousel |
| --- | --- |
| contains `single` | Singles |
| otherwise contains `ep` or `extended play` | EPs |
| contains `album`, has another type only, or has no tag | Álbuns |

The priority is **single > EP > album**. Secondary values such as `live` and
`compilation` are retained by Navidrome but do not produce duplicate cards. For
example, `single,live` is a Single; `ep,live` is an EP; `album,live` is an
Album. This also means the visible album carousel excludes singles and EPs. If
an artist response includes its release list, the app derives that artist's
album count from the same rule; a compact artist-list response that contains
only Navidrome's aggregate `albumCount` necessarily keeps the server value.

## Marking the files

Navidrome's default tag aliases include `RELEASETYPE`,
`MUSICBRAINZ_ALBUMTYPE`, and the MusicBrainz Album Type forms. Use one canonical
tag per file format; do not write two conflicting values:

| File format | Canonical tag |
| --- | --- |
| FLAC, Ogg, Opus | `RELEASETYPE=single` / `ep` / `album` |
| MP3 / ID3v2 | `TXXX:MusicBrainz Album Type=single` / `ep` / `album` |
| M4A / MP4 | `----:com.apple.iTunes:MusicBrainz Album Type=single` / `ep` / `album` |
| APE / WavPack | `MUSICBRAINZ_ALBUMTYPE=single` / `ep` / `album` |

MusicBrainz Picard writes exactly these format-specific forms. Navidrome maps
them to its `releasetype` field and exposes them through OpenSubsonic as
`releaseTypes`.

## Batch script

Run the included PowerShell script on the machine that owns the music library.
It uses FFmpeg stream copy and creates a `*.pre-release-type` backup before any
replacement. The default is a simulation:

```powershell
./scripts/Set-NavidromeReleaseType.ps1 `
  -AlbumPath "D:\Music\Artist\Example Single", "D:\Music\Artist\Another Single" `
  -ReleaseType single
```

After reviewing the file list, apply it:

```powershell
./scripts/Set-NavidromeReleaseType.ps1 `
  -AlbumPath "D:\Music\Artist\Example Single" `
  -ReleaseType single `
  -Apply
```

Then trigger a Navidrome library rescan (or wait for its watcher). Confirm the
result with `getArtist` or `getAlbum`: its OpenSubsonic JSON must include
`"releaseTypes":["single"]`. A marked single will then be exclusive to the
Singles carousel and absent from Álbuns.

Sources: [OpenSubsonic AlbumID3](https://opensubsonic.netlify.app/docs/responses/albumid3/),
[Navidrome tag mapping discussion](https://github.com/navidrome/navidrome/discussions/5414),
and [MusicBrainz Picard's tag mapping](https://picard-docs.musicbrainz.org/en/latest/appendices/tag_mapping.html).
