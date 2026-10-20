package com.music.bitchord.ui.screens

import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.navidrome.NavidromeCredentialKind
import com.music.bitchord.data.navidrome.NavidromeLoginDraft
import com.music.bitchord.data.navidrome.NavidromeLoginResult
import com.music.bitchord.data.navidrome.NavidromeSession
import com.music.bitchord.data.navidrome.NavidromeSessionState
import com.music.bitchord.data.navidrome.NavidromeTranscodeProfile
import com.music.bitchord.data.navidrome.NavidromeTranscoding
import com.music.bitchord.data.navidrome.NavidromeLyricsMode
import com.music.bitchord.data.navidrome.NavidromeLyricsSettings
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.canvas.CanvasCache
import com.music.bitchord.data.cache.CacheUsageEntry
import com.music.bitchord.data.navidrome.NavidromeStore
import com.music.bitchord.ui.components.ProfilePhotoCropDialog
import com.music.bitchord.playback.AudioCache
import coil3.SingletonImageLoader
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NavidromeSettingsScreen(
    contentPadding: PaddingValues,
    onForceSynchronization: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val session by NavidromeSession.state.collectAsStateWithLifecycle()
    val storeState by NavidromeStore.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLogin by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<NavidromeLoginResult?>(null) }
    val wifiQuality by NavidromeTranscoding.wifi.collectAsStateWithLifecycle()
    val cellularQuality by NavidromeTranscoding.cellular.collectAsStateWithLifecycle()
    val downloadQuality by NavidromeTranscoding.download.collectAsStateWithLifecycle()
    val lyricsMode by NavidromeLyricsSettings.mode.collectAsStateWithLifecycle()
    val cacheLimitBytes by AppSettings.audioCacheLimitBytes.collectAsStateWithLifecycle()
    val imageCacheLimitBytes by AppSettings.imageCacheLimitBytes.collectAsStateWithLifecycle()
    val videoCacheLimitBytes by AppSettings.videoCacheLimitBytes.collectAsStateWithLifecycle()
    var pickingQuality by remember { mutableStateOf<QualityTarget?>(null) }
    var pickingDownloadQuality by remember { mutableStateOf(false) }
    var confirmClearSongCache by remember { mutableStateOf(false) }
    var confirmClearImageCache by remember { mutableStateOf(false) }
    var inspectedCache by remember { mutableStateOf<CacheType?>(null) }
    var inspectedEntries by remember { mutableStateOf<List<CacheUsageEntry>>(emptyList()) }
    var inspectingCache by remember { mutableStateOf(false) }
    var pickingLyricsMode by remember { mutableStateOf(false) }
    var photoToCrop by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) photoToCrop = uri
    }

    photoToCrop?.let { source ->
        ProfilePhotoCropDialog(
            source = source,
            onDismiss = { photoToCrop = null },
            onSaved = { saved ->
                val state = NavidromeStore.state.value as? NavidromeStore.State.Configured
                state?.let { NavidromeStore.save(it.config.copy(profileImageUri = saved)) }
                photoToCrop = null
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Text(
            text = stringResource(R.string.navidrome),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
        )

        SettingsGroup(
            footer = stringResource(R.string.navidrome_login_footer),
            topSpacing = 0.dp,
        ) {
            when (val current = session) {
                NavidromeSessionState.Loading -> SettingsRow(
                    icon = Icons.Rounded.Dns,
                    title = stringResource(R.string.navidrome_server),
                    subtitle = stringResource(R.string.navidrome_loading_configuration),
                    enabled = false,
                )
                NavidromeSessionState.SignedOut -> SettingsRow(
                    icon = Icons.Rounded.Login,
                    title = stringResource(R.string.navidrome_sign_in),
                    subtitle = stringResource(R.string.navidrome_sign_in_subtitle),
                    onClick = { result = null; showLogin = true },
                )
                NavidromeSessionState.SecureStorageUnavailable -> SettingsRow(
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.navidrome_secure_storage_unavailable),
                    subtitle = stringResource(R.string.navidrome_secure_storage_unavailable_subtitle),
                    enabled = false,
                )
                is NavidromeSessionState.SignedIn -> {
                    SettingsRow(
                        icon = Icons.Rounded.CloudDone,
                        title = stringResource(R.string.navidrome_connected),
                        subtitle = current.serverUrl,
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Person,
                        title = stringResource(R.string.navidrome_username),
                        subtitle = if (current.username.isBlank()) {
                            stringResource(R.string.navidrome_api_key)
                        } else {
                            current.username
                        },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Person,
                        title = stringResource(R.string.navidrome_profile_photo),
                        subtitle = stringResource(
                            if ((storeState as? NavidromeStore.State.Configured)
                                ?.config?.profileImageUri.isNullOrBlank()) R.string.navidrome_profile_photo_unset
                            else R.string.navidrome_profile_photo_set,
                        ),
                        onClick = { photoPicker.launch(arrayOf("image/*")) },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Refresh,
                        title = stringResource(R.string.navidrome_force_sync),
                        subtitle = stringResource(R.string.navidrome_force_sync_subtitle),
                        enabled = current != NavidromeSessionState.Loading,
                        onClick = onForceSynchronization,
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Refresh,
                        title = stringResource(R.string.navidrome_test_connection),
                        subtitle = result?.localizedMessage(),
                        enabled = !busy,
                        onClick = {
                            busy = true
                            result = null
                            scope.launch {
                                result = NavidromeSession.testConnection()
                                busy = false
                            }
                        },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Key,
                        title = stringResource(R.string.navidrome_change_login),
                        onClick = { result = null; showLogin = true },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Logout,
                        title = stringResource(R.string.navidrome_sign_out),
                        onClick = { result = NavidromeSession.disconnect().fold(
                            onSuccess = { null },
                            onFailure = { NavidromeLoginResult.SecureStorageUnavailable(it.message.orEmpty()) },
                        ) },
                    )
                }
            }
        }

        // These are the same persisted preferences used by playback and
        // downloads. Keeping their Navidrome entry points here makes the active
        // source's settings complete without introducing a parallel preference.
        SettingsGroup(header = stringResource(R.string.audio_quality)) {
            SettingsRow(
                icon = Icons.Rounded.Wifi,
                title = stringResource(R.string.on_wifi),
                value = wifiQuality.localizedLabel(),
                onClick = { pickingQuality = QualityTarget.WIFI },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.SignalCellularAlt,
                title = stringResource(R.string.on_mobile_data),
                value = cellularQuality.localizedLabel(),
                onClick = { pickingQuality = QualityTarget.CELLULAR },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Download,
                title = stringResource(R.string.download_quality),
                value = downloadQuality.localizedLabel(),
                onClick = { pickingDownloadQuality = true },
            )
        }

        SettingsGroup(header = stringResource(R.string.streaming)) {
            val configured = session as? NavidromeSessionState.SignedIn
            SettingsRow(
                icon = Icons.Rounded.Waves,
                title = stringResource(R.string.force_music_streaming),
                subtitle = stringResource(R.string.force_music_streaming_description),
                trailing = {
                    Switch(
                        checked = (storeState as? NavidromeStore.State.Configured)
                            ?.config?.forceStreaming == true,
                        onCheckedChange = { checked ->
                            val state = NavidromeStore.state.value as? NavidromeStore.State.Configured
                            state?.let { NavidromeStore.save(it.config.copy(forceStreaming = checked)) }
                        },
                    )
                },
                enabled = configured != null,
            )
        }

        val cacheUnlimited = cacheLimitBytes == AppSettings.UNLIMITED_CACHE_LIMIT_BYTES
        val cacheLimitMb = if (cacheUnlimited) CACHE_UNLIMITED_STOP_MB else {
            (cacheLimitBytes / BYTES_PER_MB).toInt()
        }
        SettingsGroup(header = stringResource(R.string.storage)) {
            SliderRow(
                icon = Icons.Rounded.Image,
                title = stringResource(R.string.image_cache_limit),
                subtitle = stringResource(R.string.image_cache_limit_subtitle),
                value = formatCacheSize((imageCacheLimitBytes / BYTES_PER_MB).toInt()),
                sliderValue = (imageCacheLimitBytes / BYTES_PER_MB).toFloat(),
                onSliderValue = { value -> AppSettings.setImageCacheLimitBytes(value.toInt().toLong() * BYTES_PER_MB) },
                valueRange = (AppSettings.MIN_IMAGE_CACHE_LIMIT_BYTES / BYTES_PER_MB).toFloat()..
                    (AppSettings.MAX_IMAGE_CACHE_LIMIT_BYTES / BYTES_PER_MB).toFloat(),
                steps = 39,
            )
            RowDivider()
            SliderRow(
                icon = Icons.Rounded.VideoLibrary,
                title = stringResource(R.string.video_cache_limit),
                subtitle = stringResource(R.string.video_cache_limit_subtitle),
                value = formatCacheSize((videoCacheLimitBytes / BYTES_PER_MB).toInt()),
                sliderValue = (videoCacheLimitBytes / BYTES_PER_MB).toFloat(),
                onSliderValue = { value -> AppSettings.setVideoCacheLimitBytes(value.toInt().toLong() * BYTES_PER_MB) },
                valueRange = (AppSettings.MIN_VIDEO_CACHE_LIMIT_BYTES / BYTES_PER_MB).toFloat()..
                    (AppSettings.MAX_VIDEO_CACHE_LIMIT_BYTES / BYTES_PER_MB).toFloat(),
                steps = 39,
            )
            RowDivider()
            SliderRow(
                icon = Icons.Rounded.Storage,
                title = stringResource(R.string.song_cache_limit),
                subtitle = stringResource(R.string.song_cache_limit_subtitle),
                value = if (cacheUnlimited) stringResource(R.string.song_cache_unlimited) else formatCacheSize(cacheLimitMb),
                sliderValue = cacheLimitMb.toFloat(),
                onSliderValue = { value ->
                    AppSettings.setAudioCacheLimitBytes(value.toInt().toLong() * BYTES_PER_MB)
                },
                valueRange = MIN_CACHE_MB.toFloat()..CACHE_UNLIMITED_STOP_MB.toFloat(),
                steps = 19,
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.DeleteSweep,
                title = stringResource(R.string.clear_song_cache),
                subtitle = stringResource(R.string.clear_song_cache_subtitle),
                onClick = { confirmClearSongCache = true },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.DeleteSweep,
                title = stringResource(R.string.clear_image_cache),
                subtitle = stringResource(R.string.clear_image_cache_subtitle),
                onClick = { confirmClearImageCache = true },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Storage,
                title = stringResource(R.string.cached_audio_files),
                subtitle = stringResource(R.string.cached_files_subtitle),
                onClick = {
                    inspectedCache = CacheType.AUDIO
                    inspectedEntries = emptyList()
                    inspectingCache = true
                    scope.launch {
                        inspectedEntries = AudioCache.storageEntries()
                        inspectingCache = false
                    }
                },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.VideoLibrary,
                title = stringResource(R.string.cached_video_files),
                subtitle = stringResource(R.string.cached_files_subtitle),
                onClick = {
                    inspectedCache = CacheType.VIDEO
                    inspectedEntries = emptyList()
                    inspectingCache = true
                    scope.launch {
                        inspectedEntries = CanvasCache.storageEntries()
                        inspectingCache = false
                    }
                },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Image,
                title = stringResource(R.string.cached_image_files),
                subtitle = stringResource(R.string.cached_files_subtitle),
                onClick = {
                    inspectedCache = CacheType.IMAGE
                    inspectedEntries = emptyList()
                    inspectingCache = true
                    scope.launch {
                        inspectedEntries = imageCacheEntries(context)
                        inspectingCache = false
                    }
                },
            )
        }

        SettingsGroup(header = stringResource(R.string.synced_lyrics)) {
            SettingsRow(
                icon = Icons.Rounded.LibraryMusic,
                title = stringResource(R.string.lyrics_provider),
                value = if (lyricsMode == NavidromeLyricsMode.NAVIDROME) {
                    stringResource(R.string.navidrome)
                } else {
                    stringResource(R.string.app_default)
                },
                onClick = { pickingLyricsMode = true },
            )
        }
    }

    if (showLogin) {
        NavidromeLoginDialog(
            initialSession = session as? NavidromeSessionState.SignedIn,
            busy = busy,
            result = result,
            onDismiss = { if (!busy) showLogin = false },
            onConnect = { draft ->
                busy = true
                result = null
                scope.launch {
                    val connection = NavidromeSession.connect(draft)
                    result = connection
                    busy = false
                    if (connection is NavidromeLoginResult.Connected) showLogin = false
                }
            },
        )
    }
    pickingQuality?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { pickingQuality = null },
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            NavidromeQualitySheet(
                target = target,
                selected = if (target == QualityTarget.WIFI) wifiQuality else cellularQuality,
                onSelect = { quality ->
                    if (target == QualityTarget.WIFI) NavidromeTranscoding.setWifi(quality)
                    else NavidromeTranscoding.setCellular(quality)
                    pickingQuality = null
                },
            )
        }
    }
    if (pickingDownloadQuality) {
        ModalBottomSheet(
            onDismissRequest = { pickingDownloadQuality = false },
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            NavidromeQualitySheet(
                target = null,
                selected = downloadQuality,
                onSelect = { quality ->
                    NavidromeTranscoding.setDownload(quality)
                    pickingDownloadQuality = false
                },
            )
        }
    }
    if (pickingLyricsMode) {
        ModalBottomSheet(
            onDismissRequest = { pickingLyricsMode = false },
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.lyrics_provider),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                SettingsRow(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.app_default),
                    value = if (lyricsMode == NavidromeLyricsMode.APP_DEFAULT) "✓" else null,
                    onClick = {
                        NavidromeLyricsSettings.setMode(NavidromeLyricsMode.APP_DEFAULT)
                        pickingLyricsMode = false
                    },
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.navidrome),
                    value = if (lyricsMode == NavidromeLyricsMode.NAVIDROME) "✓" else null,
                    onClick = {
                        NavidromeLyricsSettings.setMode(NavidromeLyricsMode.NAVIDROME)
                        pickingLyricsMode = false
                    },
                )
            }
        }
    }
    if (confirmClearSongCache) {
        AlertDialog(
            onDismissRequest = { confirmClearSongCache = false },
            title = { Text(stringResource(R.string.clear_song_cache_title)) },
            text = { Text(stringResource(R.string.clear_song_cache_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearSongCache = false
                    AudioCache.clear {
                        Toast.makeText(context, context.getString(R.string.song_cache_cleared), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearSongCache = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (confirmClearImageCache) {
        AlertDialog(
            onDismissRequest = { confirmClearImageCache = false },
            title = { Text(stringResource(R.string.clear_image_cache)) },
            text = { Text(stringResource(R.string.clear_image_cache_subtitle)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearImageCache = false
                    SingletonImageLoader.get(context).also { loader ->
                        loader.memoryCache?.clear()
                        loader.diskCache?.clear()
                    }
                    CanvasCache.clear()
                    Toast.makeText(context, context.getString(R.string.image_cache_cleared), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClearImageCache = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    inspectedCache?.let { type ->
        CacheContentsDialog(
            title = when (type) {
                CacheType.AUDIO -> stringResource(R.string.cached_audio_files)
                CacheType.VIDEO -> stringResource(R.string.cached_video_files)
                CacheType.IMAGE -> stringResource(R.string.cached_image_files)
            },
            entries = inspectedEntries,
            loading = inspectingCache,
            onDismiss = { inspectedCache = null },
        )
    }
}

private enum class CacheType { AUDIO, VIDEO, IMAGE }

@Composable
private fun CacheContentsDialog(
    title: String,
    entries: List<CacheUsageEntry>,
    loading: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(formatCacheBytes(entries.sumOf { it.bytes }), style = MaterialTheme.typography.titleMedium)
                if (loading) {
                    Text("…", modifier = Modifier.padding(top = 12.dp))
                } else if (entries.isEmpty()) {
                    Text(stringResource(R.string.no_cached_files), modifier = Modifier.padding(top = 12.dp))
                } else {
                    entries.forEach { entry ->
                        Column(Modifier.padding(top = 12.dp)) {
                            Text(entry.label, maxLines = 1)
                            Text(
                                formatCacheBytes(entry.bytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private suspend fun imageCacheEntries(context: android.content.Context): List<CacheUsageEntry> =
    withContext(Dispatchers.IO) {
        val diskCache = SingletonImageLoader.get(context).diskCache ?: return@withContext emptyList()
        File(diskCache.directory.toString())
            .walkTopDown()
            .filter { it.isFile && !it.name.startsWith("journal") && !it.name.endsWith(".tmp") }
            .map { CacheUsageEntry(it.name, it.length()) }
            .filter { it.bytes > 0L }
            .sortedByDescending { it.bytes }
            .toList()
    }

private fun formatCacheBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private const val BYTES_PER_MB = 1024L * 1024L
private const val MIN_CACHE_MB = 512
private const val CACHE_UNLIMITED_STOP_MB = 12 * 1024

/** Reuses the existing settings rows inside the Navidrome-only bottom sheet. */
@Composable
private fun NavidromeQualitySheet(
    target: QualityTarget?,
    selected: NavidromeTranscodeProfile,
    onSelect: (NavidromeTranscodeProfile) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            text = stringResource(R.string.audio_quality),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 14.dp),
        )
        target?.let {
            Text(
                text = stringResource(
                    R.string.audio_quality_connection,
                    stringResource(
                        if (it == QualityTarget.WIFI) R.string.on_wifi else R.string.on_mobile_data,
                    ).lowercase(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 8.dp),
            )
        }
        NavidromeTranscodeProfile.entries.forEach { profile ->
            SettingsRow(
                icon = if (profile == NavidromeTranscodeProfile.ORIGINAL) {
                    Icons.Rounded.Storage
                } else {
                    target?.icon ?: Icons.Rounded.Download
                },
                title = profile.localizedLabel(),
                subtitle = stringResource(
                    if (profile == NavidromeTranscodeProfile.ORIGINAL) R.string.navidrome_no_transcoding
                    else R.string.navidrome_transcoded_by_server,
                ),
                trailing = { RadioButton(selected = profile == selected, onClick = null) },
                onClick = { onSelect(profile) },
            )
        }
    }
}

@Composable
private fun NavidromeTranscodeProfile.localizedLabel(): String = when (this) {
    NavidromeTranscodeProfile.ORIGINAL -> stringResource(R.string.navidrome_original_file)
    NavidromeTranscodeProfile.AAC_320, NavidromeTranscodeProfile.AAC_256,
    NavidromeTranscodeProfile.AAC_192, NavidromeTranscodeProfile.AAC_128 ->
        stringResource(R.string.navidrome_quality_aac, requireNotNull(maxBitRateKbps))
    else -> stringResource(R.string.navidrome_quality_mp3, requireNotNull(maxBitRateKbps))
}

@Composable
private fun NavidromeLoginDialog(
    initialSession: NavidromeSessionState.SignedIn?,
    busy: Boolean,
    result: NavidromeLoginResult?,
    onDismiss: () -> Unit,
    onConnect: (NavidromeLoginDraft) -> Unit,
) {
    var serverUrl by remember { mutableStateOf(initialSession?.serverUrl.orEmpty()) }
    var username by remember { mutableStateOf(initialSession?.username.orEmpty()) }
    var secret by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(initialSession?.credentialKind ?: NavidromeCredentialKind.PASSWORD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.navidrome_sign_in)) },
        text = {
            Column {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text(stringResource(R.string.navidrome_server_url)) },
                    placeholder = { Text(stringResource(R.string.navidrome_server_url_example)) },
                    enabled = !busy,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsRow(
                    icon = Icons.Rounded.Person,
                    title = stringResource(R.string.navidrome_password),
                    trailing = { RadioButton(selected = kind == NavidromeCredentialKind.PASSWORD, onClick = null) },
                    enabled = !busy,
                    onClick = { kind = NavidromeCredentialKind.PASSWORD },
                )
                SettingsRow(
                    icon = Icons.Rounded.Key,
                    title = stringResource(R.string.navidrome_api_key),
                    trailing = { RadioButton(selected = kind == NavidromeCredentialKind.API_KEY, onClick = null) },
                    enabled = !busy,
                    onClick = { kind = NavidromeCredentialKind.API_KEY },
                )
                if (kind == NavidromeCredentialKind.PASSWORD) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.navidrome_username)) },
                        enabled = !busy,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text(stringResource(
                        if (kind == NavidromeCredentialKind.PASSWORD) R.string.navidrome_password
                        else R.string.navidrome_api_key,
                    )) },
                    enabled = !busy,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                result?.let {
                    Text(
                        text = it.localizedMessage(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && serverUrl.isNotBlank() && secret.isNotBlank() &&
                    (kind == NavidromeCredentialKind.API_KEY || username.isNotBlank()),
                onClick = { onConnect(NavidromeLoginDraft(serverUrl, username, secret, kind)) },
            ) {
                Text(stringResource(if (busy) R.string.navidrome_connecting else R.string.navidrome_connect))
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun NavidromeLoginResult.localizedMessage(): String = when (this) {
    is NavidromeLoginResult.Connected -> stringResource(R.string.navidrome_connection_success)
    is NavidromeLoginResult.InvalidInput -> stringResource(R.string.navidrome_invalid_input)
    is NavidromeLoginResult.InvalidEndpoint -> stringResource(R.string.navidrome_invalid_endpoint)
    is NavidromeLoginResult.Unreachable -> stringResource(R.string.navidrome_unreachable)
    is NavidromeLoginResult.InvalidCredentials -> stringResource(R.string.navidrome_invalid_credentials)
    is NavidromeLoginResult.UnsupportedAuthentication -> stringResource(R.string.navidrome_unsupported_authentication)
    is NavidromeLoginResult.Forbidden -> stringResource(R.string.navidrome_forbidden)
    is NavidromeLoginResult.InvalidResponse -> stringResource(R.string.navidrome_invalid_response)
    is NavidromeLoginResult.SecureStorageUnavailable ->
        stringResource(R.string.navidrome_secure_storage_unavailable)
}
