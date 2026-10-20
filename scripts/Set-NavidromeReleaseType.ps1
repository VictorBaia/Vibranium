[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string[]]$AlbumPath,

    [Parameter(Mandatory)]
    [ValidateSet("album", "single", "ep")]
    [string]$ReleaseType,

    # Preview is the default. This explicit switch is required before a file is changed.
    [switch]$Apply
)

$ErrorActionPreference = "Stop"
$audioExtensions = ".flac", ".mp3", ".m4a", ".mp4", ".ogg", ".opus", ".ape", ".wv"

if ($Apply -and -not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
    throw "ffmpeg was not found. Install FFmpeg on the server host, then retry."
}

$files = foreach ($path in $AlbumPath) {
    $resolved = Resolve-Path -LiteralPath $path
    Get-ChildItem -LiteralPath $resolved -File -Recurse |
        Where-Object { $_.Extension.ToLowerInvariant() -in $audioExtensions }
}

if (-not $files) {
    throw "No supported audio files were found under the supplied album path(s)."
}

Write-Host ("{0} file(s) will receive release type '{1}'." -f $files.Count, $ReleaseType)
if (-not $Apply) {
    Write-Host "Simulation only: no metadata has been changed. Re-run with -Apply to write tags."
}

foreach ($file in $files) {
    $tagName = switch ($file.Extension.ToLowerInvariant()) {
        ".flac" { "RELEASETYPE" }
        ".ogg" { "RELEASETYPE" }
        ".opus" { "RELEASETYPE" }
        ".mp3" { "MusicBrainz Album Type" }
        ".m4a" { "MusicBrainz Album Type" }
        ".mp4" { "MusicBrainz Album Type" }
        ".ape" { "MUSICBRAINZ_ALBUMTYPE" }
        ".wv" { "MUSICBRAINZ_ALBUMTYPE" }
        default { throw "Unsupported extension: $($file.Extension)" }
    }
    Write-Host ("[{0}] {1}={2}" -f $file.FullName, $tagName, $ReleaseType)

    if ($Apply) {
        $temporary = Join-Path $file.DirectoryName (".{0}.{1}.tmp{2}" -f $file.BaseName, [guid]::NewGuid(), $file.Extension)
        $backup = "$($file.FullName).pre-release-type"
        & ffmpeg -nostdin -hide_banner -loglevel error -y -i $file.FullName -map 0 -c copy `
            -map_metadata 0 "-metadata" "$tagName=$ReleaseType" $temporary
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $temporary)) {
            Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
            throw "FFmpeg could not tag '$($file.FullName)'. The original file was left untouched."
        }
        if (Test-Path -LiteralPath $backup) {
            Remove-Item -LiteralPath $temporary -Force
            throw "Backup already exists: $backup. Inspect it before retrying."
        }
        Move-Item -LiteralPath $file.FullName -Destination $backup
        Move-Item -LiteralPath $temporary -Destination $file.FullName
    }
}

if ($Apply) {
    Write-Host "Tags written. Ask Navidrome to rescan the library; originals are kept as *.pre-release-type backups."
}
