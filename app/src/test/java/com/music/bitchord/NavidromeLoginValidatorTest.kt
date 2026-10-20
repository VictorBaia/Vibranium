package com.music.bitchord

import com.music.bitchord.data.navidrome.NavidromeApiResult
import com.music.bitchord.data.navidrome.NavidromeConfig
import com.music.bitchord.data.navidrome.NavidromeConnectionResult
import com.music.bitchord.data.navidrome.NavidromeCredentialKind
import com.music.bitchord.data.navidrome.NavidromeLoginDraft
import com.music.bitchord.data.navidrome.NavidromeLoginResult
import com.music.bitchord.data.navidrome.NavidromeLoginValidator
import com.music.bitchord.data.navidrome.NavidromeResponse
import com.music.bitchord.data.navidrome.NavidromeTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeLoginValidatorTest {
    @Test
    fun `valid credentials are tested before configuration is returned`() = runBlocking {
        val transport = LoginTransport(NavidromeConnectionResult.Connected("navidrome", "0.54", true))
        val (result, config) = NavidromeLoginValidator(transport).validate(
            NavidromeLoginDraft(
                serverUrl = "https://music.example.test/",
                username = " listener ",
                secret = "secret",
                credentialKind = NavidromeCredentialKind.PASSWORD,
            ),
        )

        assertTrue(result is NavidromeLoginResult.Connected)
        assertEquals("https://music.example.test", config?.serverUrl)
        assertEquals("listener", config?.username)
        assertEquals(1, transport.calls)
    }

    @Test
    fun `rejected credentials never return persistable configuration`() = runBlocking {
        val transport = LoginTransport(NavidromeConnectionResult.InvalidCredentials("rejected"))
        val (result, config) = NavidromeLoginValidator(transport).validate(
            NavidromeLoginDraft(
                "https://music.example.test",
                "listener",
                "wrong",
                NavidromeCredentialKind.PASSWORD,
            ),
        )

        assertTrue(result is NavidromeLoginResult.InvalidCredentials)
        assertNull(config)
        assertEquals(1, transport.calls)
    }

    @Test
    fun `invalid input is rejected without a network request`() = runBlocking {
        val transport = LoginTransport(NavidromeConnectionResult.Connected(null, null, true))
        val (result, config) = NavidromeLoginValidator(transport).validate(
            NavidromeLoginDraft("not a URL", "", "", NavidromeCredentialKind.PASSWORD),
        )

        assertTrue(result is NavidromeLoginResult.InvalidInput)
        assertNull(config)
        assertEquals(0, transport.calls)
    }

    @Test
    fun `API key login does not require a username`() = runBlocking {
        val transport = LoginTransport(NavidromeConnectionResult.Connected("navidrome", null, true))
        val (result, config) = NavidromeLoginValidator(transport).validate(
            NavidromeLoginDraft(
                "https://music.example.test",
                "",
                "api-secret",
                NavidromeCredentialKind.API_KEY,
            ),
        )

        assertTrue(result is NavidromeLoginResult.Connected)
        assertEquals("", config?.username)
    }

    @Test
    fun `login draft never prints its secret`() {
        val draft = NavidromeLoginDraft(
            "https://music.example.test",
            "listener",
            "do-not-log-this",
            NavidromeCredentialKind.PASSWORD,
        )

        assertTrue(!draft.toString().contains("do-not-log-this"))
        assertTrue(draft.toString().contains("secret=***"))
    }

    private class LoginTransport(
        private val connection: NavidromeConnectionResult,
    ) : NavidromeTransport {
        var calls = 0

        override suspend fun testConnection(config: NavidromeConfig): NavidromeConnectionResult {
            calls++
            return connection
        }

        override suspend fun request(
            config: NavidromeConfig,
            method: String,
            parameters: Map<String, String>,
        ): NavidromeApiResult<NavidromeResponse> = error("Not used")
    }
}
