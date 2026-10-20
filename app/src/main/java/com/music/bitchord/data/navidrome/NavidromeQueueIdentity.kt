package com.music.bitchord.data.navidrome

/** Account guard for persisted queues. The namespace is random and non-secret. */
internal object NavidromeQueueIdentity {
    fun accountFor(mediaIds: Iterable<String>, currentAccount: String?): String? =
        currentAccount.takeIf { mediaIds.any(::isSong) }

    fun canRestore(
        mediaIds: Iterable<String>,
        persistedAccount: String?,
        currentAccount: String?,
    ): Boolean = !mediaIds.any(::isSong) ||
        (persistedAccount != null && persistedAccount == currentAccount)

    private fun isSong(mediaId: String): Boolean =
        NavidromeIds.parse(mediaId)?.entity == NavidromeEntity.SONG
}
