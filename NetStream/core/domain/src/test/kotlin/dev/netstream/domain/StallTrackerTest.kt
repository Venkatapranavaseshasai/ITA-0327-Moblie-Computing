package dev.netstream.domain

import org.junit.Assert.*
import org.junit.Test

class StallTrackerTest {
    private val t = StallTracker()

    private fun buffering(at: Long) = t.update(at, PlaybackPhase.BUFFERING, true, false)

    private fun playing(at: Long) = t.update(at, PlaybackPhase.READY, true, true)

    @Test
    fun initialBufferIsNotAStall() {
        buffering(0)
        playing(2500)
        assertEquals(0, t.snapshot(2500).count)
    }

    @Test
    fun repeatedBufferEventsCountOnceAndDurationIsNotDoubleCounted() {
        playing(0)
        buffering(1000)
        buffering(2200)
        assertEquals(1600L, t.snapshot(2600).durationMs)
        playing(3000)
        assertEquals(StallTracker.Snapshot(false, 1, 2000, 3000), t.snapshot(8000))
    }

    @Test
    fun seekDoesNotCreateARebuffer() {
        playing(0)
        t.interrupt(100)
        buffering(110)
        playing(800)
        assertEquals(0, t.snapshot(900).count)
    }

    @Test
    fun pauseClosesStallAndResumeBufferIsExcluded() {
        playing(0)
        buffering(500)
        t.update(1000, PlaybackPhase.BUFFERING, false, false)
        buffering(3000)
        playing(4000)
        buffering(5000)
        t.interrupt(5700)
        assertEquals(StallTracker.Snapshot(false, 2, 1200, 5700), t.snapshot(9999))
    }

    @Test
    fun releaseMidStallFinalizesOpenDurationOnce() {
        playing(100)
        buffering(300)
        t.interrupt(1300)
        t.interrupt(1800)
        assertEquals(StallTracker.Snapshot(false, 1, 1000, 1300), t.snapshot(2100))
    }

    @Test
    fun idleAndEndedDisarmRebufferTracking() {
        playing(0)
        t.update(200, PlaybackPhase.ENDED, true, false)
        buffering(500)
        assertEquals(0, t.snapshot(510).count)
        playing(1000)
        buffering(1100)
        t.update(1300, PlaybackPhase.IDLE, true, false)
        assertEquals(200L, t.snapshot(2000).durationMs)
    }
}
