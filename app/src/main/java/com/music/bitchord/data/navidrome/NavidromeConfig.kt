package com.music.bitchord.data.navidrome

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

internal data class NavidromeConfig(
    val serverUrl: String,
    val username: String,
    val credential: NavidromeCredential,
    /** Random, non-secret namespace rotated whenever saved credentials change. */
    val accountKey: String,
    val forceStreaming: Boolean = false,
    val profileImageUri: String = "",
    val forceSynchronization: Boolean = false,
) {
    fun baseUrl(): HttpUrl = normalizeServerUrl(serverUrl).toHttpUrlOrNull()
        ?: throw NavidromeConfigException("Invalid Navidrome server URL")

    fun endpoint(method: String): HttpUrl {
        require(method.matches(ENDPOINT_NAME)) { "Invalid OpenSubsonic endpoint name" }
        return baseUrl().newBuilder()
            .addPathSegment("rest")
            .addPathSegment("$method.view")
            .build()
    }

    fun namespaceAfter(previous: NavidromeConfig?): NavidromeConfig =
        if (previous != null && sameIdentityAs(previous)) copy(accountKey = previous.accountKey)
        else copy(accountKey = UUID.randomUUID().toString())

    private fun sameIdentityAs(other: NavidromeConfig): Boolean {
        if (serverUrl != other.serverUrl || username != other.username) return false
        val first = credential
        val second = other.credential
        return when {
            first is NavidromeCredential.Password && second is NavidromeCredential.Password ->
                first.value == second.value
            first is NavidromeCredential.ApiKey && second is NavidromeCredential.ApiKey ->
                first.value == second.value
            else -> false
        }
    }

    companion object {
        private val ENDPOINT_NAME = Regex("[A-Za-z][A-Za-z0-9]*")

        fun create(
            serverUrl: String,
            username: String,
            credential: NavidromeCredential,
            accountKey: String = UUID.randomUUID().toString(),
        ): NavidromeConfig {
            val normalized = normalizeServerUrl(serverUrl)
            if (credential is NavidromeCredential.Password) {
                require(username.isNotBlank()) { "Navidrome username must not be blank" }
            }
            require(accountKey.isNotBlank()) { "Navidrome account namespace must not be blank" }
            return NavidromeConfig(normalized, username.trim(), credential, accountKey)
        }

        fun normalizeServerUrl(value: String): String {
            val candidate = value.trim().trimEnd('/')
            val parsed = candidate.toHttpUrlOrNull()
                ?: throw NavidromeConfigException("Invalid Navidrome server URL")
            if (parsed.scheme != "http" && parsed.scheme != "https") {
                throw NavidromeConfigException("Navidrome server must use HTTP or HTTPS")
            }
            if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
                throw NavidromeConfigException("Credentials must not be embedded in the server URL")
            }
            if (parsed.query != null || parsed.fragment != null) {
                throw NavidromeConfigException("Navidrome server URL must not contain a query or fragment")
            }
            return parsed.toString().trimEnd('/')
        }
    }
}

internal class NavidromeConfigException(message: String) : IllegalArgumentException(message)

