package dev.netstream.domain

import org.junit.Assert.*
import org.junit.Test

class AdaptiveQualityPolicyTest {
    private val healthy =
        ContextState(
            bandwidthBps = 6_000_000,
            bufferedDurationMs = 8_000,
            batteryPercent = 80,
            isPlaying = true,
            playbackRequested = true,
            phase = PlaybackPhase.READY,
        )

    private fun stable(s: ContextState): Quality =
        AdaptiveQualityPolicy().run {
            evaluate(s.copy(elapsedMs = 0))
            evaluate(s.copy(elapsedMs = 12_000)).cap
        }

    @Test
    fun exactBandwidthBoundaries() {
        mapOf(
                999_999L to Quality.P360,
                1_000_000L to Quality.P480,
                1_999_999L to Quality.P480,
                2_000_000L to Quality.P720,
                5_000_000L to Quality.P720,
                5_000_001L to Quality.P1080,
            )
            .forEach { (bps, q) ->
                assertEquals("$bps bps", q, stable(healthy.copy(bandwidthBps = bps)))
            }
    }

    @Test
    fun chargingOverridesLowBatteryNotBandwidth() {
        assertEquals(Quality.P360, stable(healthy.copy(batteryPercent = 14)))
        assertEquals(Quality.P1080, stable(healthy.copy(batteryPercent = 15)))
        assertEquals(Quality.P1080, stable(healthy.copy(batteryPercent = 2, isCharging = true)))
        assertEquals(
            Quality.P360,
            stable(healthy.copy(batteryPercent = 2, isCharging = true, bandwidthBps = 800_000)),
        )
    }

    @Test
    fun unknownSignalsStartConservatively() {
        assertEquals(Quality.P360, stable(healthy.copy(batteryPercent = null)))
        assertEquals(Quality.P360, stable(healthy.copy(bandwidthBps = null)))
    }

    @Test
    fun lowBufferNeedsFourSecondsUnlessVodTailIsFullyBuffered() {
        assertEquals(Quality.P480, stable(healthy.copy(bufferedDurationMs = 3999)))
        assertEquals(Quality.P1080, stable(healthy.copy(bufferedDurationMs = 4000)))
        assertEquals(
            Quality.P1080,
            stable(healthy.copy(bufferedDurationMs = 1500, positionMs = 8500, durationMs = 10000)),
        )
    }

    @Test
    fun upgradeRequiresFullContinuousWindow() {
        val p = AdaptiveQualityPolicy()
        assertEquals(Quality.P1080, p.evaluate(healthy).pendingUpgrade)
        assertEquals(1L, p.evaluate(healthy.copy(elapsedMs = 11_999)).stabilizationRemainingMs)
        assertEquals(Quality.P1080, p.evaluate(healthy.copy(elapsedMs = 12_000)).cap)
    }

    @Test
    fun candidateChangeRestartsClock() {
        val p = AdaptiveQualityPolicy()
        p.evaluate(healthy)
        val slower = healthy.copy(bandwidthBps = 3_000_000)
        p.evaluate(slower.copy(elapsedMs = 11_000))
        assertEquals(Quality.P360, p.evaluate(slower.copy(elapsedMs = 12_000)).cap)
        assertEquals(Quality.P720, p.evaluate(slower.copy(elapsedMs = 23_000)).cap)
    }

    @Test
    fun downgradeNeverWaitsForCooldown() {
        val p = AdaptiveQualityPolicy()
        p.evaluate(healthy)
        p.evaluate(healthy.copy(elapsedMs = 12_000))
        assertEquals(
            Quality.P720,
            p.evaluate(healthy.copy(elapsedMs = 12_001, bandwidthBps = 3_000_000)).cap,
        )
        assertEquals(
            Quality.P360,
            p.evaluate(healthy.copy(elapsedMs = 12_002, batteryPercent = 14)).cap,
        )
    }

    @Test
    fun activeStallAndRecentStallHoldCapBeforeNewUpgradeWindow() {
        val p = AdaptiveQualityPolicy()
        p.evaluate(healthy)
        p.evaluate(healthy.copy(elapsedMs = 12_000))
        assertEquals(
            Quality.P480,
            p.evaluate(healthy.copy(elapsedMs = 13_000, isStalling = true, isPlaying = false)).cap,
        )
        val recovered = healthy.copy(lastStallEndElapsedMs = 14_000)
        assertNull(p.evaluate(recovered.copy(elapsedMs = 23_999)).pendingUpgrade)
        assertEquals(Quality.P1080, p.evaluate(recovered.copy(elapsedMs = 24_000)).pendingUpgrade)
        assertEquals(Quality.P1080, p.evaluate(recovered.copy(elapsedMs = 36_000)).cap)
    }

    @Test
    fun pauseAndEqualTargetDiscardPendingTime() {
        val p = AdaptiveQualityPolicy()
        p.evaluate(healthy)
        assertNull(p.evaluate(healthy.copy(isPlaying = false, elapsedMs = 11_000)).pendingUpgrade)
        assertEquals(12_000L, p.evaluate(healthy.copy(elapsedMs = 30_000)).stabilizationRemainingMs)
        assertNull(
            p.evaluate(healthy.copy(elapsedMs = 31_000, bandwidthBps = 200_000)).pendingUpgrade
        )
        assertEquals(Quality.P360, p.evaluate(healthy.copy(elapsedMs = 42_000)).cap)
    }
}
