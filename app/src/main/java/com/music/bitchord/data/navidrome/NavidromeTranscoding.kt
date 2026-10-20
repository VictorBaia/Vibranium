package com.music.bitchord.data.navidrome

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/** Exact server-side FFmpeg targets offered by the Navidrome settings page. */
internal enum class NavidromeTranscodeProfile(
    val format: String?,
    val maxBitRateKbps: Int?,
    val extension: String?,
    val mimeType: String?,
) {
    ORIGINAL("raw", null, null, null),
    AAC_320("aac", 320, "m4a", "audio/mp4"),
    AAC_256("aac", 256, "m4a", "audio/mp4"),
    AAC_192("aac", 192, "m4a", "audio/mp4"),
    AAC_128("aac", 128, "m4a", "audio/mp4"),
    MP3_320("mp3", 320, "mp3", "audio/mpeg"),
    MP3_256("mp3", 256, "mp3", "audio/mpeg"),
    MP3_192("mp3", 192, "mp3", "audio/mpeg"),
    MP3_128("mp3", 128, "mp3", "audio/mpeg"),
    MP3_64("mp3", 64, "mp3", "audio/mpeg"),
    ;

    companion object {
        fun fromWire(format: String?, maxBitRateKbps: Int?): NavidromeTranscodeProfile? =
            entries.firstOrNull { it.format == format && it.maxBitRateKbps == maxBitRateKbps }
    }
}

/** Non-secret Navidrome-only preferences; they never affect the legacy source. */
internal object NavidromeTranscoding {
    private lateinit var prefs: SharedPreferences
    val wifi = MutableStateFlow(NavidromeTranscodeProfile.ORIGINAL)
    val cellular = MutableStateFlow(NavidromeTranscodeProfile.AAC_320)
    val download = MutableStateFlow(NavidromeTranscodeProfile.ORIGINAL)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("navidrome_transcoding", Context.MODE_PRIVATE)
        wifi.value = read(KEY_WIFI, NavidromeTranscodeProfile.ORIGINAL)
        cellular.value = read(KEY_CELLULAR, NavidromeTranscodeProfile.AAC_320)
        download.value = read(KEY_DOWNLOAD, NavidromeTranscodeProfile.ORIGINAL)
    }

    fun effective(isMetered: Boolean?): NavidromeTranscodeProfile =
        if (isMetered == true) cellular.value else wifi.value

    fun setWifi(value: NavidromeTranscodeProfile) = set(KEY_WIFI, value) { wifi.value = it }
    fun setCellular(value: NavidromeTranscodeProfile) = set(KEY_CELLULAR, value) { cellular.value = it }
    fun setDownload(value: NavidromeTranscodeProfile) = set(KEY_DOWNLOAD, value) { download.value = it }

    private fun read(key: String, default: NavidromeTranscodeProfile) =
        prefs.getString(key, null)?.let { runCatching { NavidromeTranscodeProfile.valueOf(it) }.getOrNull() } ?: default

    private fun set(key: String, value: NavidromeTranscodeProfile, publish: (NavidromeTranscodeProfile) -> Unit) {
        publish(value)
        prefs.edit().putString(key, value.name).apply()
    }

    private const val KEY_WIFI = "wifi"
    private const val KEY_CELLULAR = "cellular"
    private const val KEY_DOWNLOAD = "download"
}
