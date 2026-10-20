package com.music.bitchord.data.navidrome

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import java.io.IOException

internal interface NavidromeTransport {
    suspend fun testConnection(config: NavidromeConfig): NavidromeConnectionResult
    suspend fun request(
        config: NavidromeConfig,
        method: String,
        parameters: Map<String, String> = emptyMap(),
    ): NavidromeApiResult<NavidromeResponse>
}

internal class NavidromeClient(
    private val http: HttpClient = defaultHttpClient(),
) : NavidromeTransport {
    override suspend fun testConnection(config: NavidromeConfig): NavidromeConnectionResult {
        return when (val result = request(config, "ping")) {
            is NavidromeApiResult.Success -> mapConnectionResponse(result.value)
            is NavidromeApiResult.InvalidEndpoint -> NavidromeConnectionResult.InvalidEndpoint(result.reason)
            is NavidromeApiResult.Unreachable -> NavidromeConnectionResult.Unreachable(result.reason)
            is NavidromeApiResult.InvalidCredentials -> NavidromeConnectionResult.InvalidCredentials(result.reason)
            is NavidromeApiResult.UnsupportedAuthentication ->
                NavidromeConnectionResult.UnsupportedAuthentication(result.reason)
            is NavidromeApiResult.Forbidden -> NavidromeConnectionResult.Forbidden(result.reason)
            is NavidromeApiResult.NotFound -> NavidromeConnectionResult.InvalidEndpoint(result.reason)
            is NavidromeApiResult.InvalidResponse -> NavidromeConnectionResult.InvalidResponse(result.reason)
        }
    }

    override suspend fun request(
        config: NavidromeConfig,
        method: String,
        parameters: Map<String, String>,
    ): NavidromeApiResult<NavidromeResponse> {
        val endpoint = runCatching { config.endpoint(method) }.getOrElse {
            return NavidromeApiResult.InvalidEndpoint(it.message ?: "Invalid server endpoint")
        }
        return try {
            val form = Parameters.build {
                NavidromeAuth.parameters(config.username, config.credential).forEach { (key, value) ->
                    append(key, value)
                }
                parameters.forEach { (key, value) -> append(key, value) }
            }
            val httpResponse = http.submitForm(endpoint.toString(), form)
            when (httpResponse.status) {
                HttpStatusCode.Unauthorized ->
                    NavidromeApiResult.InvalidCredentials("Server rejected the credentials")
                HttpStatusCode.Forbidden -> NavidromeApiResult.Forbidden("Server denied this operation")
                HttpStatusCode.NotFound -> NavidromeApiResult.NotFound("OpenSubsonic endpoint was not found")
                else -> if (httpResponse.status.value !in 200..299) {
                    NavidromeApiResult.Unreachable("Server returned HTTP ${httpResponse.status.value}")
                } else {
                    mapApiResponse(httpResponse.body<NavidromeEnvelope>().response)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (network: IOException) {
            NavidromeApiResult.Unreachable(network.message ?: "Could not reach the server")
        } catch (invalid: Throwable) {
            NavidromeApiResult.InvalidResponse(invalid.message ?: "Invalid server response")
        }
    }

    internal fun mapApiResponse(response: NavidromeResponse): NavidromeApiResult<NavidromeResponse> {
        if (response.status.equals("ok", ignoreCase = true)) {
            return NavidromeApiResult.Success(response)
        }
        val error = response.error
            ?: return NavidromeApiResult.InvalidResponse("Failed response did not contain an error")
        val reason = error.message ?: "OpenSubsonic error ${error.code}"
        return when (error.code) {
            40, 44 -> NavidromeApiResult.InvalidCredentials(reason)
            41, 42, 43 -> NavidromeApiResult.UnsupportedAuthentication(reason)
            50 -> NavidromeApiResult.Forbidden(reason)
            70 -> NavidromeApiResult.NotFound(reason)
            else -> NavidromeApiResult.InvalidResponse(reason)
        }
    }

    private fun mapConnectionResponse(response: NavidromeResponse) =
        NavidromeConnectionResult.Connected(
            serverType = response.type,
            serverVersion = response.serverVersion,
            openSubsonic = response.openSubsonic == true,
        )

    companion object {
        private fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) {
            expectSuccess = false
            install(ContentNegotiation) { json(NavidromeJson.parser) }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
        }
    }
}

