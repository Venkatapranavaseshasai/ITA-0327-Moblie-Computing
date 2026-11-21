package dev.netstream.domain

import java.io.Serializable

/** Transport is explicit: signed/query-based manifests do not always have a useful suffix. */
enum class StreamProtocol {
    HLS,
    DASH,
}

data class VideoItem(
    val id: String,
    val title: String,
    val streamUrl: String,
    val protocol: StreamProtocol,
    val description: String = "",
    val genre: String = "Film",
    val creator: String = "Independent",
    val year: Int? = null,
    val durationSeconds: Int? = null,
    val posterKey: String? = null,
    val posterUrl: String? = null,
) : Serializable

enum class Quality(val height: Int) {
    P360(360),
    P480(480),
    P720(720),
    P1080(1080);

    val label: String
        get() = "${height}p"
}

enum class PlaybackPhase {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
}

/** null means not measured, never a synthetic zero. All durations are milliseconds. */
data class ContextState(
    val elapsedMs: Long = 0, // monotonic, never persisted as a timestamp
    val bandwidthBps: Long? = null, // Media3 estimate after a transfer sample
    val bufferedDurationMs: Long = 0,
    val batteryPercent: Int? = null,
    val isCharging: Boolean = false,
    val phase: PlaybackPhase = PlaybackPhase.IDLE,
    val isPlaying: Boolean = false,
    val playbackRequested: Boolean = false,
    val isStalling: Boolean = false,
    val lastStallEndElapsedMs: Long? = null,
    val totalStallCount: Int = 0,
    val totalStallDurationMs: Long = 0,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val isSeekable: Boolean = false,
    val isLive: Boolean = false,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
) {
    val bandwidthMbps: Double?
        get() = bandwidthBps?.div(1_000_000.0)
}

enum class SwitchKind {
    POLICY_CAP,
    VIDEO_OUTPUT,
}

data class QualitySwitchLog(
    val eventId: String,
    val sessionId: String,
    val videoId: String,
    val timestampMs: Long, // UTC Unix epoch, client observation time
    val kind: SwitchKind,
    val oldHeight: Int,
    val newHeight: Int,
    val oldWidth: Int? = null, // caps constrain height only
    val newWidth: Int? = null,
    val triggerReason: String,
    val activeCapHeight: Int,
    val bandwidthBps: Long?,
    val bufferedDurationMs: Long,
    val batteryPercent: Int?,
    val isCharging: Boolean,
    val isStalling: Boolean,
)

data class SessionSummary(
    val sessionId: String,
    val videoId: String,
    val protocol: StreamProtocol,
    val startedAtMs: Long,
    val updatedAtMs: Long,
    val endedAtMs: Long?,
    val sessionDurationMs: Long,
    val totalStallCount: Int,
    val totalStallDurationMs: Long,
    val capSwitchCount: Int,
    val outputSwitchCount: Int,
    val endReason: String? = null,
)
