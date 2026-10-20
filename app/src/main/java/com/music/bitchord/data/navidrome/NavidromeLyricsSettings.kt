package com.music.bitchord.data.navidrome

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** Which lyrics family is the automatic provider for Navidrome tracks. */
internal enum class NavidromeLyricsMode { APP_DEFAULT, NAVIDROME }

internal object NavidromeLyricsSettings {
    val mode = MutableStateFlow(NavidromeLyricsMode.APP_DEFAULT)
    private var initialized = false
    private lateinit var preferences: android.content.SharedPreferences

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        preferences = context.applicationContext.getSharedPreferences(
            "navidrome_lyrics", Context.MODE_PRIVATE,
        )
        mode.value = runCatching {
            NavidromeLyricsMode.valueOf(
                preferences.getString(KEY_MODE, NavidromeLyricsMode.APP_DEFAULT.name)
                    ?: NavidromeLyricsMode.APP_DEFAULT.name,
            )
        }.getOrDefault(NavidromeLyricsMode.APP_DEFAULT)
    }

    fun setMode(value: NavidromeLyricsMode) {
        mode.value = value
        if (initialized) preferences.edit().putString(KEY_MODE, value.name).apply()
    }

    private const val KEY_MODE = "mode"
}
