package com.music.bitchord.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/** Opens a fresh server response at a media-time seek, never at a transcode byte offset. */
@UnstableApi
internal class NavidromeTranscodeDataSource(private val upstream: DataSource) : DataSource {
    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        if (uri.path?.endsWith("/stream.view") == true &&
            uri.getQueryParameter("format") in setOf("aac", "mp3") &&
            NavidromeStreaming.isSeek(dataSpec.position)
        ) {
            val seekUri = uri.buildUpon()
                .appendQueryParameter("timeOffset", NavidromeStreaming.seekSeconds(dataSpec.position).toString())
                .build()
            upstream.open(dataSpec.buildUpon().setUri(seekUri).setPosition(0L)
                .setLength(C.LENGTH_UNSET.toLong()).build())
            return C.LENGTH_UNSET.toLong()
        }
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = upstream.read(buffer, offset, length)
    override fun getUri() = upstream.uri
    override fun getResponseHeaders() = upstream.responseHeaders
    override fun close() = upstream.close()
}

@UnstableApi
internal class NavidromeTranscodeDataSourceFactory(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val upstreamFactory: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = NavidromeTranscodeDataSource(upstreamFactory.createDataSource())
}
