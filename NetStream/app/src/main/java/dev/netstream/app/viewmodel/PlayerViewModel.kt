package dev.netstream.app.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dev.netstream.domain.TelemetryRepository
import dev.netstream.domain.VideoItem
import dev.netstream.playback.StreamingSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Saved state contains only item metadata and resume intent; no Activity, surface or PlayerView.
 */
class PlayerViewModel(
    private val app: Application,
    private val telemetry: TelemetryRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val selected = saved.getStateFlow<VideoItem?>("video", null)
    private val _session = MutableStateFlow<StreamingSession?>(null)
    val session = _session.asStateFlow()

    fun open(item: VideoItem) {
        stop("item_changed")
        saved["position"] = 0L
        saved["autoplay"] = true
        saved["video"] = item
    }

    fun start() {
        val item = selected.value ?: return
        if (_session.value == null)
            _session.value =
                StreamingSession(
                    app,
                    item,
                    telemetry,
                    saved.get<Long>("position") ?: 0,
                    saved.get<Boolean>("autoplay") ?: true,
                )
    }

    fun stop(reason: String) {
        val active = _session.value ?: return
        saved["position"] = active.player.currentPosition.coerceAtLeast(0)
        saved["autoplay"] =
            active.player.playWhenReady &&
                active.player.playbackState != androidx.media3.common.Player.STATE_ENDED
        _session.value = null
        active.finish(reason)
    }

    fun close() {
        stop("back")
        saved["video"] = null
    }

    override fun onCleared() {
        stop("viewmodel_cleared")
    }
}
