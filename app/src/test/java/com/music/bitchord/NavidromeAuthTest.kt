package com.music.bitchord

import com.music.bitchord.data.navidrome.NAVIDROME_API_VERSION
import com.music.bitchord.data.navidrome.NavidromeAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeAuthTest {

    @Test
    fun `password authentication follows the OpenSubsonic token example`() {
        val parameters = NavidromeAuth.parameters(
            username = "joe",
            credential = NavidromeAuth.password("sesame"),
            saltFactory = { "c19b2d" },
        )

        assertEquals("joe", parameters["u"])
        assertEquals("26719a1196d2a940705a59634eb18eab", parameters["t"])
        assertEquals("c19b2d", parameters["s"])
        assertEquals(NAVIDROME_API_VERSION, parameters["v"])
        assertEquals("json", parameters["f"])
        assertFalse(parameters.containsKey("p"))
        assertFalse(parameters.containsKey("apiKey"))
    }

    @Test
    fun `api key authentication does not send conflicting identity parameters`() {
        val parameters = NavidromeAuth.parameters(
            username = "must-not-be-sent",
            credential = NavidromeAuth.apiKey("secret-api-key"),
        )

        assertEquals("secret-api-key", parameters["apiKey"])
        assertFalse(parameters.containsKey("u"))
        assertFalse(parameters.containsKey("p"))
        assertFalse(parameters.containsKey("t"))
        assertFalse(parameters.containsKey("s"))
    }

    @Test
    fun `credentials redact their string representation`() {
        assertEquals("Password(***)", NavidromeAuth.password("visible-only-in-memory").toString())
        assertEquals("ApiKey(***)", NavidromeAuth.apiKey("visible-only-in-memory").toString())
    }

    @Test
    fun `generated salts are fresh and long enough`() {
        val credential = NavidromeAuth.password("password")
        val first = NavidromeAuth.parameters("user", credential).getValue("s")
        val second = NavidromeAuth.parameters("user", credential).getValue("s")

        assertTrue(first.length >= 6)
        assertTrue(second.length >= 6)
        assertFalse(first == second)
    }
}

