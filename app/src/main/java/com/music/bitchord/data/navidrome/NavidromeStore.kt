package com.music.bitchord.data.navidrome

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Strict encrypted storage for Navidrome configuration.
 *
 * Unlike the legacy preference helper, this store never falls back to plain
 * SharedPreferences. When Android Keystore is unavailable the state is marked
 * unavailable and no credential is read or written.
 */
internal object NavidromeStore {
    sealed interface State {
        data object NotInitialized : State
        data object NotConfigured : State
        data class Configured(val config: NavidromeConfig) : State
        data class Unavailable(val cause: Throwable) : State
    }

    private val mutableState = MutableStateFlow<State>(State.NotInitialized)
    val state = mutableState.asStateFlow()

    private var preferences: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (mutableState.value !is State.NotInitialized) return
        val opened = runCatching { encryptedPreferences(context.applicationContext) }
        preferences = opened.getOrNull()
        mutableState.value = opened.fold(
            onSuccess = ::read,
            onFailure = State::Unavailable,
        )
    }

    @Synchronized
    fun save(config: NavidromeConfig): Result<Unit> = runCatching {
        val prefs = preferences ?: error("Secure Navidrome storage is unavailable")
        val previous = (mutableState.value as? State.Configured)?.config
        val effective = config.namespaceAfter(previous)
        val (kind, secret) = when (val credential = effective.credential) {
            is NavidromeCredential.Password -> CREDENTIAL_PASSWORD to credential.value
            is NavidromeCredential.ApiKey -> CREDENTIAL_API_KEY to credential.value
        }
        check(
            prefs.edit()
                .putString(KEY_SERVER_URL, NavidromeConfig.normalizeServerUrl(effective.serverUrl))
                .putString(KEY_USERNAME, effective.username.trim())
                .putString(KEY_CREDENTIAL_KIND, kind)
                .putString(KEY_SECRET, secret)
                .putString(KEY_ACCOUNT_KEY, effective.accountKey)
                .putBoolean(KEY_FORCE_STREAMING, effective.forceStreaming)
                .putString(KEY_PROFILE_IMAGE_URI, effective.profileImageUri)
                .putBoolean(KEY_FORCE_SYNCHRONIZATION, effective.forceSynchronization)
                .commit(),
        ) { "Could not persist encrypted Navidrome configuration" }
        mutableState.value = State.Configured(effective)
    }

    @Synchronized
    fun clear(): Result<Unit> = runCatching {
        val prefs = preferences ?: error("Secure Navidrome storage is unavailable")
        check(prefs.edit().clear().commit()) { "Could not clear encrypted Navidrome configuration" }
        mutableState.value = State.NotConfigured
    }

    private fun read(prefs: SharedPreferences): State {
        val serverUrl = prefs.getString(KEY_SERVER_URL, null) ?: return State.NotConfigured
        val username = prefs.getString(KEY_USERNAME, "").orEmpty()
        val secret = prefs.getString(KEY_SECRET, null) ?: return State.NotConfigured
        val accountKey = prefs.getString(KEY_ACCOUNT_KEY, null) ?: return State.NotConfigured
        val credential = when (prefs.getString(KEY_CREDENTIAL_KIND, null)) {
            CREDENTIAL_PASSWORD -> NavidromeAuth.password(secret)
            CREDENTIAL_API_KEY -> NavidromeAuth.apiKey(secret)
            else -> return State.NotConfigured
        }
        return runCatching {
            NavidromeConfig.create(serverUrl, username, credential, accountKey).copy(
                forceStreaming = prefs.getBoolean(KEY_FORCE_STREAMING, false),
                profileImageUri = prefs.getString(KEY_PROFILE_IMAGE_URI, "").orEmpty(),
                forceSynchronization = prefs.getBoolean(KEY_FORCE_SYNCHRONIZATION, false),
            )
        }
            .fold(onSuccess = State::Configured, onFailure = State::Unavailable)
    }

    private fun encryptedPreferences(context: Context): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            STORE_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private const val STORE_NAME = "bitchord_navidrome"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_USERNAME = "username"
    private const val KEY_CREDENTIAL_KIND = "credential_kind"
    private const val KEY_SECRET = "secret"
    private const val KEY_ACCOUNT_KEY = "account_key"
    private const val KEY_FORCE_STREAMING = "force_streaming"
    private const val KEY_PROFILE_IMAGE_URI = "profile_image_uri"
    private const val KEY_FORCE_SYNCHRONIZATION = "force_synchronization"
    private const val CREDENTIAL_PASSWORD = "password"
    private const val CREDENTIAL_API_KEY = "api_key"
}

