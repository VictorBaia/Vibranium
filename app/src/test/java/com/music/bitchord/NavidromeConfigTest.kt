package com.music.bitchord

import com.music.bitchord.data.navidrome.NavidromeAuth
import com.music.bitchord.data.navidrome.NavidromeConfig
import com.music.bitchord.data.navidrome.NavidromeConfigException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeConfigTest {

    @Test
    fun `normalizes the server and builds a REST endpoint`() {
        val config = NavidromeConfig.create(
            " https://music.example.test/base/ ",
            " user ",
            NavidromeAuth.password("password"),
        )

        assertEquals("https://music.example.test/base", config.serverUrl)
        assertEquals("user", config.username)
        assertEquals("https://music.example.test/base/rest/ping.view", config.endpoint("ping").toString())
    }

    @Test
    fun `rejects credentials embedded in the server URL`() {
        assertThrows(NavidromeConfigException::class.java) {
            NavidromeConfig.normalizeServerUrl("https://user:password@music.example.test")
        }
    }

    @Test
    fun `rejects query and fragment in the server URL`() {
        assertThrows(NavidromeConfigException::class.java) {
            NavidromeConfig.normalizeServerUrl("https://music.example.test?token=secret")
        }
        assertThrows(NavidromeConfigException::class.java) {
            NavidromeConfig.normalizeServerUrl("https://music.example.test/#settings")
        }
    }

    @Test
    fun `account cache namespace contains no credential and rotates with configuration`() {
        val first = NavidromeConfig.create(
            "https://music.example.test",
            "one",
            NavidromeAuth.password("first-secret"),
        )
        val changedSecret = NavidromeConfig.create(
            "https://music.example.test",
            "one",
            NavidromeAuth.password("second-secret"),
        )
        val otherUser = NavidromeConfig.create(
            "https://music.example.test",
            "two",
            NavidromeAuth.password("first-secret"),
        )

        assertFalse(first.accountKey == changedSecret.accountKey)
        assertFalse(first.accountKey == otherUser.accountKey)
        assertTrue(first.accountKey.matches(Regex("[0-9a-f-]{36}")))
        assertFalse(first.accountKey.contains("first-secret"))
    }

    @Test
    fun `unchanged identity preserves namespace while credential change rotates it`() {
        val previous = NavidromeConfig.create(
            "https://music.example.test",
            "one",
            NavidromeAuth.password("first-secret"),
            accountKey = "existing-scope",
        )
        val unchanged = NavidromeConfig.create(
            "https://music.example.test",
            "one",
            NavidromeAuth.password("first-secret"),
        ).namespaceAfter(previous)
        val changed = NavidromeConfig.create(
            "https://music.example.test",
            "one",
            NavidromeAuth.password("new-secret"),
        ).namespaceAfter(previous)

        assertEquals("existing-scope", unchanged.accountKey)
        assertFalse(changed.accountKey == previous.accountKey)
    }
}

