package com.music.bitchord.data.navidrome

import java.security.MessageDigest
import java.security.SecureRandom

internal const val NAVIDROME_API_VERSION = "1.16.1"
internal const val NAVIDROME_CLIENT_ID = "BitChord"

/** A secret used only while constructing one OpenSubsonic request. */
internal sealed interface NavidromeCredential {
    class Password internal constructor(internal val value: String) : NavidromeCredential {
        override fun toString(): String = "Password(***)"
    }

    class ApiKey internal constructor(internal val value: String) : NavidromeCredential {
        override fun toString(): String = "ApiKey(***)"
    }
}

/**
 * Builds the mandatory OpenSubsonic parameters without retaining generated
 * tokens. A fresh salt is produced for every password-authenticated request.
 */
internal object NavidromeAuth {
    private val secureRandom = SecureRandom()

    fun password(value: String): NavidromeCredential.Password {
        require(value.isNotBlank()) { "Navidrome password must not be blank" }
        return NavidromeCredential.Password(value)
    }

    fun apiKey(value: String): NavidromeCredential.ApiKey {
        require(value.isNotBlank()) { "Navidrome API key must not be blank" }
        return NavidromeCredential.ApiKey(value)
    }

    fun parameters(
        username: String,
        credential: NavidromeCredential,
        saltFactory: () -> String = ::newSalt,
    ): Map<String, String> {
        val common = linkedMapOf(
            "v" to NAVIDROME_API_VERSION,
            "c" to NAVIDROME_CLIENT_ID,
            "f" to "json",
        )
        when (credential) {
            is NavidromeCredential.Password -> {
                require(username.isNotBlank()) { "Navidrome username must not be blank" }
                val salt = saltFactory()
                require(salt.length >= 6) { "OpenSubsonic salt must contain at least six characters" }
                common["u"] = username
                common["t"] = md5(credential.value + salt)
                common["s"] = salt
            }

            is NavidromeCredential.ApiKey -> common["apiKey"] = credential.value
        }
        return common
    }

    private fun newSalt(): String {
        val bytes = ByteArray(12).also(secureRandom::nextBytes)
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
}

