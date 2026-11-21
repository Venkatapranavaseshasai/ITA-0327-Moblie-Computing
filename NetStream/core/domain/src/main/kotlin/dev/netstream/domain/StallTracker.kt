package dev.netstream.domain

/**
 * One episode per transition into unintended buffering *after* actual playback. Startup, deliberate
 * seeks, paused time and audio-focus suppression are excluded. A snapshot includes elapsed time in
 * an open episode without committing it twice.
 */
class StallTracker {
    data class Snapshot(
        val active: Boolean,
        val count: Int,
        val durationMs: Long,
        val lastEndMs: Long?,
    )

    private var armed = false
    private var openSince: Long? = null
    private var count = 0
    private var completedMs = 0L
    private var lastEndMs: Long? = null

    fun update(now: Long, phase: PlaybackPhase, wantsPlayback: Boolean, playing: Boolean) {
        if (!wantsPlayback || phase == PlaybackPhase.IDLE || phase == PlaybackPhase.ENDED) {
            interrupt(now)
            return
        }
        if (phase == PlaybackPhase.READY && playing) armed = true
        if (phase == PlaybackPhase.BUFFERING && armed) {
            if (openSince == null) {
                openSince = now
                count++
            }
        } else {
            finishOpen(now)
        }
    }

    /** Called on seek, pause, stop or release, including the middle of an open stall. */
    fun interrupt(now: Long) {
        finishOpen(now)
        armed = false
    }

    private fun finishOpen(now: Long) {
        openSince?.let {
            completedMs += (now - it).coerceAtLeast(0)
            lastEndMs = now
        }
        openSince = null
    }

    fun snapshot(now: Long) =
        Snapshot(
            openSince != null,
            count,
            completedMs + (openSince?.let { (now - it).coerceAtLeast(0) } ?: 0),
            lastEndMs,
        )
}
