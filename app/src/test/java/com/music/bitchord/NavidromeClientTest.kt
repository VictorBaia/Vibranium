package com.music.bitchord

import com.music.bitchord.data.navidrome.NavidromeApiError
import com.music.bitchord.data.navidrome.NavidromeApiResult
import com.music.bitchord.data.navidrome.NavidromeClient
import com.music.bitchord.data.navidrome.NavidromeResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeClientTest {
    private val client = NavidromeClient()

    @Test
    fun `successful response remains successful`() {
        val response = NavidromeResponse(status = "ok", version = "1.16.1", type = "navidrome")
        assertEquals(NavidromeApiResult.Success(response), client.mapApiResponse(response))
    }

    @Test
    fun `authentication error codes remain distinguishable`() {
        assertTrue(client.mapApiResponse(failed(40)) is NavidromeApiResult.InvalidCredentials)
        assertTrue(client.mapApiResponse(failed(41)) is NavidromeApiResult.UnsupportedAuthentication)
        assertTrue(client.mapApiResponse(failed(42)) is NavidromeApiResult.UnsupportedAuthentication)
        assertTrue(client.mapApiResponse(failed(43)) is NavidromeApiResult.UnsupportedAuthentication)
        assertTrue(client.mapApiResponse(failed(44)) is NavidromeApiResult.InvalidCredentials)
    }

    @Test
    fun `authorization and missing data errors remain distinguishable`() {
        assertTrue(client.mapApiResponse(failed(50)) is NavidromeApiResult.Forbidden)
        assertTrue(client.mapApiResponse(failed(70)) is NavidromeApiResult.NotFound)
        assertTrue(client.mapApiResponse(failed(0)) is NavidromeApiResult.InvalidResponse)
        assertTrue(client.mapApiResponse(NavidromeResponse("failed")) is NavidromeApiResult.InvalidResponse)
    }

    private fun failed(code: Int) = NavidromeResponse(
        status = "failed",
        error = NavidromeApiError(code, "error-$code"),
    )
}

