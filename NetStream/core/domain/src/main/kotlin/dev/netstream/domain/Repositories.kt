package dev.netstream.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface CatalogueRepository {
    /**
     * Ordered catalogue. Listener/network errors propagate, rather than impersonating an empty
     * library.
     */
    fun observeVideos(): Flow<List<VideoItem>>
}

interface TelemetryRepository {
    /** Non-blocking, app-scoped submission; must outlive the player's coroutine scope. */
    fun recordSwitch(event: QualitySwitchLog)

    fun saveSession(session: SessionSummary)

    val warning: StateFlow<String?>
}
