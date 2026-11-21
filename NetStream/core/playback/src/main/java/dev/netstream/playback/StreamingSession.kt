package dev.netstream.playback

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import dev.netstream.domain.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A foreground viewing session. Owns exactly one meter, selector, monitor and player. Android UI
 * never constructs Media3 objects or writes Firebase documents.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class StreamingSession(
    context: Context,
    val video: VideoItem,
    private val telemetry: TelemetryRepository,
    initialPositionMs: Long = 0,
    autoplay: Boolean = true,
) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val id = UUID.randomUUID().toString()
    private val startedAt = System.currentTimeMillis()
    private val startElapsed = SystemClock.elapsedRealtime()
    private val meter = DefaultBandwidthMeter.Builder(app).build()
    private val selector = DefaultTrackSelector(app)
    val player: ExoPlayer =
        ExoPlayer.Builder(app)
            .setBandwidthMeter(meter)
            .setUsePlatformDiagnostics(false)
            .setTrackSelector(selector)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    private val monitor = ContextMonitorManager(app, player, meter, scope)
    private val engine = AdaptiveQualityEngine(selector)
    val contextState = monitor.state
    val policyState = engine.status
    private val _history = MutableStateFlow<List<QualitySwitchLog>>(emptyList())
    val history = _history.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val loggingWarning = telemetry.warning
    private var closed = false
    private var capCount = 0
    private var outputCount = 0
    private var previousSize: VideoSize? = null
    // A bandwidth cap is not proof of why a frame changed. Use Media3's reported
    // selection reason only when its downstream format matches the observed output.
    private var downstream: MediaLoadData? = null
    private val mediaEvents =
        object : AnalyticsListener {
            override fun onDownstreamFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                mediaLoadData: MediaLoadData,
            ) {
                if (mediaLoadData.trackType == C.TRACK_TYPE_VIDEO) downstream = mediaLoadData
            }
        }

    private fun outputReason(size: VideoSize): String {
        val selection =
            downstream?.takeIf {
                it.trackFormat?.height == size.height && it.trackFormat?.width == size.width
            }
        val trigger =
            when (selection?.trackSelectionReason) {
                C.SELECTION_REASON_ADAPTIVE -> "Media3 adaptive track selection"
                C.SELECTION_REASON_MANUAL -> "Media3 explicit track selection"
                C.SELECTION_REASON_INITIAL -> "Media3 track initialization"
                else -> "Media3 output format change"
            }
        return "$trigger under ${policyState.value.cap.label} cap"
    }

    private val listener =
        object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                _error.value =
                    "${error.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_',' ')}. Check the connection or try another stream."
            }

            // Direct discrete callback, NOT derived from a conflated StateFlow. ABR can change
            // output below a static cap; a cap change does not imply a decoded-size change.
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (closed || videoSize.height <= 0 || videoSize.width <= 0) return
                val old = previousSize
                previousSize = videoSize
                if (
                    old != null && (old.height != videoSize.height || old.width != videoSize.width)
                ) {
                    outputCount++
                    logSwitch(
                        SwitchKind.VIDEO_OUTPUT,
                        old.height,
                        videoSize.height,
                        old.width,
                        videoSize.width,
                        outputReason(videoSize),
                        monitor.sample(),
                    )
                }
            }
        }

    init {
        player.addAnalyticsListener(mediaEvents)
        player.addListener(listener)
        monitor.start()
        engine.bind(contextState, scope) { old, new, reason, state ->
            capCount++
            logSwitch(SwitchKind.POLICY_CAP, old.height, new.height, null, null, reason, state)
        }
        val mime =
            if (video.protocol == StreamProtocol.HLS) MimeTypes.APPLICATION_M3U8
            else MimeTypes.APPLICATION_MPD
        val item =
            MediaItem.Builder()
                .setMediaId(video.id)
                .setUri(video.streamUrl)
                .setMimeType(mime)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(video.title).build())
                .build()
        if (initialPositionMs > 0) player.setMediaItem(item, initialPositionMs)
        else player.setMediaItem(item)
        player.playWhenReady = autoplay
        player.prepare()
        saveSummary(contextState.value, null)
        scope.launch {
            while (isActive) {
                delay(15_000)
                saveSummary(monitor.sample(), null)
            }
        }
    }

    private fun logSwitch(
        kind: SwitchKind,
        old: Int,
        new: Int,
        oldWidth: Int?,
        newWidth: Int?,
        reason: String,
        s: ContextState,
    ) {
        val event =
            QualitySwitchLog(
                UUID.randomUUID().toString(),
                id,
                video.id,
                System.currentTimeMillis(),
                kind,
                old,
                new,
                oldWidth,
                newWidth,
                reason,
                policyState.value.cap.height,
                s.bandwidthBps,
                s.bufferedDurationMs,
                s.batteryPercent,
                s.isCharging,
                s.isStalling,
            )
        telemetry.recordSwitch(event)
        _history.value =
            (listOf(event) + _history.value).take(
                32
            ) // UI only; persisted history is not truncated.
    }

    fun togglePlay() {
        if (closed) return
        when {
            player.playbackState == Player.STATE_ENDED -> {
                player.seekToDefaultPosition()
                player.play()
            }
            player.playWhenReady -> player.pause()
            else -> player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        if (closed || !player.isCurrentMediaItemSeekable || player.duration <= 0) return
        monitor.onSeek()
        player.seekTo(positionMs.coerceIn(0, player.duration))
    }

    fun retry() {
        if (!closed) {
            _error.value = null
            player.prepare()
            player.play()
        }
    }

    private fun saveSummary(s: ContextState, endReason: String?) {
        val now = System.currentTimeMillis()
        telemetry.saveSession(
            SessionSummary(
                id,
                video.id,
                video.protocol,
                startedAt,
                now,
                now.takeIf { endReason != null },
                (SystemClock.elapsedRealtime() - startElapsed).coerceAtLeast(0),
                s.totalStallCount,
                s.totalStallDurationMs,
                capCount,
                outputCount,
                endReason,
            )
        )
    }

    fun finish(reason: String) {
        if (closed) return
        closed = true
        scope.cancel()
        player.removeAnalyticsListener(mediaEvents)
        player.removeListener(listener)
        val final = monitor.stop()
        saveSummary(final, reason)
        player.release()
    }
}
