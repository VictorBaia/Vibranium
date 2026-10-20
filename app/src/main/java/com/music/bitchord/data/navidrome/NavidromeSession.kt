package com.music.bitchord.data.navidrome

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class NavidromeCredentialKind { PASSWORD, API_KEY }

internal class NavidromeLoginDraft(
    val serverUrl: String,
    val username: String,
    val secret: String,
    val credentialKind: NavidromeCredentialKind,
) {
    override fun toString(): String =
        "NavidromeLoginDraft(serverUrl=$serverUrl, username=$username, secret=***, credentialKind=$credentialKind)"
}

internal sealed interface NavidromeLoginResult {
    data class Connected(
        val serverType: String?,
        val serverVersion: String?,
        val openSubsonic: Boolean,
    ) : NavidromeLoginResult

    data class InvalidInput(val reason: String) : NavidromeLoginResult
    data class InvalidEndpoint(val reason: String) : NavidromeLoginResult
    data class Unreachable(val reason: String) : NavidromeLoginResult
    data class InvalidCredentials(val reason: String) : NavidromeLoginResult
    data class UnsupportedAuthentication(val reason: String) : NavidromeLoginResult
    data class Forbidden(val reason: String) : NavidromeLoginResult
    data class InvalidResponse(val reason: String) : NavidromeLoginResult
    data class SecureStorageUnavailable(val reason: String) : NavidromeLoginResult
}

internal sealed interface NavidromeSessionState {
    data object Loading : NavidromeSessionState
    data object SignedOut : NavidromeSessionState
    data class SignedIn(
        val serverUrl: String,
        val username: String,
        val credentialKind: NavidromeCredentialKind,
    ) : NavidromeSessionState
    data object SecureStorageUnavailable : NavidromeSessionState
}

/** Validates credentials against the server before anything is persisted. */
internal class NavidromeLoginValidator(
    private val transport: NavidromeTransport = NavidromeClient(),
) {
    suspend fun validate(draft: NavidromeLoginDraft): Pair<NavidromeLoginResult, NavidromeConfig?> {
        val config = runCatching {
            val credential = when (draft.credentialKind) {
                NavidromeCredentialKind.PASSWORD -> NavidromeAuth.password(draft.secret)
                NavidromeCredentialKind.API_KEY -> NavidromeAuth.apiKey(draft.secret)
            }
            NavidromeConfig.create(draft.serverUrl, draft.username, credential)
        }.getOrElse {
            return NavidromeLoginResult.InvalidInput(it.message.orEmpty()) to null
        }
        val result = when (val connection = transport.testConnection(config)) {
            is NavidromeConnectionResult.Connected -> NavidromeLoginResult.Connected(
                connection.serverType,
                connection.serverVersion,
                connection.openSubsonic,
            )
            is NavidromeConnectionResult.InvalidEndpoint -> NavidromeLoginResult.InvalidEndpoint(connection.reason)
            is NavidromeConnectionResult.Unreachable -> NavidromeLoginResult.Unreachable(connection.reason)
            is NavidromeConnectionResult.InvalidCredentials ->
                NavidromeLoginResult.InvalidCredentials(connection.reason)
            is NavidromeConnectionResult.UnsupportedAuthentication ->
                NavidromeLoginResult.UnsupportedAuthentication(connection.reason)
            is NavidromeConnectionResult.Forbidden -> NavidromeLoginResult.Forbidden(connection.reason)
            is NavidromeConnectionResult.InvalidResponse -> NavidromeLoginResult.InvalidResponse(connection.reason)
        }
        return result to config.takeIf { result is NavidromeLoginResult.Connected }
    }
}

/** Redacted, reactive configuration boundary used by the settings UI. */
internal object NavidromeSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val validator = NavidromeLoginValidator()
    private val mutableState = MutableStateFlow<NavidromeSessionState>(NavidromeSessionState.Loading)
    val state: StateFlow<NavidromeSessionState> = mutableState.asStateFlow()

    init {
        scope.launch {
            NavidromeStore.state.collect { mutableState.value = it.redacted() }
        }
    }

    suspend fun connect(draft: NavidromeLoginDraft): NavidromeLoginResult = withContext(Dispatchers.IO) {
        val (result, config) = validator.validate(draft)
        if (result !is NavidromeLoginResult.Connected || config == null) return@withContext result
        NavidromeStore.save(config).fold(
            onSuccess = { result },
            onFailure = {
                NavidromeLoginResult.SecureStorageUnavailable(
                    it.message ?: "Secure Navidrome storage is unavailable",
                )
            },
        )
    }

    suspend fun testConnection(): NavidromeLoginResult = withContext(Dispatchers.IO) {
        val config = (NavidromeStore.state.value as? NavidromeStore.State.Configured)?.config
            ?: return@withContext NavidromeLoginResult.InvalidInput("Navidrome server is not configured")
        when (val result = NavidromeRepository({ config }).connection()) {
            is NavidromeConnectionResult.Connected -> NavidromeLoginResult.Connected(
                result.serverType,
                result.serverVersion,
                result.openSubsonic,
            )
            is NavidromeConnectionResult.InvalidEndpoint -> NavidromeLoginResult.InvalidEndpoint(result.reason)
            is NavidromeConnectionResult.Unreachable -> NavidromeLoginResult.Unreachable(result.reason)
            is NavidromeConnectionResult.InvalidCredentials -> NavidromeLoginResult.InvalidCredentials(result.reason)
            is NavidromeConnectionResult.UnsupportedAuthentication ->
                NavidromeLoginResult.UnsupportedAuthentication(result.reason)
            is NavidromeConnectionResult.Forbidden -> NavidromeLoginResult.Forbidden(result.reason)
            is NavidromeConnectionResult.InvalidResponse -> NavidromeLoginResult.InvalidResponse(result.reason)
        }
    }

    fun disconnect(): Result<Unit> = NavidromeStore.clear()
}

private fun NavidromeStore.State.redacted(): NavidromeSessionState = when (this) {
    NavidromeStore.State.NotInitialized -> NavidromeSessionState.Loading
    NavidromeStore.State.NotConfigured -> NavidromeSessionState.SignedOut
    is NavidromeStore.State.Configured -> NavidromeSessionState.SignedIn(
        serverUrl = config.serverUrl,
        username = config.username,
        credentialKind = when (config.credential) {
            is NavidromeCredential.Password -> NavidromeCredentialKind.PASSWORD
            is NavidromeCredential.ApiKey -> NavidromeCredentialKind.API_KEY
        },
    )
    is NavidromeStore.State.Unavailable -> NavidromeSessionState.SecureStorageUnavailable
}
