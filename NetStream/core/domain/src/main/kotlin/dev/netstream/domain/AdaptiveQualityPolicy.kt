package dev.netstream.domain

/**
 * Pure policy; pass a monotonic clock in ContextState so hysteresis has no wall-clock dependency.
 */
class AdaptiveQualityPolicy(
    private val upgradeWindowMs: Long = 12_000,
    private val recentStallWindowMs: Long = 10_000,
    private val healthyBufferMs: Long = 4_000,
) {
    data class Decision(
        val cap: Quality,
        val reason: String,
        val pendingUpgrade: Quality? = null,
        val stabilizationRemainingMs: Long = 0,
    )

    var currentCap = Quality.P360
        private set

    private var pending: Quality? = null
    private var pendingSince = 0L

    init {
        require(upgradeWindowMs > 0 && recentStallWindowMs >= 0 && healthyBufferMs >= 0)
    }

    private fun target(s: ContextState): Pair<Quality, String> {
        val bps = s.bandwidthBps
        val recentStall =
            s.lastStallEndElapsedMs?.let { s.elapsedMs - it in 0 until recentStallWindowMs } == true
        val requiredReserve =
            s.durationMs
                ?.takeUnless { s.isLive }
                ?.let { (it - s.positionMs).coerceIn(0, healthyBufferMs) } ?: healthyBufferMs
        return when {
            !s.isCharging && (s.batteryPercent ?: 0) < 15 ->
                Quality.P360 to
                    if (s.batteryPercent == null) "Waiting for battery reading"
                    else "Battery below 15%"
            bps == null -> Quality.P360 to "Measuring bandwidth"
            bps < 1_000_000 -> Quality.P360 to "Bandwidth below 1 Mbps"
            s.isStalling -> Quality.P480 to "Active rebuffer"
            recentStall -> Quality.P480 to "Recent rebuffer"
            bps < 2_000_000 -> Quality.P480 to "Bandwidth below 2 Mbps"
            s.bufferedDurationMs < requiredReserve -> Quality.P480 to "Low buffer reserve"
            bps <= 5_000_000 -> Quality.P720 to "Healthy link, 2–5 Mbps"
            else -> Quality.P1080 to "Healthy link above 5 Mbps"
        }
    }

    fun evaluate(context: ContextState): Decision {
        val (target, reason) = target(context)
        when {
            target.height <= currentCap.height -> {
                // No downgrade cooldown. An equal target also cancels any stale upgrade.
                pending = null
                currentCap = target
            }
            !context.isPlaying -> {
                pending = null // paused/startup time never earns a stabilization credit
            }
            else -> {
                if (pending != target) {
                    pending = target
                    pendingSince = context.elapsedMs
                }
                val remaining = upgradeWindowMs - (context.elapsedMs - pendingSince)
                if (remaining > 0) return Decision(currentCap, reason, target, remaining)
                currentCap = target
                pending = null
            }
        }
        return Decision(currentCap, reason)
    }
}
