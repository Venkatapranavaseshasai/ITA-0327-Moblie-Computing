package dev.netstream.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.BandwidthMeter
import dev.netstream.domain.ContextState
import dev.netstream.domain.PlaybackPhase
import dev.netstream.domain.StallTracker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * All reads, callbacks and releases use the player's main application looper. Event-driven safety
 * updates plus a half-second clock for buffer, HUD and stabilization.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class ContextMonitorManager(
    context: Context,
    private val player: Player,
    private val meter: BandwidthMeter,
    private val scope: CoroutineScope,
) {
    private val app = context.applicationContext
    private val battery = app.getSystemService(BatteryManager::class.java)
    private val stalls = StallTracker()
    private val _state = MutableStateFlow(ContextState())
    val state = _state.asStateFlow()
    private var running = false
    private var ticker: Job? = null
    private var percent: Int? = null
    private var charging = false
    private var sampled = false

    private val transfers =
        BandwidthMeter.EventListener { _, bytes, estimate ->
            if (bytes > 0 && estimate > 0) sampled = true
            if (running) sample()
        }
    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (running) {
                    readBattery(intent)
                    sample()
                }
            }
        }
    private val listener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                // Preserve discrete transitions even when onEvents batches several notifications.
                if (running) publish(advanceTracker = true, observedState = playbackState)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (running) sample()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (
                    reason == Player.DISCONTINUITY_REASON_SEEK ||
                        reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    onSeek()
                }
            }
        }

    fun start() {
        check(
            Looper.myLooper() == player.applicationLooper &&
                player.applicationLooper == Looper.getMainLooper()
        )
        if (running) return
        running = true
        val sticky =
            ContextCompat.registerReceiver(
                app,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        readBattery(sticky)
        player.addListener(listener)
        meter.addEventListener(Handler(player.applicationLooper), transfers)
        ticker =
            scope.launch {
                while (isActive) {
                    sample()
                    delay(500)
                }
            }
    }

    private fun readBattery(intent: Intent?) {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 0) ?: 0
        percent =
            if (level >= 0 && scale > 0) ((100L * level / scale).toInt()).coerceIn(0, 100)
            else
                battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf {
                    it in 0..100
                }
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        charging =
            battery.isCharging ||
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                (plugged && status == BatteryManager.BATTERY_STATUS_FULL)
    }

    internal fun onSeek() = stalls.interrupt(SystemClock.elapsedRealtime())

    internal fun sample(): ContextState = publish(advanceTracker = running)

    private fun publish(
        advanceTracker: Boolean,
        observedState: Int = player.playbackState,
    ): ContextState {
        val now = SystemClock.elapsedRealtime()
        val phase =
            when (observedState) {
                Player.STATE_BUFFERING -> PlaybackPhase.BUFFERING
                Player.STATE_READY -> PlaybackPhase.READY
                Player.STATE_ENDED -> PlaybackPhase.ENDED
                else -> PlaybackPhase.IDLE
            }
        val requested =
            player.playWhenReady &&
                player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
        if (advanceTracker) stalls.update(now, phase, requested, player.isPlaying)
        val stall = stalls.snapshot(now)
        val pos = player.currentPosition.coerceAtLeast(0)
        return ContextState(
                elapsedMs = now,
                bandwidthBps = meter.bitrateEstimate.takeIf { sampled && it > 0 },
                // Clamp TIME_UNSET before subtracting, so a sentinel cannot underflow.
                bufferedDurationMs =
                    (player.bufferedPosition.coerceAtLeast(0) - pos).coerceAtLeast(0),
                batteryPercent = percent,
                isCharging = charging,
                phase = phase,
                isPlaying = player.isPlaying,
                playbackRequested = requested,
                isStalling = stall.active,
                lastStallEndElapsedMs = stall.lastEndMs,
                totalStallCount = stall.count,
                totalStallDurationMs = stall.durationMs,
                positionMs = pos,
                durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 },
                isSeekable = player.isCurrentMediaItemSeekable,
                isLive = player.isCurrentMediaItemLive,
                videoWidth = player.videoSize.width.takeIf { it > 0 },
                videoHeight = player.videoSize.height.takeIf { it > 0 },
            )
            .also { _state.value = it }
    }

    fun stop(): ContextState {
        if (!running) return state.value
        running = false
        ticker?.cancel()
        player.removeListener(listener)
        meter.removeEventListener(transfers)
        app.unregisterReceiver(receiver)
        stalls.interrupt(SystemClock.elapsedRealtime())
        return publish(advanceTracker = false)
    }
}
