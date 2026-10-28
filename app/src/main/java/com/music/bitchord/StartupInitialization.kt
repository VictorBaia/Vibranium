package com.music.bitchord

import kotlinx.coroutines.CompletableDeferred

/** Signals when the process-wide stores opened off the main thread are ready. */
internal object StartupInitialization {
    private val completion = CompletableDeferred<Unit>()

    suspend fun awaitReady() = completion.await()

    fun markReady() {
        completion.complete(Unit)
    }
}
