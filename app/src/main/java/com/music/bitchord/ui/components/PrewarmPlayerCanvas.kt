package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.music.bitchord.data.canvas.CanvasCache
import com.music.bitchord.data.canvas.CanvasRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.originalArtwork
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.player.rememberArtworkMesh
import com.music.bitchord.ui.player.rememberFullArtworkBlurImage
import com.music.bitchord.data.model.CARD_ART_PX
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Finds a clip while the compact player is visible, before the full player opens. */
@Composable
internal fun PrewarmPlayerCanvas(song: Song?) {
    val context = LocalContext.current
    val enabled by AppSettings.animatedCanvas.collectAsStateWithLifecycle()
    val cellular by AppSettings.canvasOverCellular.collectAsStateWithLifecycle()
    val metered by AppSettings.meteredConnection.collectAsStateWithLifecycle()
    // Both player backdrops use small decodes. Prepare them while the compact
    // player is visible, so opening the full player has its colours already.
    rememberArtworkMesh(song?.thumbnailUrl)
    rememberFullArtworkBlurImage(song?.thumbnailUrl)
    LaunchedEffect(song?.videoId, song?.albumArtist, song?.albumId, enabled, cellular, metered) {
        if (song != null && enabled && (metered != true || cellular)) {
            CanvasRepository.canvasFor(song)?.let { clip ->
                // Warm both renditions concurrently: if the primary cannot
                // decode on this device, the fallback is already on disk.
                coroutineScope {
                    launch { CanvasCache.prefetchStart(clip.url) }
                    clip.fallbackUrl?.let { fallback ->
                        launch { CanvasCache.prefetchStart(fallback) }
                    }
                }
            }
        }
    }
    LaunchedEffect(song?.thumbnailUrl) {
        val art = song?.thumbnailUrl.originalArtwork() ?: return@LaunchedEffect
        // The first full-player frame asks for this same original source.
        // Decode it while the compact player is already on screen.
        SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context)
                .data(art)
                .size(CARD_ART_PX)
                .diskCacheKey("artwork:original:$art")
                .build(),
        )
    }
}
