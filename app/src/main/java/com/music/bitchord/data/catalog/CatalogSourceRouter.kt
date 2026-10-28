package com.music.bitchord.data.catalog

import com.music.bitchord.data.navidrome.NavidromeIds
import com.music.bitchord.data.navidrome.NavidromeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal enum class CatalogSourceMode { NAVIDROME, YOUTUBE }

internal enum class NavidromeAvailability {
    NOT_INITIALIZED,
    NOT_CONFIGURED,
    READY,
    SECURE_STORAGE_UNAVAILABLE,
}

internal sealed interface CatalogConfigurationState {
    data object NotInitialized : CatalogConfigurationState
    data object NotConfigured : CatalogConfigurationState
    data class Configured(val accountKey: String) : CatalogConfigurationState
    data object SecureStorageUnavailable : CatalogConfigurationState
}

internal data class CatalogRoutingState(
    val mode: CatalogSourceMode,
    val navidromeAvailability: NavidromeAvailability,
    val accountKey: String?,
    val generation: Long,
)

/**
 * The single source-selection boundary. It intentionally has no fallback:
 * failure from the selected source is returned to the caller as-is.
 */
internal class CatalogSourceRouter(
    navidromeEnabled: Boolean,
    configurationState: StateFlow<CatalogConfigurationState>,
    scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(
        routingState(navidromeEnabled, configurationState.value, generation = 0),
    )
    val state: StateFlow<CatalogRoutingState> = mutableState.asStateFlow()

    private val configurationJob: Job = scope.launch {
        configurationState.collect(::onConfigurationChanged)
    }

    suspend fun <T> route(
        navidrome: suspend () -> T,
        youtube: suspend () -> T,
    ): T = when (state.value.mode) {
        CatalogSourceMode.NAVIDROME -> navidrome()
        CatalogSourceMode.YOUTUBE -> youtube()
    }

    fun requireActiveId(id: String): String {
        val validNavidromeId = NavidromeIds.parse(id) != null
        val accepted = when (state.value.mode) {
            CatalogSourceMode.NAVIDROME -> validNavidromeId
            CatalogSourceMode.YOUTUBE -> !id.startsWith(NAVIDROME_NAMESPACE)
        }
        check(accepted) {
            "Entity ID belongs to an inactive catalogue source"
        }
        return id
    }

    fun close() = configurationJob.cancel()

    private fun onConfigurationChanged(configuration: CatalogConfigurationState) {
        val previous = mutableState.value
        val next = routingState(previous.mode == CatalogSourceMode.NAVIDROME, configuration, previous.generation)
        val identityChanged = previous.accountKey != next.accountKey ||
            previous.navidromeAvailability != next.navidromeAvailability
        mutableState.value = next.copy(
            generation = if (identityChanged) previous.generation + 1 else previous.generation,
        )
    }

    private companion object {
        const val NAVIDROME_NAMESPACE = "nd:"

        fun routingState(
            navidromeEnabled: Boolean,
            configuration: CatalogConfigurationState,
            generation: Long,
        ): CatalogRoutingState = CatalogRoutingState(
            mode = if (navidromeEnabled) CatalogSourceMode.NAVIDROME else CatalogSourceMode.YOUTUBE,
            navidromeAvailability = when (configuration) {
                CatalogConfigurationState.NotInitialized -> NavidromeAvailability.NOT_INITIALIZED
                CatalogConfigurationState.NotConfigured -> NavidromeAvailability.NOT_CONFIGURED
                is CatalogConfigurationState.Configured -> NavidromeAvailability.READY
                CatalogConfigurationState.SecureStorageUnavailable -> NavidromeAvailability.SECURE_STORAGE_UNAVAILABLE
            },
            accountKey = (configuration as? CatalogConfigurationState.Configured)?.accountKey,
            generation = generation,
        )
    }
}

/** Process-wide router observes secure Navidrome storage as it finishes opening. */
internal object AppCatalogRouter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val configurationState = MutableStateFlow<CatalogConfigurationState>(
        CatalogConfigurationState.NotInitialized,
    )
    private var router: CatalogSourceRouter? = null
    private var storeBinding: Job? = null

    val current: CatalogSourceRouter
        get() = checkNotNull(router) { "Catalogue router has not been initialized" }

    @Synchronized
    fun init(navidromeEnabled: Boolean, storeState: StateFlow<NavidromeStore.State>) {
        router?.close()
        storeBinding?.cancel()
        configurationState.value = storeState.value.toCatalogConfigurationState()
        router = CatalogSourceRouter(navidromeEnabled, configurationState, scope)
        storeBinding = scope.launch {
            storeState.collect { configurationState.value = it.toCatalogConfigurationState() }
        }
    }
}

private fun NavidromeStore.State.toCatalogConfigurationState(): CatalogConfigurationState = when (this) {
    NavidromeStore.State.NotInitialized -> CatalogConfigurationState.NotInitialized
    NavidromeStore.State.NotConfigured -> CatalogConfigurationState.NotConfigured
    is NavidromeStore.State.Configured -> CatalogConfigurationState.Configured(config.accountKey)
    is NavidromeStore.State.Unavailable -> CatalogConfigurationState.SecureStorageUnavailable
}
